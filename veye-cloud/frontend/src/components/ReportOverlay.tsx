import { useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

type Props = {
  open: boolean;
  title: string;
  subtitle?: string;
  onClose: () => void;
  headerActions?: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
  children: ReactNode;
};

export default function ReportOverlay({
  open,
  title,
  subtitle,
  onClose,
  headerActions,
  footer,
  wide = false,
  children,
}: Props) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div
      className="report-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={() => onCloseRef.current()}
    >
      <div
        className={`report-overlay-inner${wide ? " wide" : ""}`}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="report-overlay-head">
          <div className="report-overlay-title-wrap">
            <h2 className="report-overlay-title">{title}</h2>
            {subtitle ? <p className="report-overlay-subtitle muted small">{subtitle}</p> : null}
          </div>
          <div className="report-overlay-head-actions">
            {headerActions}
            <button type="button" className="btn ghost" onClick={() => onCloseRef.current()} aria-label="关闭">
              关闭
            </button>
          </div>
        </header>
        <div className="report-overlay-body">{children}</div>
        {footer ? <footer className="report-overlay-foot">{footer}</footer> : null}
      </div>
    </div>,
    document.body,
  );
}
