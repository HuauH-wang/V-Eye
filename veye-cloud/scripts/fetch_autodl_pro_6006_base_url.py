#!/usr/bin/env python3
"""
从 AutoDL 容器实例 Pro API 拉取当前实例的 6006 端口反代公网地址，供 start_all_venv.sh 写入 BASE_URL。

依赖：仅标准库。
环境变量（必填）：
  AUTODL_TOKEN              控制台 → 账号 → 设置 → 开发者 Token

实例 ID（二选一）：
  AUTODL_PRO_INSTANCE_UUID  控制台「实例详情」里的实例 ID，原样传给 API（无 pro- 前缀处理）。
                              若未设置，将调用 instance/pro/list，当且仅当「运行中」实例恰好 1 个时自动使用该 uuid。

私有化（勿提交公开仓库）：可在本文件顶部 _EMBEDDED_* 填写 Token / 实例 ID，优先级低于环境变量。
  AUTODL_DISABLE_AUTO_INSTANCE_UUID=1  禁止上述 list 自动推断（必须手写 UUID）

说明：AutoDL 公开文档未承诺在容器内提供与 pro-xxx 等价的环境变量；弹性部署里的
      AutoDLContainerUUID 属于另一套标识，勿与 Pro 实例 uuid 混用。

可选：
  AUTODL_API_BASE             默认 https://api.autodl.com
  AUTODL_SNAPSHOT_RETRIES     重试次数（实例刚开机时 domain 可能暂为空），默认 8
  AUTODL_SNAPSHOT_RETRY_SECS  重试间隔秒，默认 3
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# 可选：仅私有部署时在此填写（勿 push 到公开 Git）。非空且环境变量未设置时生效。
_EMBEDDED_AUTODL_TOKEN: str = ""
_EMBEDDED_AUTODL_INSTANCE_UUID: str = ""


def _request_snapshot(api_base: str, token: str, instance_uuid: str, use_post: bool) -> dict:
    path = "/api/v1/dev/instance/pro/snapshot"
    headers = {
        "Authorization": token.strip(),
        "Accept": "application/json",
    }
    if use_post:
        url = api_base.rstrip("/") + path
        body = json.dumps({"instance_uuid": instance_uuid}).encode("utf-8")
        headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    else:
        qs = urllib.parse.urlencode({"instance_uuid": instance_uuid})
        url = api_base.rstrip("/") + path + "?" + qs
        req = urllib.request.Request(url, headers=headers, method="GET")
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read().decode("utf-8")
    return json.loads(raw)


def _request_list(api_base: str, token: str, page_index: int, page_size: int) -> dict:
    path = "/api/v1/dev/instance/pro/list"
    url = api_base.rstrip("/") + path
    body = json.dumps({"page_index": page_index, "page_size": page_size}).encode("utf-8")
    headers = {
        "Authorization": token.strip(),
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read().decode("utf-8")
    return json.loads(raw)


def _resolve_instance_uuid(token: str, api_base: str) -> str:
    env_uuid = (
        os.environ.get("AUTODL_PRO_INSTANCE_UUID", "").strip()
        or _EMBEDDED_AUTODL_INSTANCE_UUID.strip()
    )
    if env_uuid:
        return env_uuid

    flag = os.environ.get("AUTODL_DISABLE_AUTO_INSTANCE_UUID", "").strip().lower()
    if flag in ("1", "true", "yes", "on"):
        print(
            "[err] 未设置 AUTODL_PRO_INSTANCE_UUID，且已禁用 list 自动推断（AUTODL_DISABLE_AUTO_INSTANCE_UUID）",
            file=sys.stderr,
        )
        sys.exit(2)

    seen: set[str] = set()
    page = 1
    page_size = min(100, int(os.environ.get("AUTODL_LIST_PAGE_SIZE", "50")))
    max_pages = int(os.environ.get("AUTODL_LIST_MAX_PAGES", "30"))

    while page <= max_pages:
        try:
            doc = _request_list(api_base, token, page, page_size)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")[:800]
            print(f"[err] instance/pro/list HTTP {e.code}: {body}", file=sys.stderr)
            sys.exit(1)
        except urllib.error.URLError as e:
            print(f"[err] instance/pro/list 请求失败: {e}", file=sys.stderr)
            sys.exit(1)
        except json.JSONDecodeError as e:
            print(f"[err] instance/pro/list 返回非 JSON: {e}", file=sys.stderr)
            sys.exit(1)

        if doc.get("code") != "Success":
            print(
                f"[err] instance/pro/list API code={doc.get('code')!r} msg={doc.get('msg')!r}",
                file=sys.stderr,
            )
            sys.exit(1)

        data = doc.get("data")
        if not isinstance(data, dict):
            break
        lst = data.get("list") or []
        if not isinstance(lst, list) or not lst:
            break

        for item in lst:
            if not isinstance(item, dict):
                continue
            if str(item.get("status", "")).lower() != "running":
                continue
            u = str(item.get("uuid", "")).strip()
            if u:
                seen.add(u)

        if len(lst) < page_size:
            break
        page += 1

    if len(seen) == 1:
        u = next(iter(seen))
        print(f"[info] AutoDL：未配置 AUTODL_PRO_INSTANCE_UUID，list 推断唯一运行实例 {u}", file=sys.stderr)
        return u
    if len(seen) == 0:
        print(
            "[err] 无法推断实例 ID：未设置实例 ID，且 list 中无 status=running 的实例。"
            " 请在 .env 设置 AUTODL_PRO_INSTANCE_UUID=控制台实例 ID。",
            file=sys.stderr,
        )
        sys.exit(2)
    print(
        f"[err] 无法自动推断实例 ID：当前有 {len(seen)} 个运行中的 Pro 实例 {sorted(seen)}。"
        " 请在 .env 设置 AUTODL_PRO_INSTANCE_UUID 指向本机实例。",
        file=sys.stderr,
    )
    sys.exit(2)


def fetch_base_url() -> str:
    token = os.environ.get("AUTODL_TOKEN", "").strip() or _EMBEDDED_AUTODL_TOKEN.strip()
    if not token:
        print(
            "[err] fetch_autodl_pro_6006_base_url: 需要 AUTODL_TOKEN 环境变量，或在本脚本顶部填写 _EMBEDDED_AUTODL_TOKEN",
            file=sys.stderr,
        )
        sys.exit(2)

    api_base = os.environ.get("AUTODL_API_BASE", "https://api.autodl.com").strip()
    instance_uuid = _resolve_instance_uuid(token, api_base)
    retries = int(os.environ.get("AUTODL_SNAPSHOT_RETRIES", "8"))
    delay = float(os.environ.get("AUTODL_SNAPSHOT_RETRY_SECS", "3"))

    last_err: str | None = None
    for attempt in range(retries):
        for use_post in (False, True):
            try:
                doc = _request_snapshot(api_base, token, instance_uuid, use_post=use_post)
            except urllib.error.HTTPError as e:
                body = e.read().decode("utf-8", errors="replace")[:800]
                last_err = f"HTTP {e.code}: {body}"
                continue
            except urllib.error.URLError as e:
                last_err = str(e)
                continue
            except json.JSONDecodeError as e:
                last_err = f"invalid JSON: {e}"
                continue

            if doc.get("code") != "Success":
                last_err = f"API code={doc.get('code')!r} msg={doc.get('msg')!r}"
                continue

            data = doc.get("data")
            if not isinstance(data, dict):
                last_err = "response data is not an object"
                continue

            domain = (data.get("service_6006_domain") or "").strip()
            protocol = (data.get("service_6006_port_protocol") or "http").strip().lower()
            if protocol not in {"http", "https", "tcp"}:
                protocol = "http"
            if protocol == "tcp":
                protocol = "http"

            if domain:
                if "://" in domain:
                    return domain.rstrip("/") + "/"
                return f"{protocol}://{domain}".rstrip("/") + "/"

        if attempt + 1 < retries:
            time.sleep(delay)

    print(
        f"[err] 未能从 AutoDL snapshot 解析 service_6006_domain（已重试 {retries} 次）。最后错误: {last_err}",
        file=sys.stderr,
    )
    sys.exit(1)


def main() -> None:
    url = fetch_base_url()
    sys.stdout.write(url)


if __name__ == "__main__":
    main()
