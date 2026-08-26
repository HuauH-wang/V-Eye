import type {
  MailItem,
  MailListResponse,
  TeamDetail,
  TeamListResponse,
  UserPublicItem,
} from "./types";
import { apiFetch, readErrorBody } from "./api";

export async function fetchTeams(): Promise<TeamListResponse> {
  const res = await apiFetch("/teams", { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as TeamListResponse;
}

export async function fetchTeam(teamId: string): Promise<TeamDetail> {
  const res = await apiFetch(`/teams/${teamId}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as TeamDetail;
}

export async function createTeam(body: { name: string; description?: string }): Promise<TeamDetail> {
  const res = await apiFetch("/teams", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as TeamDetail;
}

export async function updateTeam(
  teamId: string,
  body: { name?: string; description?: string },
): Promise<TeamDetail> {
  const res = await apiFetch(`/teams/${teamId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as TeamDetail;
}

export async function disbandTeam(teamId: string): Promise<void> {
  const res = await apiFetch(`/teams/${teamId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function inviteToTeam(teamId: string, query: string): Promise<void> {
  const res = await apiFetch(`/teams/${teamId}/invite`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query }),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function kickMember(teamId: string, memberId: string): Promise<void> {
  const res = await apiFetch(`/teams/${teamId}/members/${memberId}/kick`, { method: "POST" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function leaveTeam(teamId: string): Promise<void> {
  const res = await apiFetch(`/teams/${teamId}/leave`, { method: "POST" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function uploadTeamAvatar(teamId: string, file: File): Promise<TeamDetail> {
  const fd = new FormData();
  fd.append("avatar", file);
  const res = await apiFetch(`/teams/${teamId}/avatar`, { method: "POST", body: fd });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as TeamDetail;
}

export async function deleteTeamAvatar(teamId: string): Promise<TeamDetail> {
  const res = await apiFetch(`/teams/${teamId}/avatar`, { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as TeamDetail;
}

export async function lookupUsers(q: string): Promise<UserPublicItem[]> {
  const res = await apiFetch(`/teams/users/lookup?q=${encodeURIComponent(q)}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as UserPublicItem[];
}

export async function fetchMail(unreadOnly = false): Promise<MailListResponse> {
  const qs = unreadOnly ? "?unread_only=true" : "";
  const res = await apiFetch(`/mail${qs}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as MailListResponse;
}

export async function fetchUnreadCount(): Promise<number> {
  const res = await apiFetch("/mail/unread-count", { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  const data = (await res.json()) as { unread_count: number };
  return data.unread_count;
}

export async function markMailRead(mailId: string): Promise<MailItem> {
  const res = await apiFetch(`/mail/${mailId}/read`, { method: "PATCH" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as MailItem;
}

export async function acceptMailInvite(mailId: string): Promise<void> {
  const res = await apiFetch(`/mail/${mailId}/accept`, { method: "POST" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function declineMailInvite(mailId: string): Promise<void> {
  const res = await apiFetch(`/mail/${mailId}/decline`, { method: "POST" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function deleteMail(mailId: string): Promise<void> {
  const res = await apiFetch(`/mail/${mailId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export function teamErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    team_not_found: "小队不存在",
    not_team_member: "你不是该小队成员",
    not_team_owner: "仅队长可操作",
    user_not_found: "未找到该用户，请检查 ID 或昵称",
    already_team_member: "该用户已在小队中",
    invite_already_sent: "已向该用户发送过邀请",
    team_full: "小队人数已满（最多 20 人）",
    member_not_found: "成员不存在",
    cannot_kick_self: "不能踢出自己",
    owner_cannot_leave: "队长需先解散小队或转让队长",
    "avatar too large": "头像文件过大（最大 2MB）",
    "invalid image": "无法识别图片格式",
    avatar_not_found: "尚未设置头像",
    mail_not_found: "邮件不存在",
    invite_already_handled: "邀请已处理",
    api_route_missing_restart: "服务端未加载最新接口，请重启 FastAPI",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}

export const MAIL_TYPE_LABELS: Record<string, string> = {
  team_invite: "小队邀请",
  team_kick: "移出通知",
  team_join: "新成员",
  team_disband: "小队解散",
  team_leave: "成员离开",
};
