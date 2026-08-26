from __future__ import annotations

import json
import re
from typing import Any

import httpx

from .config import Settings


class VlmError(RuntimeError):
    pass


def _extract_json_object(text: str) -> str:
    s = text.strip()
    if s.startswith("{") and s.endswith("}"):
        return s

    m = re.search(r"\{[\s\S]*\}", s)
    if not m:
        raise VlmError("model output does not contain a JSON object")
    return m.group(0)


def parse_model_json(text: str) -> dict[str, Any]:
    raw = _extract_json_object(text)
    try:
        obj = json.loads(raw)
        if not isinstance(obj, dict):
            raise VlmError("model JSON is not an object")
        return obj
    except json.JSONDecodeError:
        cleaned = raw.replace("\uFEFF", "").strip()
        cleaned = re.sub(r",\s*([}\]])", r"\1", cleaned)
        obj = json.loads(cleaned)
        if not isinstance(obj, dict):
            raise VlmError("model JSON is not an object")
        return obj


async def chat_completions_image(
    *,
    settings: Settings,
    system_prompt: str,
    user_prompt: str,
    image_data_url: str,
    timeout_s: float | None = None,
) -> dict[str, Any]:
    url = settings.VLLM_BASE_URL.rstrip("/") + "/v1/chat/completions"
    read_timeout = float(timeout_s) if timeout_s is not None else float(settings.VLLM_HTTP_TIMEOUT_S)
    payload: dict[str, Any] = {
        "model": settings.VLLM_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_prompt},
                    {"type": "image_url", "image_url": {"url": image_data_url}},
                ],
            },
        ],
        "temperature": 0.2,
        "max_tokens": 512,
    }

    # trust_env=False：避免 HTTP(S)_PROXY 把访问本机 vLLM 的流量错误地走代理导致连接失败
    timeout = httpx.Timeout(connect=30.0, read=read_timeout, write=120.0, pool=10.0)
    async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
        try:
            r = await client.post(url, json=payload)
        except httpx.TimeoutException as e:
            raise VlmError(
                f"vLLM read timeout ({read_timeout}s); try raising VLLM_HTTP_TIMEOUT_S in .env. {e}"
            ) from e
        except httpx.ConnectError as e:
            raise VlmError(
                f"vLLM connect failed: {url!s} -> {e}. "
                "If FastAPI runs in Docker/K8s, set VLLM_BASE_URL to the vLLM service "
                "address reachable from this process (not 127.0.0.1 unless vLLM is in the same network namespace)."
            ) from e
        except httpx.RequestError as e:
            raise VlmError(f"vLLM request failed: {e}") from e
        if r.status_code >= 400:
            raise VlmError(f"vLLM HTTP {r.status_code}: {r.text[:500]}")
        try:
            data = r.json()
        except ValueError as e:
            raise VlmError(f"vLLM returned non-JSON body (HTTP {r.status_code}): {r.text[:300]!r}") from e

    try:
        content = data["choices"][0]["message"]["content"]
    except Exception as e:  # noqa: BLE001
        raise VlmError(f"unexpected vLLM response shape: {str(e)}") from e

    if not isinstance(content, str):
        raise VlmError("unexpected content type from model")

    return parse_model_json(content)

