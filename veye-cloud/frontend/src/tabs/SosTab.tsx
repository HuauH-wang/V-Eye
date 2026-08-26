import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { fetchTeams } from "../lib/team";
import { fetchSosRecords, deleteSosRecord, sosErrorMessage, sosEventLabel } from "../lib/sos";
import type { SosRecordItem, TeamSummary } from "../lib/types";
import { formatTs } from "../lib/types";

type Props = {
  onActivity: (msg: string) => void;
};

export default function SosTab({ onActivity }: Props) {
  const { user } = useAuth();
  const [teamId, setTeamId] = useState("");
  const [teams, setTeams] = useState<TeamSummary[]>([]);
  const [records, setRecords] = useState<SosRecordItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const loadRecords = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    try {
      const data = await fetchSosRecords({
        team_id: teamId || undefined,
        days: 180,
        limit: 100,
      });
      setRecords(data.items);
      setErr("");
    } catch (e) {
      setErr(sosErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [user, teamId]);

  useEffect(() => {
    if (!user) return;
    fetchTeams()
      .then((data) => setTeams(data.items))
      .catch(() => undefined);
  }, [user]);

  useEffect(() => {
    loadRecords();
  }, [loadRecords]);

  const handleDeleteRecord = async (row: SosRecordItem) => {
    if (!window.confirm(`确定删除 SOS 记录「${sosEventLabel(row.event_type)} · ${formatTs(row.server_ts)}」？`)) {
      return;
    }
    setDeletingId(row.incident_id);
    setErr("");
    try {
      await deleteSosRecord(row.incident_id);
      setRecords((prev) => prev.filter((r) => r.incident_id !== row.incident_id));
      onActivity("SOS 记录已删除");
    } catch (e) {
      setErr(sosErrorMessage(e));
    } finally {
      setDeletingId(null);
    }
  };

  if (!user) {
    return (
      <div className="form-layout">
        <div className="empty-state neo-card">
          <h3>请先登录</h3>
          <p className="muted">登录后可查看 SOS 历史记录。</p>
        </div>
      </div>
    );
  }

  return (
    <div className="sos-layout page-fill">
      <div className="section-head">
        <div>
          <h2>SOS 记录</h2>
        </div>
      </div>

      {err ? (
        <div className="alert neo-card" role="alert">
          {err}
        </div>
      ) : null}

      <section className="sos-history neo-card page-fill-panel">
        <div className="sos-history-head">
          <h3>SOS 历史记录</h3>
          <div className="report-toolbar-actions">
            <select
              className="map-select"
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
              aria-label="筛选小队"
            >
              <option value="">仅我的记录</option>
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                  {t.my_role === "owner" ? "（队长·全队）" : ""}
                </option>
              ))}
            </select>
            <button type="button" className="btn ghost" disabled={loading} onClick={loadRecords}>
              <RefreshCw size={16} aria-hidden />
              {loading ? "刷新中…" : "刷新"}
            </button>
          </div>
        </div>

        {records.length === 0 ? (
          <p className="muted">暂无 SOS 记录。{teamId ? "尝试切换为「仅我的记录」。" : ""}</p>
        ) : (
          <ul className="sos-history-list">
            {records.map((row) => (
              <li
                key={row.incident_id}
                className={`sos-history-row${row.attributed ? "" : " unattributed"}`}
              >
                <div className="sos-history-main">
                  <strong>{sosEventLabel(row.event_type)}</strong>
                  <span className="muted small">
                    {row.display_name ?? row.username ?? "未归属用户"} · {row.device_id}
                  </span>
                  {!row.attributed ? (
                    <span className="sos-tag muted-tag">历史未归属</span>
                  ) : null}
                  {!row.has_gps ? <span className="muted small">无 GPS（地图不显示）</span> : null}
                  {row.has_gps ? (
                    <span className="muted small">
                      GPS {row.gps_lat?.toFixed(5)}, {row.gps_lng?.toFixed(5)}
                    </span>
                  ) : null}
                </div>
                <div className="sos-history-meta">
                  <span>{formatTs(row.server_ts)}</span>
                  <span className="mono small">{row.incident_id.slice(0, 8)}</span>
                  <button
                    type="button"
                    className="btn danger ghost sos-delete-btn"
                    disabled={deletingId === row.incident_id}
                    onClick={() => void handleDeleteRecord(row)}
                  >
                    {deletingId === row.incident_id ? "删除中…" : "删除"}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
