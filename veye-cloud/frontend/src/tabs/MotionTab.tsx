import { useEffect, useState } from "react";
import XiaoOuBubble from "../components/XiaoOuBubble";
import { fetchCompanion, fetchMotionSessions, type CompanionState, type MotionSession } from "../lib/motion";

export default function MotionTab() {
  const [companion, setCompanion] = useState<CompanionState | null>(null);
  const [sessions, setSessions] = useState<MotionSession[]>([]);
  const [err, setErr] = useState("");

  useEffect(() => {
    (async () => {
      try {
        const [c, s] = await Promise.all([fetchCompanion(), fetchMotionSessions()]);
        setCompanion(c);
        setSessions(s.items);
        setErr("");
      } catch (e) {
        setErr(String(e));
      }
    })();
  }, []);

  return (
    <div className="tab-page">
      <header className="tab-header">
        <h1>运动健康</h1>
      </header>

      {err ? <div className="alert alert-warn">{err}</div> : null}

      {companion ? (
        <section className="neo-card section-block">
          <XiaoOuBubble companion={companion} />
        </section>
      ) : null}

      <section className="neo-card section-block">
        <h2>运动会话</h2>
        {sessions.length === 0 ? (
          <p className="muted">暂无运动记录，请在 App 运动页开始锻炼。</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>任务</th>
                <th>开始时间</th>
                <th>状态</th>
                <th>步数</th>
                <th>跌倒</th>
                <th>姿态分</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => {
                const label = typeof s.summary_json?.label === "string" ? s.summary_json.label : "—";
                return (
                  <tr key={s.id}>
                    <td>{label}</td>
                    <td>{new Date(s.started_at).toLocaleString()}</td>
                    <td>{s.status}</td>
                    <td>{s.steps}</td>
                    <td>{s.fall_count}</td>
                    <td>{s.pose_score.toFixed(0)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
