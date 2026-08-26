import { riskLabel } from "../lib/types";

type Props = { level: number; large?: boolean };

const RISK_CLASS = ["risk-0", "risk-1", "risk-2", "risk-3"] as const;

export default function RiskBadge({ level, large }: Props) {
  const idx = Math.max(0, Math.min(3, level));
  return (
    <span className={`risk-badge ${RISK_CLASS[idx]}${large ? " large" : ""}`}>
      {riskLabel(level)}
    </span>
  );
}
