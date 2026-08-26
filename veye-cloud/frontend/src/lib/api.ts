import type { HealthInfo, IdentifyRecordsResponse, IdentifyResult, UserProfile } from "./types";
import { loadToken } from "./auth";
import type { FetchRecordsParams } from "./libraryFilters";
import { buildRecordsQuery } from "./libraryFilters";

const LS_API_BASE = "veye_web_api_base";
const LS_API_KEY = "veye_web_api_key";

export function loadApiBase(): string {
  return (localStorage.getItem(LS_API_BASE) ?? "").trim();
}

export function loadApiKey(): string {
  return (localStorage.getItem(LS_API_KEY) ?? "").trim();
}

export function saveSettings(apiBase: string, apiKey: string): void {
  const b = apiBase.trim();
  const k = apiKey.trim();
  if (b) localStorage.setItem(LS_API_BASE, b);
  else localStorage.removeItem(LS_API_BASE);
  if (k) localStorage.setItem(LS_API_KEY, k);
  else localStorage.removeItem(LS_API_KEY);
}

/** 相对路径以 / 开头，例如 /health */
export function buildUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  const base = loadApiBase();
  if (base) {
    return `${base.replace(/\/$/, "")}${p}`;
  }
  // 开发：Vite 代理 /api → FastAPI；生产构建挂到 FastAPI 同源时直接用根路径
  if (import.meta.env.DEV) {
    return `/api${p}`;
  }
  return p;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const url = buildUrl(path);
  const headers = new Headers(init.headers ?? undefined);
  const key = loadApiKey();
  if (key) headers.set("X-API-Key", key);
  const token = loadToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  return fetch(url, { ...init, headers });
}

export function parseErrorText(text: string, fallback = ""): string {
  try {
    const j = JSON.parse(text) as { detail?: unknown };
    if (j && typeof j.detail === "string") return j.detail;
    if (j && j.detail !== undefined) return JSON.stringify(j.detail);
  } catch {
    /* ignore */
  }
  return text || fallback;
}

export async function readErrorBody(res: Response): Promise<string> {
  if (res.status === 405) {
    return "api_route_missing_restart";
  }
  return parseErrorText(await res.text(), res.statusText);
}

export async function fetchHealth(): Promise<HealthInfo> {
  const res = await apiFetch("/health", { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as HealthInfo;
}

export async function fetchRecords(params: FetchRecordsParams = {}): Promise<IdentifyRecordsResponse> {
  const qs = buildRecordsQuery({ limit: 100, offset: 0, ...params });
  const res = await apiFetch(`/vision/records?${qs}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as IdentifyRecordsResponse;
}

export function recordImageUrl(requestId: string): string {
  return buildUrl(`/vision/records/${requestId}/image`);
}

export function recordOriginalImageUrl(requestId: string): string {
  return buildUrl(`/vision/records/${requestId}/image/original`);
}

export type EnhanceMode = "denoise" | "enhance" | "both";
export type EnhanceStrength = "light" | "normal" | "strong";

export async function deleteRecord(requestId: string): Promise<void> {
  const res = await apiFetch(`/vision/records/${requestId}`, { method: "DELETE" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export async function previewRecordEnhance(
  requestId: string,
  body: { mode: EnhanceMode; strength: EnhanceStrength },
): Promise<{ preview_data_url: string; mode: EnhanceMode; strength: EnhanceStrength }> {
  const res = await apiFetch(`/vision/records/${requestId}/enhance/preview`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as { preview_data_url: string; mode: EnhanceMode; strength: EnhanceStrength };
}

export async function applyRecordEnhance(
  requestId: string,
  body: { mode: EnhanceMode; strength: EnhanceStrength },
): Promise<{ has_original: boolean; mode: EnhanceMode; strength: EnhanceStrength }> {
  const res = await apiFetch(`/vision/records/${requestId}/enhance/apply`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as { has_original: boolean; mode: EnhanceMode; strength: EnhanceStrength };
}

export async function restoreRecordImage(requestId: string): Promise<void> {
  const res = await apiFetch(`/vision/records/${requestId}/enhance/restore`, { method: "POST" });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export function libraryErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    record_not_found: "记录不存在",
    forbidden: "无权操作此记录",
    image_not_found: "图片文件不存在",
    no_original_backup: "没有可恢复的原图",
    enhance_failed: "图像处理失败",
    not_authenticated: "请先登录",
    database_unavailable: "数据库暂时不可用，请稍后重试",
    api_route_missing_restart: "服务端未加载最新接口，请重启 FastAPI",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}

export async function identifyImage(
  fd: FormData,
  signal?: AbortSignal,
): Promise<IdentifyResult> {
  const res = await apiFetch("/vision/identify", { method: "POST", body: fd, signal });
  const text = await res.text();
  if (!res.ok) throw new Error(parseErrorText(text, res.statusText));
  return JSON.parse(text) as IdentifyResult;
}

export async function postSos(body: Record<string, unknown>): Promise<unknown> {
  const res = await apiFetch("/sos", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(parseErrorText(text, res.statusText));
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
