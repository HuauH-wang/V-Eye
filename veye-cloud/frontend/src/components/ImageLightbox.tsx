import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

type Props = {
  open: boolean;
  src: string;
  alt: string;
  caption?: string;
  onClose: () => void;
};

export default function ImageLightbox({ open, src, alt, caption, onClose }: Props) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (!open || !src) return null;

  return createPortal(
    <div
      className="image-lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={alt}
      onClick={() => onCloseRef.current()}
    >
      <div className="image-lightbox-inner" onClick={(e) => e.stopPropagation()}>
        <header className="image-lightbox-head">
          <span>{caption ?? alt}</span>
          <button type="button" className="btn ghost" onClick={() => onCloseRef.current()} aria-label="关闭">
            关闭
          </button>
        </header>
        <img src={src} alt={alt} className="image-lightbox-img" draggable={false} />
      </div>
    </div>,
    document.body,
  );
}
