from __future__ import annotations

import asyncio
import logging
import subprocess
from enum import Enum
from pathlib import Path

import time

import httpx

from .config import Settings

logger = logging.getLogger(__name__)

PROJECT_ROOT = Path(__file__).resolve().parent.parent
MODEL_SWITCH_SCRIPT = PROJECT_ROOT / "scripts" / "model_switch.sh"

_lock = asyncio.Lock()
_active_mode: str = "vision"
_report_job_active = False


class ModelMode(str, Enum):
    VISION = "vision"
    REPORT = "report"
    IDLE = "idle"
    BUSY = "busy"


def is_report_generation_active() -> bool:
    return _report_job_active


def get_active_mode() -> str:
    if _report_job_active:
        return ModelMode.BUSY.value
    return _active_mode


def _run_switch(cmd: str, settings: Settings) -> None:
    if not MODEL_SWITCH_SCRIPT.is_file():
        raise RuntimeError(f"model_switch script not found: {MODEL_SWITCH_SCRIPT}")
    env = {
        **dict(__import__("os").environ),
        "MODEL_SWITCH_TIMEOUT_S": str(int(settings.MODEL_SWITCH_TIMEOUT_S)),
    }
    proc = subprocess.run(
        ["bash", str(MODEL_SWITCH_SCRIPT), cmd],
        cwd=str(PROJECT_ROOT),
        env=env,
        capture_output=True,
        text=True,
        timeout=max(int(settings.MODEL_SWITCH_TIMEOUT_S) + 30, 60),
    )
    if proc.returncode != 0:
        logger.error("model_switch %s failed: %s\n%s", cmd, proc.stdout, proc.stderr)
        raise RuntimeError(f"model_switch_{cmd}_failed")
    logger.info("model_switch %s ok: %s", cmd, proc.stdout.strip())


async def _poll_health(base_url: str, timeout_s: float) -> bool:
    url = base_url.rstrip("/") + "/v1/models"
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(url)
                if resp.status_code == 200:
                    return True
        except Exception:
            pass
        await asyncio.sleep(2)
    return False


async def ensure_model(mode: ModelMode, settings: Settings) -> None:
    global _active_mode
    cmd_map = {
        ModelMode.VISION: "start_vision",
        ModelMode.REPORT: "start_report",
    }
    cmd = cmd_map.get(mode)
    if cmd is None:
        raise ValueError(f"unsupported mode: {mode}")

    loop = asyncio.get_running_loop()
    await loop.run_in_executor(None, _run_switch, cmd, settings)

    base = settings.VLLM_BASE_URL if mode == ModelMode.VISION else settings.REPORT_VLLM_BASE_URL
    ok = await _poll_health(base, settings.MODEL_SWITCH_TIMEOUT_S)
    if not ok:
        raise RuntimeError(f"model_not_ready:{mode.value}")

    _active_mode = mode.value
    logger.info("ensure_model: now %s", _active_mode)


class report_job_guard:
    """Context manager: mark report job active, restore vision model on exit."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def __aenter__(self) -> None:
        global _report_job_active
        await _lock.acquire()
        _report_job_active = True
        await ensure_model(ModelMode.REPORT, self._settings)

    async def __aexit__(self, exc_type, exc, tb) -> None:
        global _report_job_active, _active_mode
        try:
            try:
                await ensure_model(ModelMode.VISION, self._settings)
            except Exception:
                logger.exception("failed to restore vision model after report job")
        finally:
            _report_job_active = False
            _lock.release()


class full_report_pipeline_guard:
    """Acquire lock for entire pipeline including optional VL backfill."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def __aenter__(self) -> "full_report_pipeline_guard":
        global _report_job_active
        await _lock.acquire()
        _report_job_active = True
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, _run_switch, "stop_all", self._settings)
        await asyncio.sleep(2)
        _active_mode = ModelMode.IDLE.value
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        global _report_job_active, _active_mode
        try:
            try:
                await ensure_model(ModelMode.VISION, self._settings)
            except Exception:
                logger.exception("failed to restore vision model after report pipeline")
        finally:
            _report_job_active = False
            _active_mode = ModelMode.VISION.value
            _lock.release()

    async def switch_to_vision(self) -> None:
        await ensure_model(ModelMode.VISION, self._settings)

    async def switch_to_report(self) -> None:
        await ensure_model(ModelMode.REPORT, self._settings)
