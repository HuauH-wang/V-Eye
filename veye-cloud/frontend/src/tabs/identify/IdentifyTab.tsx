import { Camera } from "lucide-react";
import RiskBadge from "../../components/RiskBadge";
import { useIdentify } from "./IdentifyContext";
import { formatPct, formatTs } from "../../lib/types";

function riskPanelClass(level: number, embedded: boolean): string {
  const idx = Math.max(0, Math.min(3, level));
  return `hero-result neo-card accent-panel${embedded ? "" : " page-fill-panel"} has-risk-${idx}`;
}

type Props = {
  embedded?: boolean;
};

export default function IdentifyTab({ embedded = false }: Props) {
  const { previewUrl, result, slowHint, err, topCandidate } = useIdentify();

  return (
    <div className={embedded ? "identify-layout settings-identify-layout" : "identify-layout page-fill"}>
      <div className={embedded ? "hero-row" : "hero-row page-fill-row"}>
        <div className={embedded ? "hero-media neo-card" : "hero-media neo-card page-fill-panel"}>
          {previewUrl ? (
            <img src={previewUrl} alt="待识别图片预览" className="hero-img" />
          ) : (
            <div className="hero-placeholder">
              <Camera className="hero-placeholder-icon" size={48} strokeWidth={1.5} />
              <p>{embedded ? "在上方选择图片并开始识别" : "在左侧选择图片并开始识别"}</p>
            </div>
          )}
        </div>

        <div className={result ? riskPanelClass(result.risk_level, embedded) : embedded ? "hero-result neo-card accent-panel" : "hero-result neo-card accent-panel page-fill-panel"}>
          {result ? (
            <>
              <div className="hero-meta">
                <span className="tag muted-tag">{formatTs(new Date().toISOString())}</span>
              </div>
              <h2 className="hero-title">{result.label_main || "未命名"}</h2>
              <div className="hero-stats">
                <span className="hero-stat-chip">置信度 {formatPct(result.confidence)}</span>
                <RiskBadge level={result.risk_level} large />
                <span className="hero-stat-chip">{result.latency_ms} ms</span>
              </div>
              <p className="hero-summary">{result.summary || "暂无摘要。"}</p>
              {result.advice ? <p className="hero-advice">{result.advice}</p> : null}
              {result.risk_tags.length ? (
                <div className="tag-row">
                  {result.risk_tags.map((t) => (
                    <span key={t} className="tag warn-tag">
                      {t}
                    </span>
                  ))}
                </div>
              ) : null}
            </>
          ) : (
            <div className="hero-empty">
              <h2>识别结果</h2>
              <p>上传图片并点击「开始识别」。</p>
              {slowHint ? <p className="slow-hint">仍在分析中，请稍候…</p> : null}
            </div>
          )}
        </div>
      </div>

      {err ? (
        <div className="alert neo-card" role="alert">
          {err}
        </div>
      ) : null}

      {result && result.candidates.length > 1 ? (
        <div className="candidate-grid">
          {result.candidates.slice(0, 4).map((c) => (
            <article key={c.label} className="mini-card neo-card">
              <h3>{c.label}</h3>
              <p>{formatPct(c.confidence)}</p>
              <div className="candidate-bar" aria-hidden>
                <div className="candidate-bar-fill" style={{ width: `${Math.round(c.confidence * 100)}%` }} />
              </div>
            </article>
          ))}
        </div>
      ) : null}

      {topCandidate && result && topCandidate.label !== result.label_main ? (
        <p className="muted note">Top 候选：{topCandidate.label}</p>
      ) : null}
    </div>
  );
}
