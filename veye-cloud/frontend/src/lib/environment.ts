import { apiFetch } from "./api";

export type EnvironmentLatest = {
  server_ts: string;
  gps_lat: number | null;
  gps_lng: number | null;
  accuracy_m: number | null;
  gps_altitude: number | null;
  baro_altitude: number | null;
  temperature: number | null;
  humidity: number | null;
  gps_speed: number | null;
  gps_heading: number | null;
  imu_heading: number | null;
  pitch: number | null;
  roll: number | null;
  accel_x: number | null;
  accel_y: number | null;
  accel_z: number | null;
  gyro_x: number | null;
  gyro_y: number | null;
  gyro_z: number | null;
  source: string;
  label?: string | null;
};

export async function fetchEnvironmentLatest(): Promise<EnvironmentLatest> {
  const res = await apiFetch("/environment/latest");
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export function formatMetric(value: number | null | undefined, unit: string, digits = 1): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  const n = value.toFixed(digits);
  return unit ? `${n} ${unit}` : n;
}
