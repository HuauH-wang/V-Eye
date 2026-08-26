import type { MapDemoPoint } from "./types";

/** 与 scripts/seed_veye_demo.py 保持一致的清水河演示地理数据 */
export const VEYE_DEMO_TEAM_NAME = "veye小队";

export const VEYE_TEAM_START = { lat: 30.7518, lng: 103.9330, label: "集合点" };

export const VEYE_TASK_POINTS: Record<
  "Euphoria" | "Paddi" | "HuauH",
  { lat: number; lng: number; label: string; glyph: string; color: string; member: string }
> = {
  Euphoria: {
    lat: 30.7482,
    lng: 103.9323,
    label: "A · 识图（莲）",
    glyph: "A",
    color: "#3a86ff",
    member: "Euphoria",
  },
  Paddi: {
    lat: 30.7512,
    lng: 103.9332,
    label: "B · 报警 / LoRa",
    glyph: "B",
    color: "#2a9d8f",
    member: "Paddi",
  },
  HuauH: {
    lat: 30.74945,
    lng: 103.9324,
    label: "C · 指挥待命与救援",
    glyph: "C",
    color: "#f4a261",
    member: "HuauH",
  },
};

/** Euphoria 识图点向南延伸：查环境 → 查运动 */
export const VEYE_EUPHORIA_WAYPOINTS = [
  {
    id: "euph-env",
    lat: 30.74785,
    lng: 103.93225,
    label: "查环境",
    glyph: "环",
    color: "#3a86ff",
    member: "Euphoria",
  },
  {
    id: "euph-motion",
    lat: 30.74745,
    lng: 103.93215,
    label: "veye小队演示",
    glyph: "动",
    color: "#3a86ff",
    member: "Euphoria",
  },
] as const;

export function veyeDemoMapPoints(): MapDemoPoint[] {
  const start: MapDemoPoint = {
    id: "veye-start",
    kind: "start",
    glyph: "集",
    label: VEYE_TEAM_START.label,
    gps_lat: VEYE_TEAM_START.lat,
    gps_lng: VEYE_TEAM_START.lng,
    color: "#6c757d",
  };
  const tasks: MapDemoPoint[] = Object.entries(VEYE_TASK_POINTS).map(([key, t]) => ({
    id: `veye-task-${key}`,
    kind: "task" as const,
    glyph: t.glyph,
    label: t.label,
    member: t.member,
    gps_lat: t.lat,
    gps_lng: t.lng,
    color: t.color,
  }));
  const waypoints: MapDemoPoint[] = VEYE_EUPHORIA_WAYPOINTS.map((w) => ({
    id: `veye-waypoint-${w.id}`,
    kind: "task" as const,
    glyph: w.glyph,
    label: w.label,
    member: w.member,
    gps_lat: w.lat,
    gps_lng: w.lng,
    color: w.color,
  }));
  return [start, ...tasks, ...waypoints];
}

export function isVeyeDemoTeam(teamName: string | undefined | null): boolean {
  return teamName === VEYE_DEMO_TEAM_NAME;
}
