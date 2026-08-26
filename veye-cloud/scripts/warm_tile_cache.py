#!/usr/bin/env python3
"""按 bbox 预热瓦片到 TILE_CACHE_DIR（经本地 FastAPI 代理写入磁盘）。"""
from __future__ import annotations

import argparse
import asyncio
import math
import sys
import time

import httpx


def lat_lng_to_tile(lat: float, lng: float, zoom: int) -> tuple[int, int]:
    lat_rad = math.radians(lat)
    n = 2.0**zoom
    x = int((lng + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return x, y


def tile_bounds(lat: float, lng: float, span: float, zoom: int) -> tuple[int, int, int, int]:
    x0, y0 = lat_lng_to_tile(lat + span, lng - span, zoom)
    x1, y1 = lat_lng_to_tile(lat - span, lng + span, zoom)
    return min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)


async def warm_provider(
    client: httpx.AsyncClient,
    *,
    api_base: str,
    provider: str,
    lat: float,
    lng: float,
    span: float,
    zoom_min: int,
    zoom_max: int,
    sem: asyncio.Semaphore,
    retries: int,
) -> tuple[int, int]:
    ok = 0
    fail = 0

    async def fetch_one(z: int, x: int, y: int) -> None:
        nonlocal ok, fail
        url = f"{api_base}/map/tiles/{provider}/{z}/{x}/{y}.png"
        async with sem:
            for attempt in range(retries + 1):
                try:
                    r = await client.get(url)
                    if r.status_code == 200 and len(r.content) > 100:
                        ok += 1
                        return
                except httpx.HTTPError:
                    pass
                if attempt < retries:
                    await asyncio.sleep(0.15 * (attempt + 1))
            fail += 1

    tasks: list[asyncio.Task[None]] = []
    for z in range(zoom_min, zoom_max + 1):
        x_min, y_min, x_max, y_max = tile_bounds(lat, lng, span, z)
        for x in range(x_min, x_max + 1):
            for y in range(y_min, y_max + 1):
                tasks.append(asyncio.create_task(fetch_one(z, x, y)))
    await asyncio.gather(*tasks)
    return ok, fail


async def warm(
    *,
    api_base: str,
    providers: list[str],
    lat: float,
    lng: float,
    span: float,
    zoom_min: int,
    zoom_max: int,
    concurrency: int,
    retries: int,
) -> None:
    api_base = api_base.rstrip("/")
    sem = asyncio.Semaphore(concurrency)
    t0 = time.perf_counter()

    async with httpx.AsyncClient(
        timeout=20.0,
        limits=httpx.Limits(max_connections=concurrency, max_keepalive_connections=concurrency),
    ) as client:
        total_ok = 0
        total_fail = 0
        for provider in providers:
            p0 = time.perf_counter()
            ok, fail = await warm_provider(
                client,
                api_base=api_base,
                provider=provider,
                lat=lat,
                lng=lng,
                span=span,
                zoom_min=zoom_min,
                zoom_max=zoom_max,
                sem=sem,
                retries=retries,
            )
            total_ok += ok
            total_fail += fail
            print(
                f"[warm] {provider} z{zoom_min}-{zoom_max}: ok={ok} fail={fail} "
                f"elapsed={time.perf_counter()-p0:.1f}s",
                flush=True,
            )

    print(
        f"[warm] total ok={total_ok} fail={total_fail} elapsed={time.perf_counter()-t0:.1f}s",
        flush=True,
    )


def main() -> int:
    p = argparse.ArgumentParser(description="Warm map tile disk cache via local FastAPI")
    p.add_argument("--api-base", default="http://127.0.0.1:6006")
    p.add_argument("--provider", default="amap", help="single provider (legacy)")
    p.add_argument(
        "--providers",
        default="",
        help="comma-separated providers, e.g. street,amap (overrides --provider)",
    )
    p.add_argument("--lat", type=float, default=30.5928)
    p.add_argument("--lng", type=float, default=114.3055)
    p.add_argument("--span", type=float, default=0.22, help="bbox half-span in degrees")
    p.add_argument("--zoom-min", type=int, default=11)
    p.add_argument("--zoom-max", type=int, default=14)
    p.add_argument("--concurrency", type=int, default=8)
    p.add_argument("--retries", type=int, default=2)
    args = p.parse_args()

    if args.providers.strip():
        providers = [x.strip() for x in args.providers.split(",") if x.strip()]
    else:
        providers = [args.provider]

    asyncio.run(
        warm(
            api_base=args.api_base,
            providers=providers,
            lat=args.lat,
            lng=args.lng,
            span=args.span,
            zoom_min=args.zoom_min,
            zoom_max=args.zoom_max,
            concurrency=args.concurrency,
            retries=args.retries,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
