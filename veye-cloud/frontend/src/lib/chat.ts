import type {
  ChatConversationItem,
  ChatConversationsResponse,
  ChatMessageItem,
  ChatMessagesResponse,
} from "./types";
import { apiFetch, readErrorBody } from "./api";

export async function fetchConversations(): Promise<ChatConversationsResponse> {
  const res = await apiFetch("/teams/chat/conversations", { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ChatConversationsResponse;
}

export async function fetchMessages(
  teamId: string,
  opts?: { limit?: number; before?: string; since?: string },
): Promise<ChatMessagesResponse> {
  const params = new URLSearchParams();
  if (opts?.limit) params.set("limit", String(opts.limit));
  if (opts?.before) params.set("before", opts.before);
  if (opts?.since) params.set("since", opts.since);
  const qs = params.toString();
  const res = await apiFetch(`/teams/${teamId}/messages${qs ? `?${qs}` : ""}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ChatMessagesResponse;
}

export async function sendMessage(teamId: string, body: string): Promise<ChatMessageItem> {
  const res = await apiFetch(`/teams/${teamId}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ body }),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as ChatMessageItem;
}

export async function markChatRead(teamId: string): Promise<void> {
  const res = await apiFetch(`/teams/${teamId}/messages/read`, { method: "POST" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export function chatErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    team_not_found: "小队不存在",
    not_team_member: "你不是该小队成员",
    message_empty: "消息不能为空",
    message_too_long: "消息过长（最多 2000 字）",
    api_route_missing_restart: "服务端未加载最新接口，请重启 FastAPI",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}

export function previewText(text: string, max = 32): string {
  const t = text.replace(/\s+/g, " ").trim();
  if (t.length <= max) return t;
  return `${t.slice(0, max)}…`;
}

export type { ChatConversationItem };
