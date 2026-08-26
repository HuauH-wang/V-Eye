import { FileText, Map } from "lucide-react";
import { useAuthenticatedImage } from "../hooks/useAuthenticatedImage";
import { REPORT_TYPE_LABELS } from "../lib/reports";
import type { ReportJobItem } from "../lib/types";
import { formatTs } from "../lib/types";

type Props = {
  job: ReportJobItem;
  onView: () => void;
  onEdit: () => void;
  onDelete: () => void;
};

function firstCoverImageId(job: ReportJobItem): string | null {
  const images = job.timeline_json?.images;
  if (Array.isArray(images) && images.length > 0) {
    const id = String((images[0] as { record_id?: string }).record_id ?? "").trim();
    if (id) return id;
  }
  const events = job.timeline_json?.events;
  if (Array.isArray(events)) {
    for (const ev of events) {
      const row = ev as { type?: string; has_image?: boolean; record_id?: string };
      if (row.type === "identify" && row.has_image && row.record_id) {
        return String(row.record_id);
      }
    }
  }
  return null;
}

function coverExcerpt(job: ReportJobItem): string {
  if (job.markdown) {
    const line = job.markdown
      .split("\n")
      .map((l) => l.replace(/^#+\s*/, "").trim())
      .find((l) => l.length > 0);
    if (line) return line.length > 72 ? `${line.slice(0, 71)}…` : line;
  }
  const stats = (job.timeline_json?.stats ?? {}) as Record<string, unknown>;
  const identify = Number(stats.identify_count ?? 0);
  const sos = Number(stats.sos_count ?? 0);
  if (identify || sos) return `识图 ${identify} · SOS ${sos}`;
  if (job.status === "failed") return "生成失败，点击查看详情";
  return "报告生成中…";
}

function statusLabel(job: ReportJobItem): string {
  if (job.status === "done") return job.is_edited ? "已编辑" : "已完成";
  if (job.status === "failed") return "失败";
  if (job.status === "cancelled") return "已取消";
  return "生成中";
}

function CoverThumb({ requestId }: { requestId: string }) {
  const { url, failed, loading } = useAuthenticatedImage(`/vision/records/${requestId}/image`);
  if (loading) return <div className="report-cover-thumb-placeholder" />;
  if (failed || !url) return null;
  return <img src={url} alt="" className="report-cover-thumb-img" draggable={false} />;
}

export default function ReportCoverCard({ job, onView, onEdit, onDelete }: Props) {
  const type = job.report_type ?? "safety";
  const canEdit = job.status === "done" && Boolean(job.markdown);
  const canView = !["aggregating", "identifying", "writing"].includes(job.status);
  const canDelete = !["aggregating", "identifying", "writing"].includes(job.status);
  const thumbId = firstCoverImageId(job);

  return (
    <article className={`report-cover-card type-${type} status-${job.status}`}>
      <div className={`report-cover-visual${thumbId ? " has-thumb" : ""}`}>
        {thumbId ? (
          <div className="report-cover-thumb" aria-hidden>
            <CoverThumb requestId={thumbId} />
          </div>
        ) : (
          <div className="report-cover-icon" aria-hidden>
            {type === "travel" ? <Map size={28} strokeWidth={1.75} /> : <FileText size={28} strokeWidth={1.75} />}
          </div>
        )}
        <div className="report-cover-text">
          <span className="report-cover-type">{REPORT_TYPE_LABELS[type]}</span>
          <strong className="report-cover-date">{job.report_date}</strong>
          <span className="report-cover-member">{job.subject_display_name}</span>
        </div>
      </div>
      <div className="report-cover-body">
        <p className="report-cover-excerpt">{coverExcerpt(job)}</p>
        <div className="report-cover-meta muted small">
          <span className={`report-cover-status status-${job.status}`}>{statusLabel(job)}</span>
          <span>{formatTs(job.finished_at ?? job.updated_at)}</span>
        </div>
        <div className="report-cover-actions">
          <button type="button" className="btn secondary" onClick={onView} disabled={!canView}>
            查看
          </button>
          <button type="button" className="btn ghost" onClick={onEdit} disabled={!canEdit}>
            编辑
          </button>
          <button type="button" className="btn danger ghost" onClick={onDelete} disabled={!canDelete}>
            删除
          </button>
        </div>
      </div>
    </article>
  );
}
