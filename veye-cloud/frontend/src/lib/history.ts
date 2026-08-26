import type { HistoryEntry, IdentifyRecordItem, IdentifyResult, Scene } from "./types";
import { sanitizePreviewDataUrl } from "./previewUrl";

const LS_HISTORY = "veye_web_history_v1";
const MAX_LOCAL = 50;

function normalizeLocalEntry(entry: HistoryEntry): HistoryEntry {
  return {
    ...entry,
    previewDataUrl: sanitizePreviewDataUrl(entry.previewDataUrl),
  };
}

export function loadLocalHistory(): HistoryEntry[] {
  try {
    const raw = localStorage.getItem(LS_HISTORY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as HistoryEntry[];
    if (!Array.isArray(parsed)) return [];
    const normalized = parsed.map(normalizeLocalEntry);
    const changed = normalized.some(
      (entry, i) => entry.previewDataUrl !== parsed[i]?.previewDataUrl,
    );
    if (changed) {
      saveLocalHistory(normalized);
    }
    return normalized;
  } catch {
    return [];
  }
}

function saveLocalHistory(entries: HistoryEntry[]): void {
  localStorage.setItem(LS_HISTORY, JSON.stringify(entries.slice(0, MAX_LOCAL)));
}

export function addLocalHistory(
  result: IdentifyResult,
  scene: Scene,
  previewDataUrl?: string,
): void {
  const entry: HistoryEntry = {
    request_id: result.request_id,
    device_id: null,
    scene,
    label_main: result.label_main,
    confidence: result.confidence,
    risk_level: result.risk_level,
    summary: result.summary,
    server_ts: new Date().toISOString(),
    previewDataUrl: sanitizePreviewDataUrl(previewDataUrl),
    source: "local",
  };
  const existing = loadLocalHistory().filter((e) => e.request_id !== entry.request_id);
  saveLocalHistory([entry, ...existing]);
}

export function removeLocalHistory(requestId: string): void {
  const next = loadLocalHistory().filter((e) => e.request_id !== requestId);
  saveLocalHistory(next);
}

export function mergeHistory(serverItems: IdentifyRecordItem[], localItems: HistoryEntry[]): HistoryEntry[] {
  const map = new Map<string, HistoryEntry>();

  for (const item of serverItems) {
    map.set(item.request_id, { ...item, source: "server" });
  }
  for (const item of localItems) {
    if (map.has(item.request_id)) {
      continue;
    }
    map.set(item.request_id, {
      ...item,
      previewDataUrl: sanitizePreviewDataUrl(item.previewDataUrl),
      source: "local",
    });
  }

  return [...map.values()].sort(
    (a, b) => new Date(b.server_ts).getTime() - new Date(a.server_ts).getTime(),
  );
}
