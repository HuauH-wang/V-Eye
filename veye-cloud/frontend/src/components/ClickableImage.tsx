import { useCallback, useEffect, useRef, useState, type MouseEvent } from "react";
import { ImageOff } from "lucide-react";
import ImageLightbox from "./ImageLightbox";

type Props = {
  src: string | null;
  alt: string;
  className?: string;
  caption?: string;
  loading?: boolean;
  failed?: boolean;
  onRetry?: () => void;
};

export default function ClickableImage({
  src,
  alt,
  className = "",
  caption,
  loading = false,
  failed = false,
  onRetry,
}: Props) {
  const [open, setOpen] = useState(false);
  /** 打开灯箱时快照 URL，避免父级 reload/revoke blob 导致闪烁 */
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  const closeLightbox = useCallback(() => {
    setOpen(false);
  }, []);

  useEffect(() => {
    if (!open) {
      setLightboxSrc(null);
    }
  }, [open]);

  const openLightbox = (e: MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!src || loading || failed) return;
    setLightboxSrc(src);
    setOpen(true);
  };

  if (loading && !open) {
    return <div className={`library-thumb-fallback ${className}`.trim()}>…</div>;
  }

  if ((failed || !src) && !open) {
    return (
      <div className={`library-thumb-fallback image-load-failed ${className}`.trim()}>
        <ImageOff size={22} strokeWidth={1.75} aria-hidden />
        <span className="image-fail-label">加载失败</span>
        {onRetry ? (
          <button type="button" className="btn ghost image-retry-btn" onClick={onRetry}>
            重试
          </button>
        ) : null}
      </div>
    );
  }

  return (
    <>
      <button
        type="button"
        className="clickable-image-btn"
        onClick={openLightbox}
        aria-label={`放大查看：${caption ?? alt}`}
      >
        {src ? (
          <img src={src} alt={alt} className={className || "record-img"} loading="lazy" draggable={false} />
        ) : (
          <div className={`library-thumb-fallback ${className}`.trim()}>…</div>
        )}
        <span className="zoom-hint">点击放大</span>
      </button>
      <ImageLightbox
        open={open}
        src={lightboxSrc ?? src ?? ""}
        alt={alt}
        caption={caption}
        onClose={closeLightbox}
      />
    </>
  );
}
