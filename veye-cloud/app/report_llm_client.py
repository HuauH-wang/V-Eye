from __future__ import annotations

import logging
from typing import Any

import httpx

from .config import Settings

logger = logging.getLogger(__name__)

# Conservative chars-per-token estimate for Chinese + JSON payloads (Qwen tokenizer).
_CHARS_PER_TOKEN = 1.8
_OUTPUT_HEADROOM = 256
_MIN_OUTPUT_TOKENS = 512


class ReportLlmError(RuntimeError):
    pass


def _estimate_tokens(text: str) -> int:
    if not text:
        return 0
    return max(1, int(len(text) / _CHARS_PER_TOKEN) + 1)


def _resolve_max_tokens(
    *,
    settings: Settings,
    system_prompt: str,
    user_prompt: str,
    requested_max_tokens: int,
) -> int:
    context_len = int(settings.REPORT_VLLM_MAX_MODEL_LEN)
    input_est = _estimate_tokens(system_prompt) + _estimate_tokens(user_prompt)
    budget = context_len - input_est - _OUTPUT_HEADROOM
    cap = min(requested_max_tokens, budget)
    if cap < _MIN_OUTPUT_TOKENS:
        logger.warning(
            "report_llm: tight context budget input_est=%s context=%s budget=%s",
            input_est,
            context_len,
            budget,
        )
        cap = max(256, budget)
    return max(256, cap)


async def chat_completions_text(
    *,
    settings: Settings,
    system_prompt: str,
    user_prompt: str,
    timeout_s: float | None = None,
    max_tokens: int | None = None,
    temperature: float = 0.3,
) -> str:
    requested = max_tokens if max_tokens is not None else int(settings.REPORT_VLLM_MAX_OUTPUT_TOKENS)
    resolved_max_tokens = _resolve_max_tokens(
        settings=settings,
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        requested_max_tokens=requested,
    )
    url = settings.REPORT_VLLM_BASE_URL.rstrip("/") + "/v1/chat/completions"
    read_timeout = float(timeout_s) if timeout_s is not None else float(settings.VLLM_HTTP_TIMEOUT_S)
    payload: dict[str, Any] = {
        "model": settings.REPORT_VLLM_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": temperature,
        "max_tokens": resolved_max_tokens,
    }
    logger.info(
        "report_llm: request max_tokens=%s (requested=%s, prompt_chars=%s)",
        resolved_max_tokens,
        requested,
        len(system_prompt) + len(user_prompt),
    )
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, read=read_timeout)) as client:
            resp = await client.post(url, json=payload)
            if resp.status_code >= 400:
                detail = resp.text[:800]
                logger.error("report_llm: HTTP %s body=%s", resp.status_code, detail)
                raise ReportLlmError(f"http_error_{resp.status_code}: {detail}") from None
            data = resp.json()
    except ReportLlmError:
        raise
    except httpx.HTTPError as e:
        logger.exception("report_llm: HTTP error: %s", e)
        raise ReportLlmError(f"http_error: {e}") from e
    except Exception as e:
        logger.exception("report_llm: unexpected error: %s", e)
        raise ReportLlmError(f"call_failed: {e}") from e

    choices = data.get("choices") or []
    if not choices:
        raise ReportLlmError("empty choices")
    message = choices[0].get("message") or {}
    content = message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise ReportLlmError("empty content")
    return content.strip()
