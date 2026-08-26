import type { HistoryEntry, Scene } from "./types";
import { riskLabel } from "./types";

export type SortMode =
  | "time_desc"
  | "time_asc"
  | "confidence_desc"
  | "confidence_asc"
  | "risk_desc"
  | "risk_asc"
  | "label_asc"
  | "label_desc";

export type ViewMode = "grid" | "list" | "group_scene" | "group_risk" | "timeline";

export type LibraryFilters = {
  q: string;
  scene: Scene | "all";
  risk: "all" | "0" | "1" | "2" | "3" | "high";
  source: "all" | "server" | "local";
  minConfidence: number;
  sort: SortMode;
  view: ViewMode;
};

export const DEFAULT_FILTERS: LibraryFilters = {
  q: "",
  scene: "all",
  risk: "all",
  source: "all",
  minConfidence: 0,
  sort: "time_desc",
  view: "grid",
};

export type LibraryFacets = {
  total: number;
  matched: number;
  byScene: Record<string, number>;
  byRisk: Record<number, number>;
  bySource: Record<string, number>;
};

export type LibraryGroup = {
  key: string;
  title: string;
  items: HistoryEntry[];
};

const LS_FILTERS = "veye_library_filters_v1";

export function loadSavedFilters(): Partial<LibraryFilters> {
  try {
    const raw = localStorage.getItem(LS_FILTERS);
    if (!raw) return {};
    return JSON.parse(raw) as Partial<LibraryFilters>;
  } catch {
    return {};
  }
}

export function saveFilters(filters: LibraryFilters): void {
  localStorage.setItem(LS_FILTERS, JSON.stringify(filters));
}

function matchesRisk(entry: HistoryEntry, risk: LibraryFilters["risk"]): boolean {
  if (risk === "all") return true;
  if (risk === "high") return entry.risk_level >= 2;
  return entry.risk_level === Number(risk);
}

function searchHaystack(entry: HistoryEntry): string {
  return [entry.label_main, entry.summary, entry.scene, entry.device_id ?? ""]
    .join(" ")
    .toLowerCase();
}

export function filterEntries(items: HistoryEntry[], f: LibraryFilters): HistoryEntry[] {
  const q = f.q.trim().toLowerCase();
  return items.filter((e) => {
    if (f.scene !== "all" && e.scene !== f.scene) return false;
    if (!matchesRisk(e, f.risk)) return false;
    if (f.source !== "all" && e.source !== f.source) return false;
    if (e.confidence < f.minConfidence) return false;
    if (q && !searchHaystack(e).includes(q)) return false;
    return true;
  });
}

export function sortEntries(items: HistoryEntry[], sort: SortMode): HistoryEntry[] {
  const arr = [...items];
  switch (sort) {
    case "time_asc":
      return arr.sort((a, b) => Date.parse(a.server_ts) - Date.parse(b.server_ts));
    case "confidence_desc":
      return arr.sort((a, b) => b.confidence - a.confidence);
    case "confidence_asc":
      return arr.sort((a, b) => a.confidence - b.confidence);
    case "risk_desc":
      return arr.sort((a, b) => b.risk_level - a.risk_level || Date.parse(b.server_ts) - Date.parse(a.server_ts));
    case "risk_asc":
      return arr.sort((a, b) => a.risk_level - b.risk_level || Date.parse(b.server_ts) - Date.parse(a.server_ts));
    case "label_asc":
      return arr.sort((a, b) => a.label_main.localeCompare(b.label_main, "zh-CN"));
    case "label_desc":
      return arr.sort((a, b) => b.label_main.localeCompare(a.label_main, "zh-CN"));
    default:
      return arr.sort((a, b) => Date.parse(b.server_ts) - Date.parse(a.server_ts));
  }
}

export function computeFacets(all: HistoryEntry[], matched: HistoryEntry[]): LibraryFacets {
  const byScene: Record<string, number> = {};
  const byRisk: Record<number, number> = { 0: 0, 1: 0, 2: 0, 3: 0 };
  const bySource: Record<string, number> = { server: 0, local: 0 };
  for (const e of all) {
    byScene[e.scene] = (byScene[e.scene] ?? 0) + 1;
    byRisk[e.risk_level] = (byRisk[e.risk_level] ?? 0) + 1;
    bySource[e.source] = (bySource[e.source] ?? 0) + 1;
  }
  return {
    total: all.length,
    matched: matched.length,
    byScene,
    byRisk,
    bySource,
  };
}

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

export function groupEntries(items: HistoryEntry[], view: ViewMode, sceneLabels: Record<string, string>): LibraryGroup[] {
  if (view === "grid" || view === "list") {
    return items.length ? [{ key: "all", title: "全部结果", items }] : [];
  }

  if (view === "group_scene") {
    const map = new Map<string, HistoryEntry[]>();
    for (const e of items) {
      const k = e.scene;
      if (!map.has(k)) map.set(k, []);
      map.get(k)!.push(e);
    }
    return [...map.entries()].map(([key, groupItems]) => ({
      key,
      title: sceneLabels[key] ?? key,
      items: groupItems,
    }));
  }

  if (view === "group_risk") {
    const order = [3, 2, 1, 0];
    const map = new Map<number, HistoryEntry[]>();
    for (const e of items) {
      if (!map.has(e.risk_level)) map.set(e.risk_level, []);
      map.get(e.risk_level)!.push(e);
    }
    return order
      .filter((lvl) => map.has(lvl))
      .map((lvl) => ({
        key: String(lvl),
        title: riskLabel(lvl),
        items: map.get(lvl)!,
      }));
  }

  // timeline
  const now = Date.now();
  const today = startOfDay(new Date(now));
  const yesterday = today - 86400000;
  const weekAgo = today - 7 * 86400000;

  const buckets: { key: string; title: string; items: HistoryEntry[] }[] = [
    { key: "today", title: "今天", items: [] },
    { key: "yesterday", title: "昨天", items: [] },
    { key: "week", title: "本周早些时候", items: [] },
    { key: "older", title: "更早", items: [] },
  ];

  for (const e of items) {
    const t = Date.parse(e.server_ts);
    const day = startOfDay(new Date(t));
    if (day >= today) buckets[0].items.push(e);
    else if (day >= yesterday) buckets[1].items.push(e);
    else if (day >= weekAgo) buckets[2].items.push(e);
    else buckets[3].items.push(e);
  }

  return buckets.filter((b) => b.items.length > 0);
}

export type FetchRecordsParams = {
  limit?: number;
  offset?: number;
  q?: string;
  scene?: string;
  riskLevel?: number;
  sort?: SortMode;
};

export function buildRecordsQuery(params: FetchRecordsParams): string {
  const sp = new URLSearchParams();
  sp.set("limit", String(params.limit ?? 100));
  sp.set("offset", String(params.offset ?? 0));
  if (params.q?.trim()) sp.set("q", params.q.trim());
  if (params.scene && params.scene !== "all") sp.set("scene", params.scene);
  if (params.riskLevel !== undefined) sp.set("risk_level", String(params.riskLevel));
  if (params.sort) sp.set("sort", params.sort);
  return sp.toString();
}
