import { useState } from "react";
import { fetchHealth, loadApiBase, loadApiKey, saveSettings } from "../lib/api";
import { gpsFixFromFields, loadGpsFix, saveGpsFix } from "../lib/gpsSettings";
import type { HealthInfo } from "../lib/types";
import IdentifySidebar from "./identify/IdentifySidebar";
import IdentifyTab from "./identify/IdentifyTab";

type Props = {
  onActivity: (msg: string) => void;
};

export default function SettingsTab({ onActivity }: Props) {
  const [apiBase, setApiBase] = useState(loadApiBase);
  const [apiKey, setApiKey] = useState(loadApiKey);
  const [health, setHealth] = useState<HealthInfo | null>(null);
  const [healthRaw, setHealthRaw] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [saved, setSaved] = useState(false);
  const initialGps = loadGpsFix();
  const [gpsLat, setGpsLat] = useState(initialGps ? String(initialGps.lat) : "");
  const [gpsLng, setGpsLng] = useState(initialGps ? String(initialGps.lng) : "");
  const [gpsAcc, setGpsAcc] = useState(
    initialGps?.accuracyM !== undefined ? String(initialGps.accuracyM) : "",
  );
  const [gpsSaved, setGpsSaved] = useState(false);

  const persist = () => {
    saveSettings(apiBase, apiKey);
    setSaved(true);
    setErr("");
    onActivity("连接设置已保存到浏览器。");
    window.setTimeout(() => setSaved(false), 2000);
  };

  const persistGps = () => {
    const fix = gpsFixFromFields(gpsLat, gpsLng, gpsAcc);
    if (!fix) {
      setErr("GPS 坐标无效，请填写有效纬度和经度。");
      return;
    }
    saveGpsFix(fix);
    setGpsSaved(true);
    setErr("");
    onActivity(`高精度 GPS 已保存 (${fix.lat}, ${fix.lng})`);
    window.setTimeout(() => setGpsSaved(false), 2000);
  };

  const clearGps = () => {
    saveGpsFix(null);
    setGpsLat("");
    setGpsLng("");
    setGpsAcc("");
    onActivity("已清除 GPS 坐标。");
  };

  const runHealth = async () => {
    setBusy(true);
    setErr("");
    setHealth(null);
    setHealthRaw("");
    try {
      const data = await fetchHealth();
      setHealth(data);
      setHealthRaw(JSON.stringify(data, null, 2));
      onActivity(`服务正常 · 模型 ${data.model}`);
    } catch (e) {
      setErr(String(e));
      onActivity("健康检查失败。");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="form-layout">
      <div className="section-head">
        <div>
          <h2>视觉识别</h2>
        </div>
      </div>

      <IdentifySidebar variant="settings" />
      <IdentifyTab embedded />

      <div className="section-head">
        <div>
          <h2>连接与诊断</h2>
        </div>
      </div>

      <div className="form-grid neo-card settings-section">
        <div className="field span-2">
          <label htmlFor="apiBase">API Base（可选）</label>
          <input
            id="apiBase"
            type="url"
            placeholder="例如 https://your-host:6006 留空则用 /api 代理"
            value={apiBase}
            onChange={(e) => setApiBase(e.target.value)}
          />
        </div>
        <div className="field span-2">
          <label htmlFor="apiKey">X-API-Key（可选）</label>
          <input
            id="apiKey"
            type="password"
            autoComplete="off"
            placeholder="与服务端 API_KEY 一致时填写"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
        </div>
        <button type="button" className="btn secondary" onClick={persist}>
          {saved ? "已保存 ✓" : "保存到浏览器"}
        </button>
        <button type="button" className="btn primary" disabled={busy} onClick={runHealth}>
          {busy ? "检查中…" : "GET /health"}
        </button>
      </div>

      <div className="section-head">
        <div>
          <h2>高精度 GPS（WGS84）</h2>
        </div>
      </div>

      <div className="form-grid neo-card settings-section gps-settings-card">
        <div className="field">
          <label htmlFor="gpsLat">纬度 gps_lat</label>
          <input
            id="gpsLat"
            type="text"
            inputMode="decimal"
            placeholder="例如 30.592849"
            value={gpsLat}
            onChange={(e) => setGpsLat(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="gpsLng">经度 gps_lng</label>
          <input
            id="gpsLng"
            type="text"
            inputMode="decimal"
            placeholder="例如 114.305539"
            value={gpsLng}
            onChange={(e) => setGpsLng(e.target.value)}
          />
        </div>
        <div className="field span-2">
          <label htmlFor="gpsAcc">精度 accuracy_m（可选，用于地图精度圈）</label>
          <input
            id="gpsAcc"
            type="text"
            inputMode="decimal"
            placeholder="例如 0.15 表示 15 厘米"
            value={gpsAcc}
            onChange={(e) => setGpsAcc(e.target.value)}
          />
        </div>
        <button type="button" className="btn secondary" onClick={persistGps}>
          {gpsSaved ? "GPS 已保存 ✓" : "保存 GPS 坐标"}
        </button>
        <button type="button" className="btn ghost" onClick={clearGps}>
          清除
        </button>
      </div>

      {err ? (
        <div className="alert neo-card" role="alert">
          {err}
        </div>
      ) : null}

      {health ? (
        <div className="health-cards">
          <div className="mini-card neo-card">
            <h3>状态</h3>
            <p>{health.status}</p>
          </div>
          <div className="mini-card neo-card accent-panel">
            <h3>模型</h3>
            <p className="mono small">{health.model}</p>
          </div>
          <div className="mini-card neo-card">
            <h3>vLLM</h3>
            <p className="mono small">{health.vllm.base_url}</p>
          </div>
        </div>
      ) : null}

      {healthRaw ? <pre className="code-block">{healthRaw}</pre> : null}
    </div>
  );
}
