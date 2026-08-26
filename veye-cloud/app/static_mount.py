from __future__ import annotations

from pathlib import Path

from fastapi.staticfiles import StaticFiles
from starlette.responses import FileResponse, JSONResponse, Response
from starlette.types import Receive, Scope, Send

_API_PATH_PREFIXES = (
    "/auth/",
    "/vision/",
    "/map/",
    "/teams/",
    "/mail/",
    "/devices/",
    "/camera/",
    "/health",
    "/sos",
    "/docs",
    "/redoc",
    "/openapi.json",
)


class ApiSafeStaticFiles(StaticFiles):
    """避免 SPA 静态层对 API 路径的 POST/PATCH 等请求返回 405。"""

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and scope.get("method") not in ("GET", "HEAD"):
            path = scope.get("path", "")
            if any(path == p.rstrip("/") or path.startswith(p) for p in _API_PATH_PREFIXES):
                resp = JSONResponse(
                    status_code=404,
                    content={"detail": "api_route_not_found_restart_server"},
                )
                await resp(scope, receive, send)
                return
        await super().__call__(scope, receive, send)

    async def get_response(self, path: str, scope: Scope) -> Response:
        response = await super().get_response(path, scope)
        if path in ("", ".", "index.html") and isinstance(response, FileResponse):
            response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
            response.headers["Pragma"] = "no-cache"
        return response


def mount_web_ui(app, web_root: Path) -> None:
    if (web_root / "index.html").is_file():
        app.mount("/", ApiSafeStaticFiles(directory=str(web_root), html=True), name="web_ui")
