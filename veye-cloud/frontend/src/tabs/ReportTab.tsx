import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import ReportCoverCard from "../components/ReportCoverCard";
import ReportEditor from "../components/ReportEditor";
import ReportMarkdownViewer from "../components/ReportMarkdownViewer";
import ReportOverlay from "../components/ReportOverlay";
import { fetchTeam, fetchTeams, teamErrorMessage } from "../lib/team";
import {
  REPORT_TYPE_LABELS,
  createReportJob,
  deleteReportJob,
  fetchModelStatus,
  fetchReportJob,
  fetchReportJobs,
  fetchReportPreview,
  phaseLabel,
  reportErrorMessage,
  todayIsoDate,
} from "../lib/reports";
import type { ReportJobItem, ReportPreviewResponse, ReportType, TeamDetail, TeamSummary } from "../lib/types";
import { formatTs } from "../lib/types";

type Props = {
  onActivity: (msg: string) => void;
};

type ModalState =
  | { mode: "view"; job: ReportJobItem }
  | { mode: "edit"; job: ReportJobItem }
  | null;

const ACTIVE = new Set(["pending", "aggregating", "identifying", "writing"]);

function progressPercent(job: ReportJobItem | null): number {
  if (!job) return 0;
  switch (job.status) {
    case "pending":
      return 5;
    case "aggregating":
      return 20;
    case "identifying": {
      const total = Number(job.phase_detail.missing_total ?? 0);
      const done = Number(job.phase_detail.missing_done ?? 0);
      if (total <= 0) return 45;
      return 25 + Math.round((done / total) * 35);
    }
    case "writing":
      return 85;
    case "done":
      return 100;
    default:
      return 0;
  }
}

function reportTitle(job: ReportJobItem): string {
  return `${REPORT_TYPE_LABELS[job.report_type ?? "safety"]} · ${job.subject_display_name} · ${job.report_date}`;
}

function reportSubtitle(job: ReportJobItem): string {
  const parts = [`生成于 ${formatTs(job.finished_at ?? job.updated_at)}`, `请求人 ${job.requested_by_display_name}`];
  if (job.is_edited && job.edited_at) parts.push(`最后编辑 ${formatTs(job.edited_at)}`);
  return parts.join(" · ");
}

export default function ReportTab({ onActivity }: Props) {
  const { user } = useAuth();
  const [teams, setTeams] = useState<TeamSummary[]>([]);
  const [teamId, setTeamId] = useState("");
  const [detail, setDetail] = useState<TeamDetail | null>(null);
  const [memberId, setMemberId] = useState("");
  const [reportDate, setReportDate] = useState(todayIsoDate);
  const [reportType, setReportType] = useState<ReportType>("safety");
  const [forceReidentify, setForceReidentify] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [activeJob, setActiveJob] = useState<ReportJobItem | null>(null);
  const [modal, setModal] = useState<ModalState>(null);
  const [history, setHistory] = useState<ReportJobItem[]>([]);
  const [modelBusy, setModelBusy] = useState(false);
  const [dataPreview, setDataPreview] = useState<ReportPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const pollRef = useRef<number | null>(null);

  const isOwner = detail && user && detail.owner_id === user.id;

  const loadTeams = useCallback(async () => {
    try {
      const data = await fetchTeams();
      setTeams(data.items);
      if (data.items.length && !teamId) {
        setTeamId(data.items[0].id);
      }
    } catch (e) {
      setErr(teamErrorMessage(e));
    }
  }, [teamId]);

  const loadHistory = useCallback(async () => {
    if (!teamId) return;
    try {
      const data = await fetchReportJobs({ team_id: teamId, limit: 30 });
      setHistory(data.items);
    } catch {
      /* ignore */
    }
  }, [teamId]);

  useEffect(() => {
    loadTeams();
  }, [loadTeams]);

  useEffect(() => {
    if (!teamId) {
      setDetail(null);
      return;
    }
    fetchTeam(teamId)
      .then((d) => {
        setDetail(d);
        if (user) {
          if (d.owner_id === user.id) {
            setMemberId((prev) => prev || user.id);
          } else {
            setMemberId(user.id);
          }
        }
      })
      .catch((e) => setErr(teamErrorMessage(e)));
  }, [teamId, user]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory, activeJob?.status]);

  useEffect(() => {
    if (!teamId || !memberId || !reportDate) {
      setDataPreview(null);
      return;
    }
    setPreviewLoading(true);
    fetchReportPreview({ team_id: teamId, user_id: memberId, date: reportDate })
      .then(setDataPreview)
      .catch(() => setDataPreview(null))
      .finally(() => setPreviewLoading(false));
  }, [teamId, memberId, reportDate]);

  useEffect(() => {
    const tick = () => {
      fetchModelStatus()
        .then((s) => setModelBusy(s.report_job_active))
        .catch(() => undefined);
    };
    tick();
    const t = window.setInterval(tick, 5000);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => {
    if (!activeJob || !ACTIVE.has(activeJob.status)) {
      if (pollRef.current) {
        window.clearInterval(pollRef.current);
        pollRef.current = null;
      }
      return;
    }
    const poll = () => {
      fetchReportJob(activeJob.id)
        .then((job) => {
          setActiveJob(job);
          loadHistory();
          if (job.status === "done") {
            setModal({ mode: "view", job });
            onActivity(`报告已生成：${job.subject_display_name} · ${job.report_date}`);
          } else if (job.status === "failed") {
            setErr(String(job.phase_detail.error ?? "生成失败"));
            onActivity("报告生成失败");
            loadHistory();
          }
        })
        .catch((e) => setErr(reportErrorMessage(e)));
    };
    pollRef.current = window.setInterval(poll, 2000);
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current);
    };
  }, [activeJob?.id, activeJob?.status, loadHistory, onActivity]);

  const memberOptions = useMemo(() => {
    if (!detail) return [];
    if (isOwner) return detail.members;
    return detail.members.filter((m) => m.user_id === user?.id);
  }, [detail, isOwner, user?.id]);

  const handleGenerate = async () => {
    if (!teamId || !memberId || !reportDate) return;
    setErr("");
    setBusy(true);
    setModal(null);
    try {
      const job = await createReportJob({
        team_id: teamId,
        user_id: memberId,
        date: reportDate,
        report_type: reportType,
        force_reidentify: forceReidentify,
      });
      setActiveJob(job);
      onActivity(`开始生成${REPORT_TYPE_LABELS[reportType]}：${job.subject_display_name} · ${job.report_date}`);
      loadHistory();
    } catch (e) {
      setErr(reportErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const handleJobSaved = (job: ReportJobItem) => {
    setModal({ mode: "edit", job });
    setHistory((prev) => prev.map((h) => (h.id === job.id ? job : h)));
  };

  const openReport = useCallback(async (mode: "view" | "edit", job: ReportJobItem) => {
    setErr("");
    try {
      const fresh = await fetchReportJob(job.id);
      setHistory((prev) => prev.map((h) => (h.id === fresh.id ? fresh : h)));
      setModal({ mode, job: fresh });
    } catch (e) {
      setErr(reportErrorMessage(e));
      setModal({ mode, job });
    }
  }, []);

  const handleDeleteReport = async (job: ReportJobItem) => {
    if (!window.confirm(`确定删除「${REPORT_TYPE_LABELS[job.report_type ?? "safety"]} · ${job.report_date}」？此操作不可恢复。`)) {
      return;
    }
    setErr("");
    try {
      await deleteReportJob(job.id);
      setHistory((prev) => prev.filter((h) => h.id !== job.id));
      if (modal?.job.id === job.id) setModal(null);
      if (activeJob?.id === job.id) setActiveJob(null);
      onActivity(`已删除报告：${job.report_date}`);
    } catch (e) {
      setErr(reportErrorMessage(e));
    }
  };

  const handleCopy = async (job: ReportJobItem) => {
    if (!job.markdown) return;
    try {
      await navigator.clipboard.writeText(job.markdown);
      onActivity("报告已复制到剪贴板");
    } catch {
      setErr("复制失败");
    }
  };

  const handleDownload = (job: ReportJobItem) => {
    if (!job.markdown) return;
    const blob = new Blob([job.markdown], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${REPORT_TYPE_LABELS[job.report_type ?? "safety"]}-${job.report_date}-${job.subject_display_name}.md`;
    a.click();
    URL.revokeObjectURL(url);
    onActivity("报告已下载");
  };

  const generating = activeJob !== null && ACTIVE.has(activeJob.status);
  const modalJob = modal?.job ?? null;

  return (
    <div className="report-layout page-fill">
      {generating || modelBusy ? (
        <div className="report-banner neo-card" role="status">
          报告生成中，实时识图功能暂时不可用。当前阶段：{activeJob ? phaseLabel(activeJob) : "准备中"}…
        </div>
      ) : null}

      <section className="report-toolbar neo-card">
        <div className="report-toolbar-row">
          <div className="field">
            <label htmlFor="report-team">小队</label>
            <select
              id="report-team"
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
              disabled={generating}
            >
              {teams.length === 0 ? <option value="">暂无小队</option> : null}
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="report-member">成员</label>
            <select
              id="report-member"
              value={memberId}
              onChange={(e) => setMemberId(e.target.value)}
              disabled={generating || !isOwner}
            >
              {memberOptions.map((m) => (
                <option key={m.user_id} value={m.user_id}>
                  {m.display_name}
                  {m.role === "owner" ? "（队长）" : ""}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="report-type">报告类型</label>
            <select
              id="report-type"
              value={reportType}
              onChange={(e) => setReportType(e.target.value as ReportType)}
              disabled={generating}
            >
              <option value="safety">{REPORT_TYPE_LABELS.safety}</option>
              <option value="travel">{REPORT_TYPE_LABELS.travel}</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="report-date">日期</label>
            <input
              id="report-date"
              type="date"
              value={reportDate}
              onChange={(e) => setReportDate(e.target.value)}
              disabled={generating}
            />
          </div>
        </div>
        {previewLoading ? (
          <p className="muted small">正在检查当日数据…</p>
        ) : dataPreview ? (
          <div className="report-preview-box">
            <div className="report-preview-stats">
              <span>识图 {dataPreview.identify_count}</span>
              <span>SOS {dataPreview.sos_count}</span>
              <span>聊天 {dataPreview.chat_messages_by_subject}</span>
            </div>
            {!dataPreview.has_data && dataPreview.empty_hint ? (
              <p className="report-preview-hint">{dataPreview.empty_hint}</p>
            ) : null}
            {!dataPreview.has_data && dataPreview.suggested_report_date ? (
              <button
                type="button"
                className="btn ghost"
                onClick={() => setReportDate(dataPreview.suggested_report_date!)}
              >
                切换到有数据的日期（{dataPreview.suggested_report_date}）
              </button>
            ) : null}
          </div>
        ) : null}
        <div className="report-toolbar-actions">
          <label className="muted small">
            <input
              type="checkbox"
              checked={forceReidentify}
              onChange={(e) => setForceReidentify(e.target.checked)}
              disabled={generating}
            />{" "}
            强制重新识图（两阶段，耗时更长）
          </label>
          <button
            type="button"
            className="btn primary"
            onClick={handleGenerate}
            disabled={busy || generating || !teamId || !memberId}
          >
            {generating ? "生成中…" : "生成报告"}
          </button>
        </div>
        {err ? <p className="alert inline">{err}</p> : null}
      </section>

      {activeJob && ACTIVE.has(activeJob.status) ? (
        <section className="report-progress neo-card">
          <strong>{phaseLabel(activeJob)}</strong>
          <div className="report-progress-bar">
            <div className="report-progress-fill" style={{ width: `${progressPercent(activeJob)}%` }} />
          </div>
          <p className="muted small">模型切换与推理可能需要 2–5 分钟，请保持页面打开。</p>
        </section>
      ) : null}

      <section className="report-gallery-section neo-card page-fill-panel">
        <div className="report-gallery-head">
          <h3>我的报告</h3>
        </div>
        {history.length === 0 ? (
          <div className="report-empty">
            <p>暂无报告</p>
            <p className="muted small">选择小队、成员与日期，点击「生成报告」</p>
          </div>
        ) : (
          <div className="report-gallery">
            {history.map((h) => (
              <ReportCoverCard
                key={h.id}
                job={h}
                onView={() => void openReport("view", h)}
                onEdit={() => void openReport("edit", h)}
                onDelete={() => void handleDeleteReport(h)}
              />
            ))}
          </div>
        )}
      </section>

      {modalJob && modal?.mode === "view" ? (
        <ReportOverlay
          open
          title={reportTitle(modalJob)}
          subtitle={modalJob.status === "done" ? reportSubtitle(modalJob) : undefined}
          onClose={() => setModal(null)}
          headerActions={
            modalJob.markdown ? (
              <>
                {modalJob.status === "done" ? (
                  <button type="button" className="btn ghost" onClick={() => setModal({ mode: "edit", job: modalJob })}>
                    编辑
                  </button>
                ) : null}
                <button type="button" className="btn ghost" onClick={() => void handleCopy(modalJob)}>
                  复制
                </button>
                <button type="button" className="btn secondary" onClick={() => handleDownload(modalJob)}>
                  下载 .md
                </button>
                {!["aggregating", "identifying", "writing"].includes(modalJob.status) ? (
                  <button type="button" className="btn danger ghost" onClick={() => void handleDeleteReport(modalJob)}>
                    删除
                  </button>
                ) : null}
              </>
            ) : !["aggregating", "identifying", "writing"].includes(modalJob.status) ? (
              <button type="button" className="btn danger ghost" onClick={() => void handleDeleteReport(modalJob)}>
                删除
              </button>
            ) : null
          }
        >
          {modalJob.markdown ? (
            <div className="report-view-scroll">
              <ReportMarkdownViewer markdown={modalJob.markdown} />
            </div>
          ) : modalJob.status === "failed" ? (
            <div className="report-failed-view">
              <p className="alert inline">{String(modalJob.phase_detail.error ?? "未知错误")}</p>
              {modalJob.timeline_json ? (
                <>
                  <p className="muted">已聚合的数据如下，可供人工参考：</p>
                  <pre className="code-block">{JSON.stringify(modalJob.timeline_json, null, 2)}</pre>
                </>
              ) : null}
            </div>
          ) : (
            <p className="muted">报告尚未生成完成</p>
          )}
        </ReportOverlay>
      ) : null}

      {modalJob && modal?.mode === "edit" ? (
        <ReportOverlay
          open
          wide
          title={reportTitle(modalJob)}
          subtitle={reportSubtitle(modalJob)}
          onClose={() => setModal(null)}
        >
          <ReportEditor
            job={modalJob}
            onSaved={handleJobSaved}
            onActivity={onActivity}
            onError={setErr}
          />
        </ReportOverlay>
      ) : null}
    </div>
  );
}
