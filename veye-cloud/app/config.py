from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    API_HOST: str = "0.0.0.0"
    API_PORT: int = 6006
    # 可选：为空/未设置时不启用鉴权
    API_KEY: str = ""

    # 可选：浏览器跨域访问 API 时配置，逗号分隔多个 Origin，例如 http://localhost:5173
    CORS_ALLOW_ORIGINS: str = ""

    VLLM_BASE_URL: str = "http://127.0.0.1:8001"
    VLLM_MODEL: str = "Qwen/Qwen2.5-VL-7B-Instruct"
    VLLM_USE_V1: str | None = None
    # 调用 vLLM /v1/chat/completions 的读超时（秒）；首包/大图推理可能较慢
    VLLM_HTTP_TIMEOUT_S: float = 180.0

    REPORT_VLLM_BASE_URL: str = "http://127.0.0.1:8002"
    REPORT_VLLM_MODEL: str = "/root/autodl-tmp/.cache/modelscope/models/Qwen/Qwen2.5-7B-Instruct"
    REPORT_VLLM_MAX_MODEL_LEN: int = 8192
    REPORT_VLLM_MAX_OUTPUT_TOKENS: int = 2048
    REPORT_VLLM_GPU_MEMORY_UTILIZATION: float = 0.75
    MODEL_SWITCH_TIMEOUT_S: float = 180.0
    REPORT_TIMEZONE: str = "Asia/Shanghai"

    DATABASE_URL: str = "postgresql+psycopg://veye:veye_password@127.0.0.1:5432/veye"

    IMAGE_DIR: str = "/data/veye/images"
    AVATAR_DIR: str = "/data/veye/avatars"
    TILE_CACHE_DIR: str = "/data/veye/tiles"
    MAX_AVATAR_BYTES: int = 2_097_152

    MAX_IMAGE_BYTES: int = 104_857_600
    MAX_IMAGE_LONG_EDGE: int = 1024

    JWT_SECRET: str = "veye-dev-change-me-in-production"
    JWT_EXPIRE_HOURS: int = 168


def get_settings() -> Settings:
    return Settings()

