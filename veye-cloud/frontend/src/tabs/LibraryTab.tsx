import { useEffect, useMemo, useState } from "react";
import { LayoutGrid } from "lucide-react";
import { LibraryCard } from "../components/LibraryCard";
import LibraryImagePanel from "../components/LibraryImagePanel";
import RiskBadge from "../components/RiskBadge";
import {
  DEFAULT_FILTERS,
  computeFacets,
  filterEntries,
  groupEntries,
  loadSavedFilters,
  saveFilters,
  sortEntries,
  type LibraryFilters,
  type SortMode,
  type ViewMode,
} from "../lib/libraryFilters";
import type { HistoryEntry, Scene } from "../lib/types";
import {
  SCENE_LABELS,
  formatAltitude,
  formatGpsCoords,
  formatPct,
  formatTs,
} from "../lib/types";

type Props = {
  items: HistoryEntry[];
  loading: boolean;
  err: string;
  onRefresh: () => void;
};

const SORT_OPTIONS: { id: SortMode; label: string }[] = [
  { id: "time_desc", label: "最新优先" },
  { id: "time_asc", label: "最早优先" },
  { id: "confidence_desc", label: "置信度高" },
  { id: "confidence_asc", label: "置信度低" },
  { id: "risk_desc", label: "风险高" },
  { id: "risk_asc", label: "风险低" },
  { id: "label_asc", label: "名称 A→Z" },
  { id: "label_desc", label: "名称 Z→A" },
];

const VIEW_OPTIONS: { id: ViewMode; label: string }[] = [
  { id: "grid", label: "网格" },
  { id: "list", label: "列表" },
  { id: "group_scene", label: "按场景" },
  { id: "group_risk", label: "按风险" },
  { id: "timeline", label: "时间线" },
];

export default function LibraryTab({ items, loading, err, onRefresh }: Props) {
  const [filters, setFilters] = useState<LibraryFilters>(() => ({
    ...DEFAULT_FILTERS,
    ...loadSavedFilters(),
  }));
  const [selected, setSelected] = useState<HistoryEntry | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);

  useEffect(() => {
    setSelected((prev) => {
      if (!prev) return null;
      return items.find((i) => i.request_id === prev.request_id) ?? prev;
    });
  }, [items]);

  useEffect(() => {
    saveFilters(filters);
  }, [filters]);

  const patch = (partial: Partial<LibraryFilters>) => {
    setFilters((f) => ({ ...f, ...partial }));
  };

  const filtered = useMemo(() => filterEntries(items, filters), [items, filters]);
  const sorted = useMemo(() => sortEntries(filtered, filters.sort), [filtered, filters.sort]);
  const groups = useMemo(
    () => groupEntries(sorted, filters.view, SCENE_LABELS as Record<string, string>),
    [sorted, filters.view],
  );
  const facets = useMemo(() => computeFacets(items, filtered), [items, filtered]);

  const hasActiveFilters = useMemo(() => {
    return (
      filters.q !== DEFAULT_FILTERS.q ||
      filters.scene !== DEFAULT_FILTERS.scene ||
      filters.risk !== DEFAULT_FILTERS.risk ||
      filters.source !== DEFAULT_FILTERS.source ||
      filters.sort !== DEFAULT_FILTERS.sort ||
      filters.view !== DEFAULT_FILTERS.view ||
      filters.minConfidence > DEFAULT_FILTERS.minConfidence
    );
  }, [filters]);

  const renderGrid = (entries: HistoryEntry[], compact = false) => (
    <div className={compact ? "library-list" : "library-grid"}>
      {entries.map((entry) => (
        <LibraryCard
          key={entry.request_id}
          entry={entry}
          selected={selected?.request_id === entry.request_id}
          onSelect={setSelected}
          compact={compact}
        />
      ))}
    </div>
  );

  return (
    <div className={`library-layout page-fill${selected ? " has-detail-open" : ""}`}>
      <div className="library-toolbar neo-card">
        <div className="library-toolbar-row">
          <div className="library-search-wrap">
            <input
              type="search"
              className="library-search"
              placeholder="搜索名称、摘要、场景…"
              value={filters.q}
              onChange={(e) => patch({ q: e.target.value })}
            />
            {filters.q ? (
              <button type="button" className="btn ghost library-clear" onClick={() => patch({ q: "" })}>
                清除
              </button>
            ) : null}
          </div>
          <button type="button" className="btn secondary" disabled={loading} onClick={onRefresh}>
            {loading ? "刷新中…" : "刷新"}
          </button>
        </div>

        <div
          id="library-filters-panel"
          className={`library-filters-panel${showAdvanced ? " open" : ""}`}
          aria-hidden={!showAdvanced}
        >
          <div className="library-filters-inner">
            <div className="library-chips" role="group" aria-label="场景筛选">
              <span className="chip-label">场景</span>
              {(["all", "toxic_plant", "medicine", "generic"] as const).map((s) => (
                <button
                  key={s}
                  type="button"
                  className={`chip-btn${filters.scene === s ? " active" : ""}`}
                  onClick={() => patch({ scene: s })}
                >
                  {s === "all" ? `全部 (${facets.total})` : SCENE_LABELS[s as Scene]}
                  {s !== "all" && facets.byScene[s] ? ` (${facets.byScene[s]})` : ""}
                </button>
              ))}
            </div>

            <div className="library-chips" role="group" aria-label="风险筛选">
              <span className="chip-label">风险</span>
              {(
                [
                  ["all", "全部"],
                  ["high", "较高≥2"],
                  ["3", "高"],
                  ["2", "中"],
                  ["1", "低"],
                  ["0", "安全"],
                ] as const
              ).map(([id, label]) => (
                <button
                  key={id}
                  type="button"
                  className={`chip-btn${filters.risk === id ? " active" : ""}`}
                  onClick={() => patch({ risk: id })}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="library-chips" role="group" aria-label="来源筛选">
              <span className="chip-label">来源</span>
              {(["all", "server", "local"] as const).map((s) => (
                <button
                  key={s}
                  type="button"
                  className={`chip-btn${filters.source === s ? " active" : ""}`}
                  onClick={() => patch({ source: s })}
                >
                  {s === "all" ? "全部" : s === "server" ? "云端" : "本地"}
                  {s !== "all" && facets.bySource[s] ? ` (${facets.bySource[s]})` : ""}
                </button>
              ))}
            </div>

            <div className="library-chips" role="group" aria-label="排序">
              <span className="chip-label">排序</span>
              {SORT_OPTIONS.map((o) => (
                <button
                  key={o.id}
                  type="button"
                  className={`chip-btn${filters.sort === o.id ? " active" : ""}`}
                  onClick={() => patch({ sort: o.id })}
                >
                  {o.label}
                </button>
              ))}
            </div>

            <div className="library-chips" role="group" aria-label="视图">
              <span className="chip-label">视图</span>
              {VIEW_OPTIONS.map((o) => (
                <button
                  key={o.id}
                  type="button"
                  className={`chip-btn${filters.view === o.id ? " active" : ""}`}
                  onClick={() => patch({ view: o.id })}
                >
                  {o.label}
                </button>
              ))}
            </div>

            <div className="library-advanced">
              <label className="library-confidence">
                最低置信度 {Math.round(filters.minConfidence * 100)}%
                <input
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={Math.round(filters.minConfidence * 100)}
                  onChange={(e) => patch({ minConfidence: Number(e.target.value) / 100 })}
                />
              </label>
            </div>
          </div>
        </div>

        <div className="library-toolbar-foot">
          <button
            type="button"
            className="btn ghost library-filter-toggle"
            aria-expanded={showAdvanced}
            aria-controls="library-filters-panel"
            onClick={() => setShowAdvanced((v) => !v)}
          >
            <span className="library-filter-toggle-icon" aria-hidden="true">
              {showAdvanced ? "▾" : "▸"}
            </span>
            {showAdvanced ? "收起筛选" : "高级筛选"}
            {!showAdvanced && hasActiveFilters ? (
              <span className="library-filter-badge">已筛选</span>
            ) : null}
          </button>
          <span className="muted">
            显示 {facets.matched} / {facets.total} 条
          </span>
          <button
            type="button"
            className="btn ghost"
            onClick={() => {
              setFilters(DEFAULT_FILTERS);
              setSelected(null);
            }}
          >
            重置全部
          </button>
        </div>
      </div>

      {err ? (
        <div className="alert neo-card" role="alert">
          {err}
        </div>
      ) : null}

      {!sorted.length && !loading ? (
        <div className="empty-state neo-card">
          <LayoutGrid className="empty-state-icon" size={40} strokeWidth={1.5} aria-hidden />
          <h3>{items.length ? "没有匹配的记录" : "暂无历史记录"}</h3>
          <p>{items.length ? "试试调整搜索词或筛选条件。" : "完成一次识别后，结果会自动出现在这里。"}</p>
        </div>
      ) : (
        <div className="library-results">
          {groups.map((group) => (
            <section key={group.key} className="library-group">
              {filters.view !== "grid" && filters.view !== "list" ? (
                <h3 className="library-group-title">{group.title}</h3>
              ) : null}
              {renderGrid(group.items, filters.view === "list")}
            </section>
          ))}
        </div>
      )}

      {selected ? (
        <div className="library-detail-layer">
          <button
            type="button"
            className="library-detail-backdrop"
            aria-label="关闭详情"
            onClick={() => setSelected(null)}
          />
          <aside className="detail-panel neo-card accent-panel library-detail">
            <div className="detail-head">
              <h3>{selected.label_main}</h3>
              <button type="button" className="btn ghost" onClick={() => setSelected(null)}>
                关闭
              </button>
            </div>
            <LibraryImagePanel
              entry={selected}
              onDeleted={() => {
                setSelected(null);
                onRefresh();
              }}
              onUpdated={onRefresh}
            />
            <p>{selected.summary || "无详细摘要。"}</p>
            <dl className="detail-dl">
              <div>
                <dt>场景</dt>
                <dd>{SCENE_LABELS[selected.scene as Scene] ?? selected.scene}</dd>
              </div>
              <div>
                <dt>置信度</dt>
                <dd>{formatPct(selected.confidence)}</dd>
              </div>
              <div>
                <dt>风险</dt>
                <dd>
                  <RiskBadge level={selected.risk_level} />
                </dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>{selected.source === "server" ? "云端" : "本地"}</dd>
              </div>
              <div>
                <dt>时间</dt>
                <dd>{formatTs(selected.server_ts)}</dd>
              </div>
              <div>
                <dt>位置</dt>
                <dd>
                  {formatGpsCoords(selected.gps_lat, selected.gps_lng, selected.accuracy_m) ?? (
                    <span className="muted">未记录 GPS</span>
                  )}
                </dd>
              </div>
              <div>
                <dt>海拔</dt>
                <dd>
                  {formatAltitude(selected.gps_altitude, selected.baro_altitude) ?? (
                    <span className="muted">未记录</span>
                  )}
                </dd>
              </div>
              <div>
                <dt>ID</dt>
                <dd className="mono">{selected.request_id}</dd>
              </div>
            </dl>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
