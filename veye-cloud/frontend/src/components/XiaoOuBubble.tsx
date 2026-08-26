import type { CompanionState } from "../lib/motion";

type Props = {
  companion: CompanionState;
  compact?: boolean;
};

/** 小欧浮泡：无立绘，仅保留涟漪气泡与状态文案 */
export default function XiaoOuBubble({ companion, compact = false }: Props) {
  return (
    <div className={`xiaoou-bubble-panel${compact ? " xiaoou-bubble-panel--compact" : ""}`}>
      <div
        className={`xiaoou-bubble-only${compact ? " xiaoou-bubble-only--compact" : ""}`}
        aria-hidden
      >
        <div className="xiaoou-ripple">
          <span className="xiaoou-ripple-ring xiaoou-ripple-ring-1" />
          <span className="xiaoou-ripple-ring xiaoou-ripple-ring-2" />
          <span className="xiaoou-ripple-ring xiaoou-ripple-ring-3" />
        </div>
        <span className="xiaoou-bubble-glyph">欧</span>
      </div>
      <div className="xiaoou-bubble-copy">
        <p className="xiaoou-bubble-name">{companion.name}</p>
        <p className="xiaoou-bubble-meta">
          {companion.mood} · Lv.{companion.level} · 能量 {companion.energy}%
        </p>
        <p className="xiaoou-bubble-msg">{companion.message}</p>
        <p className="muted xiaoou-bubble-steps">累计步数 {companion.total_steps}</p>
      </div>
    </div>
  );
}
