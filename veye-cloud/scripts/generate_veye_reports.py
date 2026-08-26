#!/usr/bin/env python3
"""按 veye 小队演示剧本日期生成安全报告与旅行小记（顺序执行，互斥切换模型）。"""
from __future__ import annotations

import asyncio
import sys
import uuid
from datetime import date

from app.config import get_settings
from app.db import create_engine_from_settings, create_session_factory
from app.models import ReportJob, Team, User
from app.report_service import run_report_job

TEAM_NAME = "veye小队"
REPORT_DATE = date(2026, 7, 8)

# 安全报告：三人各自视角；旅行小记：队长全队汇总
JOBS = [
    ("safety", "Euphoria"),
    ("safety", "Paddi"),
    ("safety", "HuauH"),
    ("travel", "HuauH"),
]


async def main() -> int:
    settings = get_settings()
    session_factory = create_session_factory(create_engine_from_settings(settings))

    with session_factory() as db:
        team = db.query(Team).filter(Team.name == TEAM_NAME).one_or_none()
        if team is None:
            print(f"未找到小队「{TEAM_NAME}」，请先运行 seed_veye_demo.py", file=sys.stderr)
            return 1
        huauh = db.query(User).filter(User.username == "HuauH").one()
        users = {
            u.username: u
            for u in db.query(User).filter(User.username.in_([j[1] for j in JOBS])).all()
        }
        for _, username in JOBS:
            if username not in users:
                print(f"缺少用户 {username}", file=sys.stderr)
                return 1

    results: list[tuple[str, str, str, str]] = []
    for report_type, username in JOBS:
        subject = users[username]
        job_id = uuid.uuid4()
        with session_factory() as db:
            job = ReportJob(
                id=job_id,
                team_id=team.id,
                subject_user_id=subject.id,
                requested_by_user_id=huauh.id,
                report_date=REPORT_DATE,
                report_type=report_type,
                status="pending",
                phase_detail={},
            )
            db.add(job)
            db.commit()
        label = f"{report_type} · {username} · {REPORT_DATE}"
        print(f"\n=== 生成 {label} ===", flush=True)
        try:
            await run_report_job(job_id, settings, session_factory)
        except Exception as e:
            print(f"失败: {e}", file=sys.stderr)
            results.append((report_type, username, "failed", str(e)))
            continue
        with session_factory() as db:
            job = db.get(ReportJob, job_id)
            status = job.status if job else "unknown"
            err = (job.phase_detail or {}).get("error", "") if job else ""
            results.append((report_type, username, status, err))
            if job and job.status == "done":
                preview = (job.markdown or "")[:120].replace("\n", " ")
                print(f"完成: {preview}…", flush=True)
            else:
                print(f"状态: {status} {err}", flush=True)

    print("\n=== 汇总 ===")
    ok = 0
    for report_type, username, status, err in results:
        mark = "✓" if status == "done" else "✗"
        print(f"{mark} {report_type:6} {username:10} {status} {err}")
        if status == "done":
            ok += 1
    return 0 if ok == len(JOBS) else 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
