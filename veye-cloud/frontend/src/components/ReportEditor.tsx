import { useCallback, useEffect, useState } from "react";
import ReportMarkdownViewer from "./ReportMarkdownViewer";
import { updateReportJob } from "../lib/reports";
import type { ReportJobItem } from "../lib/types";

type Props = {
  job: ReportJobItem;
  onSaved: (job: ReportJobItem) => void;
  onActivity: (msg: string) => void;
  onError: (msg: string) => void;
};

export default function ReportEditor({ job, onSaved, onActivity, onError }: Props) {
  const [draft, setDraft] = useState(job.markdown ?? "");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDraft(job.markdown ?? "");
  }, [job.id, job.markdown]);

  const dirty = draft.trim() !== (job.markdown ?? "").trim();

  const handleSave = useCallback(async () => {
    if (!draft.trim()) {
      onError("报告内容不能为空");
      return;
    }
    setSaving(true);
    try {
      const updated = await updateReportJob(job.id, draft);
      onSaved(updated);
      onActivity("报告已保存");
    } catch (e) {
      onError(String(e));
    } finally {
      setSaving(false);
    }
  }, [draft, job.id, onActivity, onError, onSaved]);

  const handleReset = () => {
    if (job.markdown_generated) {
      setDraft(job.markdown_generated);
    }
  };

  return (
    <div className="report-editor-modal">
      <div className="report-editor-split">
        <div className="report-editor-pane">
          <label className="report-editor-label" htmlFor={`report-md-${job.id}`}>
            Markdown
          </label>
          <textarea
            id={`report-md-${job.id}`}
            className="report-editor-textarea"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            spellCheck={false}
          />
          <div className="report-editor-meta muted small">
            配图：<code>![说明](veye://image/识图记录ID)</code>
          </div>
        </div>
        <div className="report-preview-pane">
          <div className="report-editor-label">实时预览</div>
          <div className="report-preview-scroll">
            <ReportMarkdownViewer markdown={draft} />
          </div>
        </div>
      </div>
      <div className="report-editor-actions">
        {job.markdown_generated ? (
          <button type="button" className="btn ghost" onClick={handleReset} disabled={saving}>
            恢复 AI 原文
          </button>
        ) : null}
        <button
          type="button"
          className="btn primary"
          onClick={() => void handleSave()}
          disabled={saving || !dirty}
        >
          {saving ? "保存中…" : dirty ? "保存修改" : "已保存"}
        </button>
      </div>
    </div>
  );
}
