from __future__ import annotations

import json
import re
from typing import Any, Literal

ReportType = Literal["safety", "travel"]

IMAGE_PROTOCOL = "veye://image/"


def image_markdown(record_id: str, caption: str) -> str:
    safe_caption = caption.replace("\n", " ").strip() or "识图记录"
    return f"![{safe_caption}]({IMAGE_PROTOCOL}{record_id})"


def build_report_system_prompt(report_type: ReportType = "safety") -> str:
    image_rules = (
        f"5. 配图：在描述相关识图场景时，于该段落后插入 Markdown 图片，格式为 "
        f"`![说明文字]({IMAGE_PROTOCOL}<record_id>)`。"
        f"`<record_id>` 必须来自 timeline.events 中 type=identify 且 has_image=true 的 record_id，"
        f"不要编造 ID。每处识图至少配一张图；旅行小记可在叙述转折处自然插入。"
    )
    if report_type == "travel":
        return (
            "你是户外旅行记录助手。根据提供的结构化活动数据，撰写一篇中文散文式旅行小记（Markdown）。\n"
            "要求：\n"
            "1. 只使用输入数据中的事实，不要编造未出现的事件或地点。\n"
            "2. 文风：第一人称或第三人称均可，偏游记/日记散文，有画面感与情绪起伏，但保持真实。\n"
            "3. 建议结构（使用 ## 标题，可按内容微调）：\n"
            "   - 启程\n"
            "   - 路途见闻\n"
            "   - 识图瞬间\n"
            "   - 险情与插曲（若无 SOS 可略写或省略）\n"
            "   - 同行与对话\n"
            "   - 收束\n"
            "4. 识图事件按时间顺序融入叙述；无数据时自然带过，不写「当日无相关记录」这类公文用语。\n"
            "5. 结合 trajectory / location 描述行进路线与里程；chat 事件请保留发言人姓名。\n"
            "6. 文末单独一行标注：「报告作者：<author_display_name>」。author 取自 meta.author_display_name。\n"
            f"{image_rules}"
        )
    return (
        "你是野外作业日报助手。根据提供的结构化工作数据，撰写一份清晰、专业的中文 Markdown 安全日报。\n"
        "要求：\n"
        "1. 只使用输入数据中的事实，不要编造未出现的事件。\n"
        "2. 按以下章节输出（使用 ## 标题）：\n"
        "   - 概览\n"
        "   - 活动摘要\n"
        "   - 识图与风险\n"
        "   - 险情与 SOS\n"
        "   - 协作与通讯\n"
        "   - 建议与待办\n"
        "3. 识图事件按 risk_level 从高到低提及；无数据时写「当日无相关记录」。\n"
        "4. 语言简洁，适合队长审阅与存档。\n"
        f"{image_rules}"
    )


def compact_timeline_for_llm(timeline: dict[str, Any]) -> dict[str, Any]:
    """Strip redundant fields to keep LLM prompt within context budget."""
    events: list[dict[str, Any]] = []
    for event in timeline.get("events") or []:
        etype = event.get("type")
        if etype == "identify":
            compact: dict[str, Any] = {
                "type": "identify",
                "ts": event.get("ts"),
                "record_id": event.get("record_id"),
                "label": event.get("label"),
                "risk_level": event.get("risk_level"),
                "summary": event.get("summary"),
                "has_image": event.get("has_image"),
            }
            if event.get("member"):
                compact["member"] = event.get("member")
            if event.get("scene") and event.get("scene") != "generic":
                compact["scene"] = event.get("scene")
            gps = event.get("gps")
            if isinstance(gps, dict) and gps.get("lat") is not None:
                compact["gps"] = gps
            events.append(compact)
        elif etype == "sos":
            compact = {
                "type": "sos",
                "ts": event.get("ts"),
                "event_type": event.get("event_type"),
            }
            if event.get("member"):
                compact["member"] = event.get("member")
            gps = event.get("gps")
            if isinstance(gps, dict) and gps.get("lat") is not None:
                compact["gps"] = gps
            events.append(compact)
        elif etype == "chat":
            compact = {
                "type": "chat",
                "ts": event.get("ts"),
                "author": event.get("author"),
                "body": event.get("body"),
            }
            if event.get("is_subject"):
                compact["is_subject"] = True
            events.append(compact)
        else:
            events.append(event)

    payload: dict[str, Any] = {
        "meta": timeline.get("meta") or {},
        "stats": timeline.get("stats") or {},
        "events": events,
    }
    location = timeline.get("location")
    if location:
        payload["location"] = location
    trajectory = timeline.get("trajectory")
    if trajectory:
        payload["trajectory"] = trajectory
    return payload


def build_report_user_prompt(meta: dict[str, Any], timeline: dict[str, Any]) -> str:
    payload = compact_timeline_for_llm({"meta": meta, **timeline})
    return (
        "请根据以下 JSON 数据撰写报告（Markdown）：\n\n"
        f"{json.dumps(payload, ensure_ascii=False, separators=(',', ':'))}"
    )


def _collect_timeline_images(timeline: dict[str, Any]) -> list[dict[str, str]]:
    images: list[dict[str, str]] = []
    seen: set[str] = set()
    for event in timeline.get("events") or []:
        if event.get("type") != "identify" or not event.get("has_image"):
            continue
        record_id = str(event.get("record_id") or "").strip()
        if not record_id or record_id in seen:
            continue
        seen.add(record_id)
        caption = str(event.get("image_caption") or event.get("label") or "识图记录").strip()
        images.append({"record_id": record_id, "caption": caption})
    for img in timeline.get("images") or []:
        record_id = str(img.get("record_id") or "").strip()
        if not record_id or record_id in seen:
            continue
        seen.add(record_id)
        caption = str(img.get("caption") or "识图记录").strip()
        images.append({"record_id": record_id, "caption": caption})
    return images


def ensure_report_images(markdown: str, timeline: dict[str, Any], report_type: ReportType = "safety") -> str:
    """Ensure identify images referenced in timeline appear in markdown."""
    images = _collect_timeline_images(timeline)
    if not images:
        return markdown

    text = markdown.strip()
    present_ids = set(re.findall(rf"{re.escape(IMAGE_PROTOCOL)}([0-9a-fA-F-]{{36}})", text))

    missing = [img for img in images if img["record_id"] not in present_ids]
    if not missing:
        return text

    section_title = "## 影像记录" if report_type == "safety" else "## 旅途影像"
    lines = [text, "", section_title, ""]
    for img in missing:
        lines.append(image_markdown(img["record_id"], img["caption"]))
        lines.append("")
    return "\n".join(lines).strip() + "\n"
