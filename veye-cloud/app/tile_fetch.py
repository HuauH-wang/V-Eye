from __future__ import annotations

import asyncio
import time
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path

import httpx

from .config import get_settings

_TILE_HEADERS = {
    "User-Agent": "V-Eye-Cloud/1.0 (map-tile-proxy)",
    "Referer": "https://www.amap.com/",
}
# 并发 + 出网抖动：connect 过短会在排队等信号量时误超时
_TILE_TIMEOUT = httpx.Timeout(12.0, connect=6.0)
_AMAP_TIMEOUT = httpx.Timeout(4.0, connect=2.0)
_TILE_LIMITS = httpx.Limits(max_connections=8, max_keepalive_connections=4)
_TILE_RETRIES = 1
_TILE_RETRY_DELAY_S = 0.05
_AMAP_PROVIDERS = frozenset({"amap", "amap_satellite"})

_MEM_CACHE_MAX = 4096
_MEM_CACHE_TTL_S = 7200.0
BROWSER_TILE_CACHE = "public, max-age=604800, immutable"

# 主源失败时用 Esri 同 z/x/y 瓦片兜底，写入原 provider 路径以便下次秒开
_PROVIDER_FALLBACK: dict[str, str] = {
    "amap": "street",
    "amap_satellite": "satellite",
}


def fallback_provider_for(provider: str) -> str | None:
    return _PROVIDER_FALLBACK.get(provider)

_tile_client: httpx.AsyncClient | None = None
_tile_cache_dir: Path | None = None
_mem_cache: OrderedDict[tuple[str, int, int, int], tuple[float, bytes, str]] = OrderedDict()
_fetch_sem: asyncio.Semaphore | None = None
_inflight: dict[tuple[str, int, int, int], asyncio.Task[MapTilePayload]] = {}


def _get_fetch_sem() -> asyncio.Semaphore:
    global _fetch_sem
    if _fetch_sem is None:
        _fetch_sem = asyncio.Semaphore(6)
    return _fetch_sem


@dataclass(frozen=True)
class MapTilePayload:
    media_type: str
    path: Path | None = None
    content: bytes | None = None

    @property
    def cache_control(self) -> str:
        return BROWSER_TILE_CACHE


def init_tile_client() -> None:
    global _tile_cache_dir
    _tile_cache_dir = Path(get_settings().TILE_CACHE_DIR)
    _tile_cache_dir.mkdir(parents=True, exist_ok=True)
    get_tile_client()


async def close_tile_client() -> None:
    global _tile_client, _fetch_sem
    if _tile_client is not None:
        await _tile_client.aclose()
        _tile_client = None
    _mem_cache.clear()
    _inflight.clear()
    _fetch_sem = None


async def reset_tile_client() -> None:
    """长连接池在并发瓦片请求下可能僵死，出错后丢弃并重建。"""
    global _tile_client
    if _tile_client is None:
        return
    try:
        await _tile_client.aclose()
    except Exception:
        pass
    _tile_client = None


def get_tile_client(*, timeout: httpx.Timeout | None = None) -> httpx.AsyncClient:
    global _tile_client
    desired = timeout or _TILE_TIMEOUT
    if _tile_client is None or _tile_client.is_closed:
        _tile_client = httpx.AsyncClient(
            timeout=desired,
            follow_redirects=True,
            limits=httpx.Limits(max_connections=6, max_keepalive_connections=4),
            headers=_TILE_HEADERS,
        )
    return _tile_client


def _disk_path(provider: str, z: int, x: int, y: int) -> Path:
    assert _tile_cache_dir is not None
    return _tile_cache_dir / provider / str(z) / str(x) / f"{y}.png"


def disk_tile_hit(provider: str, z: int, x: int, y: int) -> Path | None:
    """同步磁盘命中（路由层零 await 快路径）。"""
    if _tile_cache_dir is None:
        return None
    path = _disk_path(provider, z, x, y)
    try:
        if path.is_file() and path.stat().st_size > 100:
            return path
    except OSError:
        return None
    return None


def disk_fallback_hit(provider: str, z: int, x: int, y: int) -> Path | None:
    alt = fallback_provider_for(provider)
    if alt is None:
        return None
    return disk_tile_hit(alt, z, x, y)


def _mem_get(key: tuple[str, int, int, int]) -> tuple[bytes, str] | None:
    row = _mem_cache.get(key)
    if row is None:
        return None
    expires_at, content, media_type = row
    if time.monotonic() >= expires_at:
        _mem_cache.pop(key, None)
        return None
    _mem_cache.move_to_end(key)
    return content, media_type


def _mem_set(key: tuple[str, int, int, int], content: bytes, media_type: str) -> None:
    _mem_cache[key] = (time.monotonic() + _MEM_CACHE_TTL_S, content, media_type)
    _mem_cache.move_to_end(key)
    while len(_mem_cache) > _MEM_CACHE_MAX:
        _mem_cache.popitem(last=False)


def _disk_save(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".png.part")
    tmp.write_bytes(content)
    tmp.replace(path)


async def _fetch_url(url: str, *, timeout: httpx.Timeout) -> httpx.Response:
    async with _get_fetch_sem():
        client = get_tile_client(timeout=timeout)
        return await client.get(url)


async def _fetch_and_persist(
    *,
    provider: str,
    z: int,
    x: int,
    y: int,
    url: str,
    timeout: httpx.Timeout,
) -> MapTilePayload:
    cache_key = (provider, z, x, y)
    upstream = await _fetch_url(url, timeout=timeout)
    if upstream.status_code != 200 or len(upstream.content) < 100:
        raise httpx.HTTPStatusError(
            f"upstream status {upstream.status_code}",
            request=upstream.request,
            response=upstream,
        )
    media_type = upstream.headers.get("content-type", "image/png")
    content = upstream.content
    _mem_set(cache_key, content, media_type)
    disk_path = _disk_path(provider, z, x, y)
    await asyncio.to_thread(_disk_save, disk_path, content)
    return MapTilePayload(media_type=media_type, path=disk_path)


async def _race_fetch_and_persist(
    *,
    provider: str,
    z: int,
    x: int,
    y: int,
    urls: list[str],
    timeout: httpx.Timeout,
) -> MapTilePayload:
    if len(urls) == 1:
        return await _fetch_and_persist(
            provider=provider, z=z, x=x, y=y, url=urls[0], timeout=timeout
        )

    tasks = [asyncio.create_task(
        _fetch_and_persist(provider=provider, z=z, x=x, y=y, url=url, timeout=timeout)
    ) for url in urls]
    last_err: Exception | None = None
    try:
        for finished in asyncio.as_completed(tasks):
            try:
                result = await finished
            except Exception as e:
                last_err = e
                continue
            for task in tasks:
                if task is not finished and not task.done():
                    task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            return result
    finally:
        for task in tasks:
            if not task.done():
                task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    if last_err is not None:
        raise last_err
    raise httpx.HTTPError("all tile urls failed")


async def fetch_map_tile(
    provider: str,
    z: int,
    x: int,
    y: int,
    urls: list[str],
    *,
    fallback_url: str | None = None,
) -> MapTilePayload:
    cache_key = (provider, z, x, y)

    disk = disk_tile_hit(provider, z, x, y)
    if disk is not None:
        return MapTilePayload(media_type="image/png", path=disk)

    mem = _mem_get(cache_key)
    if mem is not None:
        content, media_type = mem
        return MapTilePayload(media_type=media_type, content=content)

    inflight = _inflight.get(cache_key)
    if inflight is not None:
        return await asyncio.shield(inflight)

    task = asyncio.create_task(
        _fetch_map_tile_upstream(provider, z, x, y, urls, fallback_url=fallback_url)
    )
    _inflight[cache_key] = task
    try:
        return await task
    finally:
        _inflight.pop(cache_key, None)


async def _fetch_map_tile_upstream(
    provider: str,
    z: int,
    x: int,
    y: int,
    urls: list[str],
    *,
    fallback_url: str | None = None,
) -> MapTilePayload:
    # AutoDL 出网访问高德 CDN 极不稳定；未命中缓存时直接用 Esri 兜底并写入 amap 路径
    primary_timeout = _AMAP_TIMEOUT if provider in _AMAP_PROVIDERS else _TILE_TIMEOUT
    if provider in _AMAP_PROVIDERS and fallback_url:
        fetch_groups: list[tuple[list[str], httpx.Timeout]] = [([fallback_url], _TILE_TIMEOUT)]
    else:
        fetch_groups = [(urls, primary_timeout)]
        if fallback_url:
            fetch_groups.append(([fallback_url], _TILE_TIMEOUT))

    last_err: Exception | None = None
    for group_urls, timeout in fetch_groups:
        for attempt in range(_TILE_RETRIES + 1):
            try:
                return await _race_fetch_and_persist(
                    provider=provider,
                    z=z,
                    x=x,
                    y=y,
                    urls=group_urls,
                    timeout=timeout,
                )
            except httpx.HTTPError as e:
                last_err = e
                await reset_tile_client()
            if attempt < _TILE_RETRIES:
                await asyncio.sleep(_TILE_RETRY_DELAY_S * (attempt + 1))

    stale = disk_tile_hit(provider, z, x, y)
    if stale is not None:
        return MapTilePayload(media_type="image/png", path=stale)

    assert last_err is not None
    raise last_err
