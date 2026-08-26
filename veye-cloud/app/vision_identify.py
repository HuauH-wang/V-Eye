from __future__ import annotations

import logging
import time
import uuid
from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy.orm import Session, sessionmaker

from .config import Settings
from .image_utils import _safe_ext, load_and_resize, save_image, to_data_url
from .models import IdentifyRecord, User
from .prompt import build_system_prompt, build_user_prompt
from .schemas import IdentifyResponse, Scene
from .sensor_service import build_sensor_json, save_track_point, upsert_member_location
from .vlm_client import VlmError, chat_completions_image

logger = logging.getLogger(__name__)

_TOXIC_PLANT_KEYWORDS = {
    "夹竹桃",
    "曼陀罗",
    "乌头",
    "附子",
    "蓖麻",
    "毒芹",
    "铃兰",
    "马缨丹",
    "一品红",
    "nerium",
    "oleander",
    "datura",
    "aconite",
    "castor",
    "poison hemlock",
    "lily of the valley",
    "lantana",
    "poinsettia",
}

_GENERIC_PLANT_LABELS = {
    "植物",
    "花",
    "叶子",
    "绿植",
    "盆栽",
    "plant",
    "flower",
    "leaf",
}


def _contains_toxic_plant_hint(text: str) -> bool:
    t = text.lower().strip()
    if not t:
        return False
    for kw in _TOXIC_PLANT_KEYWORDS:
        if kw in t:
            return True
    return False


def _rerank_candidates_for_toxic_plant(
    label_main: str,
    confidence: float,
    candidates: list[dict],
) -> tuple[str, float, list[dict]]:
    if not candidates:
        return label_main, confidence, candidates

    sorted_candidates = sorted(
        candidates,
        key=lambda x: float(x.get("confidence", 0.0)),
        reverse=True,
    )
    top = sorted_candidates[0]
    top_label = str(top.get("label", "")).strip()
    top_conf = float(top.get("confidence", 0.0))

    is_generic = label_main.strip().lower() in _GENERIC_PLANT_LABELS
    toxic_top = _contains_toxic_plant_hint(top_label)
    close_confidence = (top_conf + 0.08) >= confidence
    if toxic_top and (is_generic or close_confidence):
        return top_label, max(confidence, top_conf), sorted_candidates
    return label_main, confidence, sorted_candidates


def _apply_toxic_plant_postprocess(
    *,
    label_main: str,
    candidates: list[dict],
    risk_level: int,
    risk_tags: list[str],
    summary: str,
    advice: str,
) -> tuple[int, list[str], str, str]:
    texts: list[str] = [label_main, summary]
    texts.extend([str(c.get("label", "")) for c in candidates])
    hit = any(_contains_toxic_plant_hint(x) for x in texts)
    if not hit:
        return risk_level, risk_tags, summary, advice

    new_risk_level = max(risk_level, 3)
    new_tags = list(risk_tags)
    if "toxic_plant_suspected" not in new_tags:
        new_tags.append("toxic_plant_suspected")

    new_summary = summary
    if "有毒" not in new_summary and "toxic" not in new_summary.lower():
        prefix = "检测到疑似有毒植物，请提高警惕。"
        new_summary = f"{prefix} {summary}".strip()

    new_advice = advice
    advice_hint = "避免直接接触或误食，远离儿童和宠物，必要时联系专业人员确认。"
    if "避免" not in new_advice and "avoid" not in new_advice.lower():
        new_advice = f"{advice_hint} {advice}".strip()

    return new_risk_level, new_tags, new_summary, new_advice


async def re_identify_record(
    *,
    settings: Settings,
    session_factory: sessionmaker[Session],
    record: IdentifyRecord,
) -> IdentifyRecord:
    """Re-run VL on an existing record's image and update DB."""
    from pathlib import Path

    path = Path(record.image_path)
    if not path.is_file():
        raise ValueError("image_not_found")

    body = path.read_bytes()
    scene: Scene = record.scene if record.scene in ("toxic_plant", "medicine", "generic") else "generic"
    lang = "zh-CN"

    resized_bytes, mime = load_and_resize(body, settings.MAX_IMAGE_LONG_EDGE)
    system_prompt = build_system_prompt(lang)
    user_prompt = build_user_prompt(scene, lang)
    data_url = to_data_url(mime, resized_bytes)

    model_obj = await chat_completions_image(
        settings=settings,
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        image_data_url=data_url,
    )

    label_main = str(model_obj.get("label_main", "")).strip()[:200]
    try:
        confidence = float(model_obj.get("confidence", 0.0))
    except Exception:  # noqa: BLE001
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))

    candidates_in = model_obj.get("candidates") or []
    candidates: list[dict] = []
    if isinstance(candidates_in, list):
        for c in candidates_in[:5]:
            if not isinstance(c, dict):
                continue
            lbl = str(c.get("label", "")).strip()[:200]
            try:
                conf = float(c.get("confidence", 0.0))
            except Exception:  # noqa: BLE001
                conf = 0.0
            conf = max(0.0, min(1.0, conf))
            if lbl:
                candidates.append({"label": lbl, "confidence": conf})

    label_main, confidence, candidates = _rerank_candidates_for_toxic_plant(
        label_main=label_main,
        confidence=confidence,
        candidates=candidates,
    )

    try:
        risk_level = int(model_obj.get("risk_level", 0))
    except Exception:  # noqa: BLE001
        risk_level = 0
    risk_level = max(0, min(3, risk_level))

    risk_tags_in = model_obj.get("risk_tags") or []
    risk_tags: list[str] = []
    if isinstance(risk_tags_in, list):
        risk_tags = [str(x).strip()[:50] for x in risk_tags_in[:10] if str(x).strip()]

    summary = str(model_obj.get("summary", "")).strip()[:500]
    advice = str(model_obj.get("advice", "")).strip()[:800]

    risk_level, risk_tags, summary, advice = _apply_toxic_plant_postprocess(
        label_main=label_main or (candidates[0]["label"] if candidates else ""),
        candidates=candidates,
        risk_level=risk_level,
        risk_tags=risk_tags,
        summary=summary,
        advice=advice,
    )

    model_obj["summary"] = summary
    model_obj["advice"] = advice
    model_obj["risk_level"] = risk_level
    model_obj["risk_tags"] = risk_tags
    model_obj["confidence"] = confidence

    with session_factory() as db:
        row = db.get(IdentifyRecord, record.id)
        if row is None:
            raise ValueError("record_not_found")
        row.label_main = label_main or (candidates[0]["label"] if candidates else "")
        row.risk_level = risk_level
        row.result_json = model_obj
        db.commit()
        db.refresh(row)
        return row


async def run_identify(
    *,
    settings: Settings,
    session_factory: sessionmaker[Session],
    body: bytes,
    scene: Scene,
    lang: str = "zh-CN",
    device_id: str | None = None,
    filename: str | None = None,
    user: User | None = None,
    gps_lat: float | None = None,
    gps_lng: float | None = None,
    accuracy_m: float | None = None,
    team_id: str | None = None,
    client_ts: datetime | None = None,
    sensor_payload: dict | None = None,
) -> IdentifyResponse:
    t0 = time.perf_counter()
    request_id = str(uuid.uuid4())

    if len(body) > settings.MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="image too large")

    resized_bytes, mime = load_and_resize(body, settings.MAX_IMAGE_LONG_EDGE)
    ext = _safe_ext(mime, filename)
    image_path = save_image(settings.IMAGE_DIR, resized_bytes, ext)

    system_prompt = build_system_prompt(lang)
    user_prompt = build_user_prompt(scene, lang)
    data_url = to_data_url(mime, resized_bytes)

    try:
        model_obj = await chat_completions_image(
            settings=settings,
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            image_data_url=data_url,
        )
    except VlmError as e:
        raise HTTPException(status_code=502, detail=f"vlm_error: {str(e)}") from e
    except Exception as e:  # noqa: BLE001
        logger.exception("vision_identify: unexpected error calling vLLM: %s", e)
        raise HTTPException(status_code=502, detail="vlm_call_failed") from e

    label_main = str(model_obj.get("label_main", "")).strip()[:200]
    try:
        confidence = float(model_obj.get("confidence", 0.0))
    except Exception:  # noqa: BLE001
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))

    candidates_in = model_obj.get("candidates") or []
    candidates: list[dict] = []
    if isinstance(candidates_in, list):
        for c in candidates_in[:5]:
            if not isinstance(c, dict):
                continue
            lbl = str(c.get("label", "")).strip()[:200]
            try:
                conf = float(c.get("confidence", 0.0))
            except Exception:  # noqa: BLE001
                conf = 0.0
            conf = max(0.0, min(1.0, conf))
            if lbl:
                candidates.append({"label": lbl, "confidence": conf})

    label_main, confidence, candidates = _rerank_candidates_for_toxic_plant(
        label_main=label_main,
        confidence=confidence,
        candidates=candidates,
    )

    try:
        risk_level = int(model_obj.get("risk_level", 0))
    except Exception:  # noqa: BLE001
        risk_level = 0
    risk_level = max(0, min(3, risk_level))

    risk_tags_in = model_obj.get("risk_tags") or []
    risk_tags: list[str] = []
    if isinstance(risk_tags_in, list):
        risk_tags = [str(x).strip()[:50] for x in risk_tags_in[:10] if str(x).strip()]

    summary = str(model_obj.get("summary", "")).strip()[:500]
    advice = str(model_obj.get("advice", "")).strip()[:800]
    details = model_obj.get("details") if isinstance(model_obj.get("details"), dict) else {}

    risk_level, risk_tags, summary, advice = _apply_toxic_plant_postprocess(
        label_main=label_main or (candidates[0]["label"] if candidates else ""),
        candidates=candidates,
        risk_level=risk_level,
        risk_tags=risk_tags,
        summary=summary,
        advice=advice,
    )

    latency_ms = int((time.perf_counter() - t0) * 1000)
    sensor_json = build_sensor_json(sensor_payload or {}) or None
    team_uuid = None
    if team_id:
        try:
            team_uuid = uuid.UUID(team_id.strip())
        except ValueError:
            team_uuid = None

    try:
        with session_factory() as db:
            rec = IdentifyRecord(
                id=uuid.UUID(request_id),
                user_id=user.id if user else None,
                device_id=device_id,
                scene=str(scene),
                image_path=image_path,
                result_json=model_obj,
                risk_level=risk_level,
                label_main=label_main or (candidates[0]["label"] if candidates else ""),
                server_ts=datetime.now(timezone.utc),
                gps_lat=gps_lat,
                gps_lng=gps_lng,
                accuracy_m=accuracy_m,
                sensor_json=sensor_json,
            )
            db.add(rec)
            if user is not None and gps_lat is not None and gps_lng is not None:
                upsert_member_location(
                    db,
                    user=user,
                    team_id=team_uuid,
                    gps_lat=gps_lat,
                    gps_lng=gps_lng,
                    accuracy_m=accuracy_m,
                    client_ts=client_ts,
                )
                save_track_point(
                    db,
                    user=user,
                    team_id=team_uuid,
                    gps_lat=gps_lat,
                    gps_lng=gps_lng,
                    accuracy_m=accuracy_m,
                    client_ts=client_ts,
                    source="identify",
                    identify_record_id=rec.id,
                    payload=sensor_payload,
                )
            db.commit()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    return IdentifyResponse(
        request_id=request_id,
        label_main=label_main or (candidates[0]["label"] if candidates else ""),
        confidence=confidence,
        candidates=candidates or ([{"label": label_main, "confidence": confidence}] if label_main else []),
        risk_level=risk_level,
        risk_tags=risk_tags,
        summary=summary,
        advice=advice,
        details=details,
        latency_ms=latency_ms,
    )
