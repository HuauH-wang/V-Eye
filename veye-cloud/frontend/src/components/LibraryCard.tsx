import { MapPin } from "lucide-react";
import RecordImage from "../components/RecordImage";
import RiskBadge from "../components/RiskBadge";
import { sanitizePreviewDataUrl } from "../lib/previewUrl";
import type { HistoryEntry } from "../lib/types";
import { SCENE_LABELS, formatPct, formatTs, recordLocationLabel } from "../lib/types";

type CardProps = {
  entry: HistoryEntry;
  selected: boolean;
  onSelect: (e: HistoryEntry) => void;
  compact?: boolean;
};

function imgKind(entry: HistoryEntry): "local" | "server" | "missing" | undefined {
  if (entry.source === "server") {
    return entry.has_image === false ? "missing" : "server";
  }
  if (sanitizePreviewDataUrl(entry.previewDataUrl)) return "local";
  return undefined;
}

export function LibraryCard({ entry, selected, onSelect, compact }: CardProps) {
  const kind = imgKind(entry);
  const location = recordLocationLabel(entry);
  if (compact) {
    return (
      <article
        className={`library-list-row neo-card${selected ? " selected" : ""}`}
        onClick={() => onSelect(entry)}
        onKeyDown={(e) => e.key === "Enter" && onSelect(entry)}
        role="button"
        tabIndex={0}
      >
        <div className="library-list-thumb">
          {kind === "local" && entry.previewDataUrl ? (
            <img src={entry.previewDataUrl} alt="" loading="lazy" />
          ) : kind === "server" ? (
            <RecordImage requestId={entry.request_id} alt={entry.label_main} />
          ) : kind === "missing" ? (
            <span title="云端图片文件缺失">{entry.label_main.slice(0, 1) || "?"}</span>
          ) : (
            <span>{entry.label_main.slice(0, 1)}</span>
          )}
        </div>
        <div className="library-list-body">
          <strong>{entry.label_main || "未命名"}</strong>
          <span className="muted">{entry.summary.slice(0, 60) || "无摘要"}</span>
          {location ? (
            <span className="library-location muted">
              <MapPin size={12} aria-hidden />
              {location}
            </span>
          ) : null}
        </div>
        <div className="library-list-meta">
          <RiskBadge level={entry.risk_level} />
          <span>{formatPct(entry.confidence)}</span>
          <time className="muted">{formatTs(entry.server_ts)}</time>
        </div>
      </article>
    );
  }

  return (
    <article
      className={`library-card neo-card${selected ? " selected" : ""}`}
      onClick={() => onSelect(entry)}
      onKeyDown={(e) => e.key === "Enter" && onSelect(entry)}
      role="button"
      tabIndex={0}
    >
      <div className="library-thumb">
        {kind === "local" && entry.previewDataUrl ? (
          <img src={entry.previewDataUrl} alt={entry.label_main} loading="lazy" />
        ) : kind === "server" ? (
          <RecordImage requestId={entry.request_id} alt={entry.label_main} />
        ) : kind === "missing" ? (
          <div className="library-thumb-fallback" title="云端图片文件缺失">
            {entry.label_main.slice(0, 1) || "?"}
          </div>
        ) : (
          <div className="library-thumb-fallback">{entry.label_main.slice(0, 1)}</div>
        )}
      </div>
      <div className="library-body">
        <div className="library-meta">
          <span className="tag">{SCENE_LABELS[entry.scene as keyof typeof SCENE_LABELS] ?? entry.scene}</span>
          <span className="tag muted-tag">{entry.source === "server" ? "云端" : "本地"}</span>
        </div>
        <h3>{entry.label_main || "未命名"}</h3>
        <div className="library-stats">
          <span>{formatPct(entry.confidence)}</span>
          <RiskBadge level={entry.risk_level} />
        </div>
        <p className="library-summary">{entry.summary || "无摘要"}</p>
        {location ? (
          <p className="library-location muted">
            <MapPin size={13} aria-hidden />
            {location}
          </p>
        ) : null}
        <time className="muted">{formatTs(entry.server_ts)}</time>
      </div>
    </article>
  );
}
