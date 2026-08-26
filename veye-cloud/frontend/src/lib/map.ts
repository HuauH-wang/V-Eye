import type { MapLayersResponse, MapTrajectoriesResponse } from "./types";
import { apiFetch, readErrorBody } from "./api";

export type LocationReportBody = {
  gps_lat: number;
  gps_lng: number;
  accuracy_m?: number;
  team_id?: string;
  client_ts?: string;
  gps_speed?: number;
  gps_heading?: number;
  gps_altitude?: number;
  accel_x?: number;
  accel_y?: number;
  accel_z?: number;
  gyro_x?: number;
  gyro_y?: number;
  gyro_z?: number;
  mag_x?: number;
  mag_y?: number;
  mag_z?: number;
  pitch?: number;
  roll?: number;
  imu_heading?: number;
  baro_altitude?: number;
};

export async function fetchMapLayers(params: {
  teamId?: string;
  days?: number;
}): Promise<MapLayersResponse> {
  const qs = new URLSearchParams();
  if (params.teamId) qs.set("team_id", params.teamId);
  if (params.days) qs.set("days", String(params.days));
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  const res = await apiFetch(`/map/layers${suffix}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as MapLayersResponse;
}

export async function fetchMapTrajectories(params: {
  teamId: string;
  days?: number;
}): Promise<MapTrajectoriesResponse> {
  const qs = new URLSearchParams({ team_id: params.teamId });
  if (params.days) qs.set("days", String(params.days));
  const res = await apiFetch(`/map/trajectories?${qs.toString()}`, { method: "GET" });
  if (!res.ok) throw new Error(await readErrorBody(res));
  return (await res.json()) as MapTrajectoriesResponse;
}

export async function reportMapLocation(body: LocationReportBody): Promise<void> {
  const res = await apiFetch("/map/location", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...body,
      client_ts: body.client_ts ?? new Date().toISOString(),
    }),
  });
  if (!res.ok) throw new Error(await readErrorBody(res));
}

export function mapErrorMessage(err: unknown): string {
  const msg = String(err);
  const map: Record<string, string> = {
    not_authenticated: "请先登录",
    not_team_member: "你不是该小队成员",
    team_not_found: "小队不存在",
    "invalid team_id": "小队 ID 无效",
    api_route_not_found_restart_server: "服务端未加载地图接口，请重启 FastAPI",
  };
  for (const [key, label] of Object.entries(map)) {
    if (msg.includes(key)) return label;
  }
  return msg;
}
