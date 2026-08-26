import type {
  ModelStatusResponse,
  ReportJobItem,
  ReportJobListResponse,
  ReportJobStatus,
  ReportPreviewResponse,
  ReportType,
} from "./types";
import { apiFetch, readErrorBody } from "./api";

export const REPORT_TYPE_LABELS: Record<ReportType, string> = {
  safety: "安全报告",
  travel: "旅行小记",
};

export async function fetchModelStatus(): Promise<ModelStatusResponse> {
  const res = await apiFetch("/reports/model-status", { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ModelStatusResponse;
}

export async function createReportJob(body: {
  team_id: string;
  user_id: string;
  date: string;
  report_type?: ReportType;
  force_reidentify?: boolean;
}): Promise<ReportJobItem> {
  const res = await apiFetch("/reports/jobs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ReportJobItem;
}

export async function updateReportJob(jobId: string, markdown: string): Promise<ReportJobItem> {
  const res = await apiFetch(`/reports/jobs/${jobId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ markdown }),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ReportJobItem;
}

export async function deleteReportJob(jobId: string): Promise<void> {
  const res = await apiFetch(`/reports/jobs/${jobId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function fetchReportJob(jobId: string): Promise<ReportJobItem> {
  const res = await apiFetch(`/reports/jobs/${jobId}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ReportJobItem;
}

export async function fetchReportJobs(params: {
  team_id?: string;
  user_id?: string;
  date?: string;
  status?: ReportJobStatus;
  limit?: number;
}): Promise<ReportJobListResponse> {
  const qs = new URLSearchParams();
  if (params.team_id) qs.set("team_id", params.team_id);
  if (params.user_id) qs.set("user_id", params.user_id);
  if (params.date) qs.set("date", params.date);
  if (params.status) qs.set("status", params.status);
  if (params.limit) qs.set("limit", String(params.limit));
  const q = qs.toString();
  const res = await apiFetch(`/reports/jobs${q ? `?${q}` : ""}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ReportJobListResponse;
}

export function reportErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    model_busy: "另有报告正在生成，请稍候再试",
    not_team_owner: "仅队长可查看其他队员的报告",
    subject_not_in_team: "该成员不在当前小队",
    not_team_member: "你不是该小队成员",
    team_not_found: "小队不存在",
    user_not_found: "用户不存在",
    invalid_date: "日期格式无效",
    job_not_found: "任务不存在",
    report_generation_in_progress: "报告生成中，识图功能暂时不可用",
    job_not_editable: "仅已完成的报告可编辑",
    job_not_deletable: "报告生成中，无法删除",
    forbidden: "无权删除该报告",
    not_authenticated: "请先登录",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}

export const REPORT_STATUS_LABELS: Record<ReportJobStatus, string> = {
  pending: "排队中",
  aggregating: "聚合数据",
  identifying: "补识图中",
  writing: "撰写报告",
  done: "已完成",
  failed: "失败",
  cancelled: "已取消",
};

export function todayIsoDate(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function phaseLabel(job: ReportJobItem): string {
  const base = REPORT_STATUS_LABELS[job.status] ?? job.status;
  if (job.status === "identifying") {
    const total = Number(job.phase_detail.missing_total ?? 0);
    const done = Number(job.phase_detail.missing_done ?? 0);
    if (total > 0) return `${base} (${done}/${total})`;
  }
  return base;
}

export async function fetchReportPreview(params: {
  team_id: string;
  user_id: string;
  date: string;
}): Promise<ReportPreviewResponse> {
  const qs = new URLSearchParams({
    team_id: params.team_id,
    user_id: params.user_id,
    date: params.date,
  });
  const res = await apiFetch(`/reports/preview?${qs}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ReportPreviewResponse;
}

/** Minimal Markdown → HTML for report preview */
export function renderMarkdown(md: string): string {
  const escape = (s: string) =>
    s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

  const lines = md.split("\n");
  const out: string[] = [];
  let inUl = false;

  const closeUl = () => {
    if (inUl) {
      out.push("</ul>");
      inUl = false;
    }
  };

  for (const raw of lines) {
    const line = raw.trimEnd();
    if (!line.trim()) {
      closeUl();
      continue;
    }
    if (line.startsWith("### ")) {
      closeUl();
      out.push(`<h3>${inlineFormat(escape(line.slice(4)))}</h3>`);
      continue;
    }
    if (line.startsWith("## ")) {
      closeUl();
      out.push(`<h2>${inlineFormat(escape(line.slice(3)))}</h2>`);
      continue;
    }
    if (line.startsWith("# ")) {
      closeUl();
      out.push(`<h1>${inlineFormat(escape(line.slice(2)))}</h1>`);
      continue;
    }
    if (line.startsWith("- ") || line.startsWith("* ")) {
      if (!inUl) {
        out.push("<ul>");
        inUl = true;
      }
      out.push(`<li>${inlineFormat(escape(line.slice(2)))}</li>`);
      continue;
    }
    closeUl();
    out.push(`<p>${inlineFormat(escape(line))}</p>`);
  }
  closeUl();
  return out.join("\n");
}

function inlineFormat(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
}
