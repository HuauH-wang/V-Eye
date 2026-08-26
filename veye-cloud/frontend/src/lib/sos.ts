import type { ReportPreviewResponse, SosRecordsResponse } from "./types";
import { apiFetch, readErrorBody } from "./api";

export async function fetchSosRecords(params: {
  team_id?: string;
  days?: number;
  limit?: number;
} = {}): Promise<SosRecordsResponse> {
  const qs = new URLSearchParams();
  if (params.team_id) qs.set("team_id", params.team_id);
  if (params.days) qs.set("days", String(params.days));
  if (params.limit) qs.set("limit", String(params.limit));
  const q = qs.toString();
  const res = await apiFetch(`/sos/records${q ? `?${q}` : ""}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as SosRecordsResponse;
}

export async function deleteSosRecord(incidentId: string): Promise<void> {
  const res = await apiFetch(`/sos/records/${incidentId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export function sosErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    not_authenticated: "请先登录后查看 SOS 记录",
    not_team_member: "你不是该小队成员",
    team_not_found: "小队不存在",
    record_not_found: "记录不存在",
    forbidden: "无权删除该记录",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}

export const SOS_EVENT_LABELS: Record<string, string> = {
  manual_test: "手动测试",
  manual_sos: "手动 SOS",
  fall_detected: "跌倒检测",
  help: "求助",
};

export function sosEventLabel(type: string): string {
  return SOS_EVENT_LABELS[type] ?? type;
}
