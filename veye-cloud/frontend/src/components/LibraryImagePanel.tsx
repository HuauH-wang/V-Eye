import { useEffect, useState } from "react";
import ClickableImage from "./ClickableImage";
import {
  applyRecordEnhance,
  deleteRecord,
  previewRecordEnhance,
  restoreRecordImage,
  libraryErrorMessage,
  type EnhanceMode,
  type EnhanceStrength,
} from "../lib/api";
import { useAuthenticatedImage } from "../hooks/useAuthenticatedImage";
import { sanitizePreviewDataUrl } from "../lib/previewUrl";
import { removeLocalHistory } from "../lib/history";
import type { HistoryEntry } from "../lib/types";
import RecordImage from "./RecordImage";

type Props = {
  entry: HistoryEntry;
  onDeleted: () => void;
  onUpdated: () => void;
};

const MODE_LABELS: Record<EnhanceMode, string> = {
  denoise: "去噪",
  enhance: "增强",
  both: "去噪+增强",
};

export default function LibraryImagePanel({ entry, onDeleted, onUpdated }: Props) {
  const isServer = entry.source === "server";
  const [imageKey, setImageKey] = useState(0);
  const [strength, setStrength] = useState<EnhanceStrength>("normal");
  const [previewMode, setPreviewMode] = useState<EnhanceMode | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [compare, setCompare] = useState<"current" | "split">("current");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  const hasOriginal = Boolean(entry.has_original);
  const originalPath = hasOriginal ? `/vision/records/${entry.request_id}/image/original` : "";
  const {
    url: originalUrl,
    failed: originalFailed,
    loading: originalLoading,
    retry: retryOriginal,
  } = useAuthenticatedImage(originalPath, `${imageKey}-${entry.has_original ? "1" : "0"}`);

  useEffect(() => {
    setPreviewMode(null);
    setPreviewUrl(null);
    setCompare("current");
    setErr("");
  }, [entry.request_id, entry.has_original]);

  const runPreview = async (mode: EnhanceMode) => {
    if (!isServer) return;
    setBusy(true);
    setErr("");
    try {
      const data = await previewRecordEnhance(entry.request_id, { mode, strength });
      setPreviewMode(mode);
      setPreviewUrl(data.preview_data_url);
      setCompare("split");
    } catch (e) {
      setErr(libraryErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const runApply = async () => {
    if (!isServer || !previewMode) return;
    setBusy(true);
    setErr("");
    try {
      await applyRecordEnhance(entry.request_id, { mode: previewMode, strength });
      setPreviewUrl(null);
      setPreviewMode(null);
      setCompare("current");
      setImageKey((k) => k + 1);
      onUpdated();
    } catch (e) {
      setErr(libraryErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const runRestore = async () => {
    if (!isServer) return;
    setBusy(true);
    setErr("");
    try {
      await restoreRecordImage(entry.request_id);
      setPreviewUrl(null);
      setPreviewMode(null);
      setCompare("current");
      setImageKey((k) => k + 1);
      onUpdated();
    } catch (e) {
      setErr(libraryErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const runDelete = async () => {
    const label = entry.label_main || "该记录";
    if (!window.confirm(`确定删除「${label}」？${isServer ? "云端图片与记录将永久删除。" : "本地缓存将被清除。"}`)) {
      return;
    }
    setBusy(true);
    setErr("");
    try {
      if (isServer) {
        await deleteRecord(entry.request_id);
      }
      removeLocalHistory(entry.request_id);
      onDeleted();
    } catch (e) {
      setErr(libraryErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const renderCurrentImage = (caption: string) => {
    const localPreview = !isServer ? sanitizePreviewDataUrl(entry.previewDataUrl) : undefined;
    if (localPreview) {
      return (
        <ClickableImage
          src={localPreview}
          alt={entry.label_main}
          className="detail-img"
          caption={caption}
        />
      );
    }
    if (isServer && entry.has_image === false) {
      return (
        <div className="library-thumb-fallback detail-img image-load-failed">
          <span>?</span>
          <p className="muted">云端图片文件缺失</p>
        </div>
      );
    }
    return (
      <RecordImage
        requestId={entry.request_id}
        alt={entry.label_main}
        className="detail-img"
        refreshKey={imageKey}
        caption={caption}
      />
    );
  };

  const renderOriginalImage = () => {
    if (hasOriginal) {
      return (
        <ClickableImage
          src={originalUrl}
          alt={`${entry.label_main} 原图`}
          className="detail-img"
          caption="原图"
          loading={originalLoading}
          failed={originalFailed}
          onRetry={retryOriginal}
        />
      );
    }
    const localPreview = !isServer ? sanitizePreviewDataUrl(entry.previewDataUrl) : undefined;
    if (localPreview) {
      return (
        <ClickableImage
          src={localPreview}
          alt={entry.label_main}
          className="detail-img"
          caption="原图"
        />
      );
    }
    return renderCurrentImage("原图 / 当前");
  };

  return (
    <div className="library-image-panel">
      <div className={`library-preview${compare === "split" ? " split" : ""}`}>
        {compare === "split" && (previewUrl || hasOriginal) ? (
          <>
            <figure className="library-preview-pane">
              <figcaption>{previewUrl ? "原图 / 当前" : "原图"}</figcaption>
              {renderOriginalImage()}
            </figure>
            <figure className="library-preview-pane">
              <figcaption>{previewUrl ? `${MODE_LABELS[previewMode!]} 预览` : "处理后"}</figcaption>
              {previewUrl ? (
                <ClickableImage
                  src={previewUrl}
                  alt="处理预览"
                  className="detail-img"
                  caption={previewMode ? `${MODE_LABELS[previewMode]} 预览` : "处理后预览"}
                />
              ) : (
                renderCurrentImage("处理后")
              )}
            </figure>
          </>
        ) : (
          <figure className="library-preview-single">
            {previewUrl ? (
              <ClickableImage
                src={previewUrl}
                alt="处理预览"
                className="detail-img"
                caption={previewMode ? `${MODE_LABELS[previewMode]} 预览` : "预览"}
              />
            ) : (
              renderCurrentImage("当前图片")
            )}
          </figure>
        )}
      </div>

      {entry.last_enhance_mode ? (
        <p className="muted library-enhance-tag">
          已应用云端处理：{MODE_LABELS[entry.last_enhance_mode as EnhanceMode] ?? entry.last_enhance_mode}
        </p>
      ) : null}

      {isServer ? (
        <div className="library-enhance-tools">
          <div className="library-chips" role="group" aria-label="处理强度">
            <span className="chip-label">强度</span>
            {(["light", "normal", "strong"] as const).map((s) => (
              <button
                key={s}
                type="button"
                className={`chip-btn${strength === s ? " active" : ""}`}
                disabled={busy}
                onClick={() => setStrength(s)}
              >
                {s === "light" ? "轻度" : s === "normal" ? "标准" : "强力"}
              </button>
            ))}
          </div>

          <div className="library-enhance-actions">
            {(Object.keys(MODE_LABELS) as EnhanceMode[]).map((mode) => (
              <button
                key={mode}
                type="button"
                className={`btn secondary${previewMode === mode ? " active-mode" : ""}`}
                disabled={busy}
                onClick={() => runPreview(mode)}
              >
                预览{MODE_LABELS[mode]}
              </button>
            ))}
            {hasOriginal ? (
              <button
                type="button"
                className="btn ghost"
                disabled={busy}
                onClick={() => setCompare(compare === "split" ? "current" : "split")}
              >
                {compare === "split" ? "单图视图" : "对比原图"}
              </button>
            ) : null}
          </div>

          {previewUrl ? (
            <div className="library-enhance-apply">
              <button type="button" className="btn primary" disabled={busy} onClick={runApply}>
                应用并保存到云端
              </button>
              <button
                type="button"
                className="btn ghost"
                disabled={busy}
                onClick={() => {
                  setPreviewUrl(null);
                  setPreviewMode(null);
                  setCompare(hasOriginal ? "split" : "current");
                }}
              >
                取消预览
              </button>
            </div>
          ) : null}

          {hasOriginal ? (
            <button type="button" className="btn ghost" disabled={busy} onClick={runRestore}>
              恢复原图
            </button>
          ) : null}
        </div>
      ) : (
        <p className="muted library-local-hint">本地记录仅保存在浏览器，登录后识别会自动同步云端后可使用去噪增强。</p>
      )}

      {err ? (
        <div className="alert inline" role="alert">
          {err}
        </div>
      ) : null}

      <div className="library-danger-zone">
        <button type="button" className="btn danger" disabled={busy} onClick={runDelete}>
          删除{isServer ? "云端记录" : "本地记录"}
        </button>
      </div>
    </div>
  );
}
