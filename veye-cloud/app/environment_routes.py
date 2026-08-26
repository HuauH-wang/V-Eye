from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session, sessionmaker

from .deps import get_current_user, get_session_factory_dep
from .environment_service import get_latest_environment
from .models import User
from .schemas import EnvironmentLatestResponse

router = APIRouter(prefix="/environment", tags=["environment"])


@router.get("/latest", response_model=EnvironmentLatestResponse)
def environment_latest(
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> EnvironmentLatestResponse:
    with session_factory() as db:
        return get_latest_environment(db, current_user)
