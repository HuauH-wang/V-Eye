import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "./context/AuthContext";
import AppSidebar from "./components/AppSidebar";
import SystemNotifications from "./components/SystemNotifications";
import ProfileMenu from "./components/ProfileMenu";
import { fetchHealth, fetchRecords } from "./lib/api";
import { addLocalHistory, loadLocalHistory, mergeHistory } from "./lib/history";
import { fetchUnreadCount } from "./lib/team";
import type { HealthInfo, HistoryEntry, IdentifyResult, Scene, TabId } from "./lib/types";
import { IdentifyProvider } from "./tabs/identify/IdentifyContext";
import LibraryTab from "./tabs/LibraryTab";
import MapTab from "./tabs/MapTab";
import SettingsTab from "./tabs/SettingsTab";
import SosTab from "./tabs/SosTab";
import TeamTab from "./tabs/TeamTab";
import ReportTab from "./tabs/ReportTab";
import MotionTab from "./tabs/MotionTab";
import EnvironmentTab from "./tabs/EnvironmentTab";
export default function AppShell() {
  const { user, refreshUser } = useAuth();
  const [tab, setTab] = useState<TabId>("map");
  const [health, setHealth] = useState<HealthInfo | null>(null);
  const [history, setHistory] = useState<HistoryEntry[]>(() => loadLocalHistory());
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyErr, setHistoryErr] = useState("");
  const [tickerMsg, setTickerMsg] = useState("V-Eye 云端控制台已就绪");
  const [notifyOpen, setNotifyOpen] = useState(false);
  const [unreadNotify, setUnreadNotify] = useState(0);
  const [teamRefreshKey, setTeamRefreshKey] = useState(0);

  const refreshHistory = useCallback(async () => {
    setHistoryLoading(true);
    setHistoryErr("");
    const local = loadLocalHistory();
    try {
      const data = await fetchRecords({ limit: 100 });
      setHistory(mergeHistory(data.items, local));
    } catch (e) {
      setHistoryErr(String(e));
      setHistory(local);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const refreshHealth = useCallback(async () => {
    try {
      const data = await fetchHealth();
      setHealth(data);
    } catch {
      setHealth(null);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    refreshHistory();
    const t = window.setInterval(refreshHealth, 30_000);
    return () => window.clearInterval(t);
  }, [refreshHealth, refreshHistory]);

  const refreshUnreadNotify = useCallback(async () => {
    try {
      const count = await fetchUnreadCount();
      setUnreadNotify(count);
    } catch {
      /* ignore when offline */
    }
  }, []);

  useEffect(() => {
    refreshUnreadNotify();
    const t = window.setInterval(refreshUnreadNotify, 45_000);
    return () => window.clearInterval(t);
  }, [refreshUnreadNotify]);

  const onIdentifySuccess = useCallback(
    (result: IdentifyResult, scene: Scene, previewUrl: string | undefined) => {
      addLocalHistory(result, scene, previewUrl);
      refreshHistory();
      refreshUser().catch(() => undefined);
    },
    [refreshHistory, refreshUser],
  );

  const tickerItems = useMemo(() => {
    const items = [tickerMsg];
    if (user) {
      items.push(`欢迎，${user.display_name} · 已识别 ${user.stats.identify_count} 次`);
    }
    if (health) {
      items.push(`服务 ${health.status} · ${health.model.split("/").pop() ?? health.model}`);
    } else {
      items.push("服务状态未知 · 请在设置页检查 /health");
    }
    if (history[0]) {
      items.push(`最近识别：${history[0].label_main}`);
    }
    return items;
  }, [tickerMsg, user, health, history]);

  return (
    <div className="app-shell">
      <div className="top-ticker" aria-live="polite">
        <span className="ticker-label">状态</span>
        <div className="ticker-track">
          <span className="ticker-text">{tickerItems.join("  ◆  ")}</span>
        </div>
      </div>

      <IdentifyProvider onSuccess={onIdentifySuccess} onActivity={setTickerMsg}>
        <div className="app-body">
          <AppSidebar tab={tab} onTabChange={setTab} />

          <div className="main-column">
            <header className="top-bar neo-card">
              <h1 className="page-title">
                {tab === "library" && "识别图鉴"}
                {tab === "map" && "观测地图"}
                {tab === "team" && "组队群聊"}
                {tab === "report" && "工作报告"}
                {tab === "environment" && "环境监测"}
                {tab === "motion" && "运动健康"}
                {tab === "sos" && "SOS 上报"}
                {tab === "settings" && "设置"}
              </h1>
              <div className="top-bar-right">
                <button
                  type="button"
                  className="btn ghost mail-trigger"
                  onClick={() => setNotifyOpen(true)}
                  aria-label={`系统通知${unreadNotify ? `，${unreadNotify} 条未读` : ""}`}
                >
                  🔔 系统通知
                  {unreadNotify > 0 ? (
                    <span className="mail-badge">{unreadNotify > 99 ? "99+" : unreadNotify}</span>
                  ) : null}
                </button>
                <div className="header-status">
                  <span className={`status-dot${health ? " ok" : ""}`} />
                  {health ? "在线" : "离线"}
                </div>
                <ProfileMenu onActivity={setTickerMsg} />
              </div>
            </header>

            <main className="main-content">
              {tab === "library" ? (
                <LibraryTab
                  items={history}
                  loading={historyLoading}
                  err={historyErr}
                  onRefresh={refreshHistory}
                />
              ) : null}
              {tab === "map" ? <MapTab onActivity={setTickerMsg} /> : null}
              {tab === "team" ? (
                <TeamTab onActivity={setTickerMsg} refreshKey={teamRefreshKey} />
              ) : null}
              {tab === "report" ? <ReportTab onActivity={setTickerMsg} /> : null}
              {tab === "environment" ? <EnvironmentTab /> : null}
              {tab === "motion" ? <MotionTab /> : null}
              {tab === "sos" ? <SosTab onActivity={setTickerMsg} /> : null}
              {tab === "settings" ? <SettingsTab onActivity={setTickerMsg} /> : null}
            </main>
          </div>
        </div>
      </IdentifyProvider>

      <SystemNotifications
        open={notifyOpen}
        onClose={() => setNotifyOpen(false)}
        onUnreadChange={setUnreadNotify}
        onTeamChange={() => setTeamRefreshKey((k) => k + 1)}
      />
    </div>
  );
}
