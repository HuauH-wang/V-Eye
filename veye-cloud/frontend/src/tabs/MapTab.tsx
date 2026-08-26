import { RefreshCw, Upload } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import ObservatoryMap from "../components/ObservatoryMap";
import { isAmapSdkConfigured } from "../lib/amapSdk";
import { loadGpsFix } from "../lib/gpsSettings";
import { fetchMapLayers, fetchMapTrajectories, mapErrorMessage, reportMapLocation } from "../lib/map";
import {
  MAP_LAYER_CHIP_ORDER,
  MAP_LAYER_FALLBACK_ORDER,
  MAP_SDK_LAYER_LABELS,
  MAP_SDK_LAYER_ORDER,
  MAP_TILE_LAYERS,
} from "../lib/mapTiles";
import { fetchTeams } from "../lib/team";
import { isVeyeDemoTeam, veyeDemoMapPoints } from "../lib/veyeDemoGeo";
import type { MapBaseLayer, MapLayersResponse, MapTrajectory, TeamSummary } from "../lib/types";

type Props = {
  onActivity: (msg: string) => void;
};

const EMPTY_LAYERS: MapLayersResponse = { identify: [], sos: [], members: [] };

export default function MapTab({ onActivity }: Props) {
  const sdkMode = isAmapSdkConfigured();
  const [teams, setTeams] = useState<TeamSummary[]>([]);
  const [teamId, setTeamId] = useState("");
  const [baseLayer, setBaseLayer] = useState<MapBaseLayer>(sdkMode ? "amap" : "street");
  const [tileHint, setTileHint] = useState("");
  const [showIdentify, setShowIdentify] = useState(true);
  const [showSos, setShowSos] = useState(true);
  const [showMembers, setShowMembers] = useState(true);
  const [showTrajectories, setShowTrajectories] = useState(true);
  const [data, setData] = useState<MapLayersResponse>(EMPTY_LAYERS);
  const [trajectories, setTrajectories] = useState<MapTrajectory[]>([]);
  const [loading, setLoading] = useState(false);
  const [reporting, setReporting] = useState(false);
  const [err, setErr] = useState("");

  const loadTeams = useCallback(async () => {
    try {
      const res = await fetchTeams();
      setTeams(res.items);
    } catch {
      setTeams([]);
    }
  }, []);

  const refreshLayers = useCallback(async () => {
    setLoading(true);
    setErr("");
    try {
      const layers = await fetchMapLayers({
        teamId: teamId || undefined,
        days: 30,
      });
      setData(layers);
      let trackCount = 0;
      if (teamId) {
        const tracks = await fetchMapTrajectories({ teamId, days: 1 });
        setTrajectories(tracks.items);
        trackCount = tracks.items.length;
      } else {
        setTrajectories([]);
      }
      onActivity(
        `地图已刷新：识别 ${layers.identify.length} · SOS ${layers.sos.length} · 队员 ${layers.members.length} · 轨迹 ${trackCount}`,
      );
    } catch (e) {
      setErr(mapErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [onActivity, teamId]);

  useEffect(() => {
    loadTeams();
  }, [loadTeams]);

  useEffect(() => {
    refreshLayers();
    const timer = window.setInterval(refreshLayers, 30_000);
    return () => window.clearInterval(timer);
  }, [refreshLayers]);

  const reportLocation = async () => {
    const fix = loadGpsFix();
    if (!fix) {
      setErr("请先在「设置 → 高精度 GPS」中填写坐标。");
      return;
    }
    setReporting(true);
    setErr("");
    try {
      await reportMapLocation({
        gps_lat: fix.lat,
        gps_lng: fix.lng,
        accuracy_m: fix.accuracyM,
        team_id: teamId || undefined,
      });
      onActivity(`已上报位置 (${fix.lat.toFixed(6)}, ${fix.lng.toFixed(6)})`);
      await refreshLayers();
    } catch (e) {
      setErr(mapErrorMessage(e));
    } finally {
      setReporting(false);
    }
  };

  const layerChips = sdkMode ? MAP_SDK_LAYER_ORDER : MAP_LAYER_CHIP_ORDER;

  const layerLabel = (id: MapBaseLayer): string => {
    if (sdkMode && (id === "amap" || id === "amap_satellite")) {
      return MAP_SDK_LAYER_LABELS[id];
    }
    return MAP_TILE_LAYERS[id].label;
  };

  const handleMapError = useCallback((message: string) => {
    setErr(`高德地图加载失败：${message}。请检查 Key、安全密钥与域名白名单。`);
  }, []);

  const handleTileWarning = useCallback(() => {
    if (!sdkMode) setTileHint("部分瓦片加载较慢，正在重试…");
  }, []);

  const handleTileError = useCallback((failed: MapBaseLayer) => {
    const idx = MAP_LAYER_FALLBACK_ORDER.indexOf(failed);
    const next = idx >= 0 ? MAP_LAYER_FALLBACK_ORDER[idx + 1] : MAP_LAYER_FALLBACK_ORDER[0];
    if (next && next !== failed) {
      setBaseLayer(next);
      setTileHint(`「${MAP_TILE_LAYERS[failed].label}」加载失败，已切换为「${MAP_TILE_LAYERS[next].label}」。`);
    } else {
      setTileHint("底图瓦片加载失败，请确认 FastAPI 已重启并检查服务端出网。");
    }
  }, []);

  const selectedTeam = teams.find((t) => t.id === teamId);
  const demoPoints = useMemo(
    () => (isVeyeDemoTeam(selectedTeam?.name) ? veyeDemoMapPoints() : undefined),
    [selectedTeam?.name],
  );

  const counts = useMemo(
    () => ({
      identify: data.identify.length,
      sos: data.sos.length,
      members: data.members.length,
      trajectories: trajectories.length,
    }),
    [data, trajectories],
  );

  return (
    <div className="map-tab">
      <div className="map-toolbar neo-card">
        <div className="map-toolbar-group">
          <label className="map-toolbar-label" htmlFor="mapTeam">
            小队态势
          </label>
          <select
            id="mapTeam"
            className="map-select"
            value={teamId}
            onChange={(e) => setTeamId(e.target.value)}
          >
            <option value="">仅本人 SOS</option>
            {teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>
        </div>

        <div className="map-toolbar-group">
          <span className="map-toolbar-label">底图</span>
          <div className="map-chip-row">
            {layerChips.map((id) => (
              <button
                key={id}
                type="button"
                className={`chip${baseLayer === id ? " active" : ""}`}
                onClick={() => {
                  setBaseLayer(id);
                  setTileHint("");
                }}
              >
                {layerLabel(id)}
              </button>
            ))}
          </div>
        </div>

        <div className="map-toolbar-group">
          <span className="map-toolbar-label">图层</span>
          <div className="map-chip-row">
            <button
              type="button"
              className={`chip identify${showIdentify ? " active" : ""}`}
              onClick={() => setShowIdentify((v) => !v)}
            >
              识别 {counts.identify}
            </button>
            <button
              type="button"
              className={`chip sos${showSos ? " active" : ""}`}
              onClick={() => setShowSos((v) => !v)}
            >
              SOS {counts.sos}
            </button>
            <button
              type="button"
              className={`chip members${showMembers ? " active" : ""}`}
              onClick={() => setShowMembers((v) => !v)}
              disabled={!teamId}
              title={teamId ? undefined : "选择小队后显示队员位置"}
            >
              队员 {counts.members}
            </button>
            <button
              type="button"
              className={`chip trajectories${showTrajectories ? " active" : ""}`}
              onClick={() => setShowTrajectories((v) => !v)}
              disabled={!teamId}
              title={teamId ? undefined : "选择小队后显示成员轨迹"}
            >
              轨迹 {counts.trajectories}
            </button>
          </div>
        </div>

        <div className="map-toolbar-actions">
          <button type="button" className="btn ghost" disabled={loading} onClick={refreshLayers}>
            <RefreshCw size={16} aria-hidden />
            {loading ? "刷新中…" : "刷新"}
          </button>
          <button type="button" className="btn secondary" disabled={reporting} onClick={reportLocation}>
            <Upload size={16} aria-hidden />
            {reporting ? "上报中…" : "上报位置"}
          </button>
        </div>
      </div>

      {tileHint ? (
        <div className="alert inline neo-card" role="status">
          {tileHint}
        </div>
      ) : null}

      {err ? (
        <div className="alert neo-card" role="alert">
          {err}
        </div>
      ) : null}

      <div className="map-stage neo-card">
        <ObservatoryMap
          data={data}
          trajectories={trajectories}
          baseLayer={baseLayer}
          demoPoints={demoPoints}
          onTileLayerWarning={sdkMode ? undefined : handleTileWarning}
          onTileLayerError={sdkMode ? undefined : handleTileError}
          onMapError={sdkMode ? handleMapError : undefined}
          visibility={{
            identify: showIdentify,
            sos: showSos,
            members: showMembers && Boolean(teamId),
            trajectories: showTrajectories && Boolean(teamId),
            demoPoints: Boolean(demoPoints?.length),
          }}
        />
      </div>
    </div>
  );
}
