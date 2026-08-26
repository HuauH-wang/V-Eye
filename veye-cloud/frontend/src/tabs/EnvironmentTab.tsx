import { Droplets, Gauge, MapPin, RefreshCw, Thermometer, Wind } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
  fetchEnvironmentLatest,
  formatMetric,
  type EnvironmentLatest,
} from "../lib/environment";
import { formatGpsCoords } from "../lib/types";

function MetricCard({
  icon,
  label,
  value,
  hint,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <article className="env-metric neo-card">
      <div className="env-metric-icon" aria-hidden>
        {icon}
      </div>
      <div>
        <p className="env-metric-label">{label}</p>
        <p className="env-metric-value">{value}</p>
        {hint ? <p className="muted env-metric-hint">{hint}</p> : null}
      </div>
    </article>
  );
}

export default function EnvironmentTab() {
  const [data, setData] = useState<EnvironmentLatest | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setErr("");
    try {
      setData(await fetchEnvironmentLatest());
    } catch (e) {
      setErr(String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const coords =
    data?.gps_lat != null && data.gps_lng != null
      ? formatGpsCoords(data.gps_lat, data.gps_lng, data.accuracy_m ?? undefined)
      : "暂无 GPS";

  return (
    <div className="tab-page env-tab">
      <header className="tab-header env-tab-header">
        <div>
          <h1>环境监测{data?.label ? ` · ${data.label}` : ""}</h1>
        </div>
        <button type="button" className="btn ghost" onClick={load} disabled={loading}>
          <RefreshCw size={16} aria-hidden />
          {loading ? "刷新中…" : "刷新"}
        </button>
      </header>

      {err ? <div className="alert alert-warn">{err}</div> : null}

      {!data && loading ? <p className="muted">加载环境数据…</p> : null}

      {data ? (
        <>
          <div className="env-metric-grid">
            <MetricCard
              icon={<Thermometer size={20} />}
              label="温度"
              value={formatMetric(data.temperature, "°C")}
              hint="现场体感参考"
            />
            <MetricCard
              icon={<Droplets size={20} />}
              label="湿度"
              value={formatMetric(data.humidity, "%", 0)}
              hint="相对湿度"
            />
            <MetricCard
              icon={<Gauge size={20} />}
              label="GPS 海拔"
              value={formatMetric(data.gps_altitude, "m")}
            />
            <MetricCard
              icon={<Wind size={20} />}
              label="气压海拔"
              value={formatMetric(data.baro_altitude, "m")}
            />
          </div>

          <section className="neo-card section-block env-location">
            <div className="env-section-head">
              <MapPin size={18} aria-hidden />
              <h2>位置</h2>
            </div>
            <p>{coords}</p>
            <p className="muted">
              航向 {formatMetric(data.gps_heading, "°", 0)} · 速度{" "}
              {formatMetric(data.gps_speed, "m/s")} · 数据源 {data.source}
            </p>
            <p className="muted env-updated">
              更新时间 {new Date(data.server_ts).toLocaleString()}
            </p>
          </section>

          <section className="neo-card section-block env-imu">
            <h2>IMU / 姿态</h2>
            <div className="env-imu-grid">
              <div>
                <h3 className="env-imu-title">加速度 (m/s²)</h3>
                <p>
                  X {formatMetric(data.accel_x, "")} · Y {formatMetric(data.accel_y, "")} · Z{" "}
                  {formatMetric(data.accel_z, "")}
                </p>
              </div>
              <div>
                <h3 className="env-imu-title">陀螺仪 (rad/s)</h3>
                <p>
                  X {formatMetric(data.gyro_x, "")} · Y {formatMetric(data.gyro_y, "")} · Z{" "}
                  {formatMetric(data.gyro_z, "")}
                </p>
              </div>
              <div>
                <h3 className="env-imu-title">姿态</h3>
                <p>
                  Pitch {formatMetric(data.pitch, "°")} · Roll {formatMetric(data.roll, "°")} · IMU
                  航向 {formatMetric(data.imu_heading, "°", 0)}
                </p>
              </div>
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
}
