import type { UserProfile } from "./types";
import { apiFetch, readErrorBody } from "./api";

const LS_TOKEN = "veye_web_access_token";
const LS_USER = "veye_web_user";

export function loadToken(): string {
  return (localStorage.getItem(LS_TOKEN) ?? "").trim();
}

export function loadCachedUser(): UserProfile | null {
  try {
    const raw = localStorage.getItem(LS_USER);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as UserProfile;
    return { ...parsed, has_avatar: parsed.has_avatar ?? false };
  } catch {
    return null;
  }
}

export function saveAuth(token: string, user: UserProfile): void {
  localStorage.setItem(LS_TOKEN, token);
  localStorage.setItem(LS_USER, JSON.stringify(user));
}

export function clearAuth(): void {
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_USER);
}

export type AuthResult = {
  access_token: string;
  token_type: string;
  user: UserProfile;
};

export async function registerUser(body: {
  username: string;
  password: string;
  display_name?: string;
  email?: string;
}): Promise<AuthResult> {
  const res = await apiFetch("/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as AuthResult;
}

export async function loginUser(body: {
  username: string;
  password: string;
}): Promise<AuthResult> {
  const res = await apiFetch("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as AuthResult;
}

export async function fetchMe(): Promise<UserProfile> {
  const res = await apiFetch("/auth/me", { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as UserProfile;
}

export async function updateProfile(body: { display_name: string }): Promise<UserProfile> {
  const res = await apiFetch("/auth/me", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as UserProfile;
}

export async function uploadAvatar(file: File): Promise<UserProfile> {
  const fd = new FormData();
  fd.append("avatar", file);
  const res = await apiFetch("/auth/me/avatar", { method: "POST", body: fd });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as UserProfile;
}

export async function deleteAvatar(): Promise<UserProfile> {
  const res = await apiFetch("/auth/me/avatar", { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as UserProfile;
}

export function authErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    invalid_credentials: "用户名或密码错误",
    username_or_email_taken: "用户名已被占用",
    not_authenticated: "请先登录",
    token_expired: "登录已过期，请重新登录",
    invalid_token: "登录无效，请重新登录",
    "password must be at least 6 chars": "密码至少 6 位",
    "username must be 3-32 chars": "用户名须为 3–32 位字母、数字或下划线",
    "avatar too large": "头像文件过大（最大 2MB）",
    "invalid image": "图片格式无效，请换一张",
    api_route_missing_restart: "服务端未加载最新接口，请重启 FastAPI：./scripts/start_api_venv.sh",
    api_route_not_found_restart_server: "服务端未加载最新接口，请重启 FastAPI：./scripts/start_api_venv.sh",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}
