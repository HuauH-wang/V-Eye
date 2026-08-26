export type Scene = "toxic_plant" | "medicine" | "generic";

export type TabId =
  | "library"
  | "map"
  | "team"
  | "report"
  | "environment"
  | "motion"
  | "sos"
  | "settings";

export type MapBaseLayer = "amap" | "amap_satellite" | "street" | "topo" | "satellite" | "osm";

export type HealthInfo = {
  status: string;
  model: string;
  vllm: { base_url: string };
};

export type IdentifyResult = {
  request_id: string;
  label_main: string;
  confidence: number;
  candidates: { label: string; confidence: number }[];
  risk_level: number;
  risk_tags: string[];
  summary: string;
  advice: string;
  details: Record<string, unknown>;
  latency_ms: number;
};

export type IdentifyRecordItem = {
  request_id: string;
  device_id: string | null;
  scene: string;
  label_main: string;
  confidence: number;
  risk_level: number;
  summary: string;
  server_ts: string;
  has_original?: boolean;
  has_image?: boolean;
  last_enhance_mode?: string | null;
  gps_lat?: number | null;
  gps_lng?: number | null;
  accuracy_m?: number | null;
  has_gps?: boolean;
  gps_altitude?: number | null;
  baro_altitude?: number | null;
};

export type IdentifyRecordsResponse = {
  items: IdentifyRecordItem[];
  total: number;
  matched?: number;
};

export type HistoryEntry = IdentifyRecordItem & {
  previewDataUrl?: string;
  source: "server" | "local";
};

export type UserStats = {
  identify_count: number;
};

export type UserProfile = {
  id: string;
  username: string;
  email: string | null;
  display_name: string;
  has_avatar: boolean;
  created_at: string;
  stats: UserStats;
};

export type UserPublicItem = {
  id: string;
  username: string;
  display_name: string;
  has_avatar?: boolean;
};

export type MemberAvatarItem = {
  user_id: string;
  display_name: string;
  has_avatar: boolean;
};

export type TeamMemberItem = {
  user_id: string;
  username: string;
  display_name: string;
  role: string;
  joined_at: string;
  has_avatar?: boolean;
};

export type TeamSummary = {
  id: string;
  name: string;
  description: string;
  owner_id: string;
  member_count: number;
  my_role: string;
  has_avatar?: boolean;
  created_at: string;
};

export type TeamDetail = TeamSummary & {
  members: TeamMemberItem[];
};

export type TeamListResponse = {
  items: TeamSummary[];
};

export type MailItem = {
  id: string;
  mail_type: string;
  title: string;
  body: string;
  payload: Record<string, unknown>;
  is_read: boolean;
  action_status: string | null;
  sender: UserPublicItem | null;
  created_at: string;
};

export type MailListResponse = {
  items: MailItem[];
  unread_count: number;
};

export type MapIdentifyPoint = {
  request_id: string;
  label_main: string;
  scene: string;
  risk_level: number;
  summary: string;
  server_ts: string;
  gps_lat: number;
  gps_lng: number;
  accuracy_m?: number | null;
};

export type MapSosPoint = {
  incident_id: string;
  event_type: string;
  device_id: string;
  user_id?: string | null;
  display_name?: string | null;
  server_ts: string;
  client_ts?: string | null;
  gps_lat: number;
  gps_lng: number;
  accuracy_m?: number | null;
};

export type MapMemberPoint = {
  user_id: string;
  username: string;
  display_name: string;
  team_id?: string | null;
  server_ts: string;
  client_ts?: string | null;
  gps_lat: number;
  gps_lng: number;
  accuracy_m?: number | null;
  gps_altitude?: number | null;
  baro_altitude?: number | null;
};

export type MapLayersResponse = {
  identify: MapIdentifyPoint[];
  sos: MapSosPoint[];
  members: MapMemberPoint[];
};

export type MapTrackPoint = {
  gps_lat: number;
  gps_lng: number;
  server_ts: string;
  source?: string;
  gps_speed?: number | null;
  gps_altitude?: number | null;
};

export type MapTrajectory = {
  user_id: string;
  username: string;
  display_name: string;
  points: MapTrackPoint[];
};

export type MapDemoPoint = {
  id: string;
  kind: "start" | "task";
  glyph: string;
  label: string;
  member?: string;
  gps_lat: number;
  gps_lng: number;
  color?: string;
};

export type MapTrajectoriesResponse = {
  items: MapTrajectory[];
};

export const SCENE_LABELS: Record<Scene, string> = {
  toxic_plant: "有毒植物",
  medicine: "药品识别",
  generic: "通用识别",
};

export const RISK_LABELS = ["安全", "低风险", "中风险", "高风险"] as const;

export function riskLabel(level: number): string {
  return RISK_LABELS[Math.max(0, Math.min(3, level))] ?? "未知";
}

export const DISPLAY_TIMEZONE = "Asia/Shanghai";

export function formatTs(iso: string): string {
  try {
    return new Date(iso).toLocaleString("zh-CN", {
      timeZone: DISPLAY_TIMEZONE,
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  } catch {
    return iso;
  }
}

export function formatPct(n: number): string {
  return `${Math.round(n * 100)}%`;
}

export function formatGpsCoords(
  lat?: number | null,
  lng?: number | null,
  accuracyM?: number | null,
): string | null {
  if (lat == null || lng == null || !Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }
  const coords = `${lat.toFixed(5)}°, ${lng.toFixed(5)}°`;
  if (accuracyM != null && Number.isFinite(accuracyM) && accuracyM > 0) {
    return `${coords} · ±${Math.round(accuracyM)} m`;
  }
  return coords;
}

export function formatAltitude(gpsAlt?: number | null, baroAlt?: number | null): string | null {
  if (gpsAlt != null && Number.isFinite(gpsAlt)) {
    const base = `${Math.round(gpsAlt)} m`;
    if (baroAlt != null && Number.isFinite(baroAlt) && Math.abs(baroAlt - gpsAlt) >= 3) {
      return `${base}（气压 ${Math.round(baroAlt)} m）`;
    }
    return base;
  }
  if (baroAlt != null && Number.isFinite(baroAlt)) {
    return `${Math.round(baroAlt)} m（气压）`;
  }
  return null;
}

export function recordLocationLabel(entry: {
  has_gps?: boolean;
  gps_lat?: number | null;
  gps_lng?: number | null;
  accuracy_m?: number | null;
  gps_altitude?: number | null;
  baro_altitude?: number | null;
}): string | null {
  const coords = formatGpsCoords(entry.gps_lat, entry.gps_lng, entry.accuracy_m);
  const alt = formatAltitude(entry.gps_altitude, entry.baro_altitude);
  if (coords && alt) return `${coords} · 海拔 ${alt}`;
  if (coords) return coords;
  if (alt) return `海拔 ${alt}`;
  if (entry.has_gps) return "位置已记录";
  return null;
}

export function memberMapPopupHtml(item: MapMemberPoint): string {
  const loc = recordLocationLabel(item);
  return `<strong>${item.display_name}</strong><br/>@${item.username}${
    loc ? `<br/>${loc}` : ""
  }<br/>更新 ${formatTs(item.server_ts)}`;
}

export type ChatMessageItem = {
  id: string;
  team_id: string;
  sender: UserPublicItem;
  body: string;
  created_at: string;
  is_mine: boolean;
};

export type ChatMessagesResponse = {
  items: ChatMessageItem[];
  has_more: boolean;
};

export type ChatConversationItem = {
  team_id: string;
  team_name: string;
  member_count: number;
  has_avatar?: boolean;
  member_avatars?: MemberAvatarItem[];
  last_message: ChatMessageItem | null;
  unread_count: number;
};

export type ChatConversationsResponse = {
  items: ChatConversationItem[];
  total_unread: number;
};

export type ReportJobStatus =
  | "pending"
  | "aggregating"
  | "identifying"
  | "writing"
  | "done"
  | "failed"
  | "cancelled";

export type ReportType = "safety" | "travel";

export type ReportJobItem = {
  id: string;
  team_id: string;
  team_name: string;
  subject_user_id: string;
  subject_display_name: string;
  requested_by_user_id: string;
  requested_by_display_name: string;
  report_date: string;
  report_type: ReportType;
  status: ReportJobStatus;
  phase_detail: Record<string, unknown>;
  timeline_json: Record<string, unknown> | null;
  markdown: string | null;
  markdown_generated: string | null;
  is_edited: boolean;
  edited_at: string | null;
  created_at: string;
  updated_at: string;
  finished_at: string | null;
};

export type ReportJobListResponse = {
  items: ReportJobItem[];
};

export type ModelStatusResponse = {
  mode: "vision" | "report" | "idle" | "busy";
  report_job_active: boolean;
};

export type ReportPreviewResponse = {
  report_date: string;
  identify_count: number;
  sos_count: number;
  chat_messages_by_subject: number;
  has_data: boolean;
  suggested_report_date: string | null;
  empty_hint: string | null;
};

export type SosRecordItem = {
  incident_id: string;
  event_type: string;
  device_id: string;
  user_id: string | null;
  display_name: string | null;
  username: string | null;
  server_ts: string;
  client_ts: string | null;
  gps_lat: number | null;
  gps_lng: number | null;
  accuracy_m: number | null;
  has_gps: boolean;
  attributed: boolean;
};

export type SosRecordsResponse = {
  items: SosRecordItem[];
  total: number;
};
