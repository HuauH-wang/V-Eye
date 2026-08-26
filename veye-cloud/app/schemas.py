from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


Scene = Literal["toxic_plant", "medicine", "generic"]
RecordSort = Literal[
    "time_desc",
    "time_asc",
    "confidence_desc",
    "confidence_asc",
    "risk_desc",
    "risk_asc",
    "label_asc",
    "label_desc",
]


class Candidate(BaseModel):
    label: str
    confidence: float = Field(ge=0.0, le=1.0)


class IdentifyResponse(BaseModel):
    request_id: str
    label_main: str
    confidence: float = Field(ge=0.0, le=1.0)
    candidates: list[Candidate]
    risk_level: int = Field(ge=0, le=3)
    risk_tags: list[str]
    summary: str
    advice: str
    details: dict[str, Any] = Field(default_factory=dict)
    latency_ms: int = Field(ge=0)


class SosRequest(BaseModel):
    device_id: str
    event_type: str
    client_ts: datetime | None = None
    gps_lat: float | None = None
    gps_lng: float | None = None
    accuracy_m: float | None = None


class SosResponse(BaseModel):
    ok: bool = True
    incident_id: str


class SosRecordItem(BaseModel):
    incident_id: str
    event_type: str
    device_id: str
    user_id: str | None = None
    display_name: str | None = None
    username: str | None = None
    server_ts: datetime
    client_ts: datetime | None = None
    gps_lat: float | None = None
    gps_lng: float | None = None
    accuracy_m: float | None = None
    has_gps: bool = False
    attributed: bool = True


class SosRecordsResponse(BaseModel):
    items: list[SosRecordItem]
    total: int


class HealthResponse(BaseModel):
    status: str = "ok"
    model: str
    vllm: dict[str, str]


class IdentifyRecordItem(BaseModel):
    request_id: str
    device_id: str | None = None
    scene: str
    label_main: str
    confidence: float = Field(ge=0.0, le=1.0, default=0.0)
    risk_level: int = Field(ge=0, le=3, default=0)
    summary: str = ""
    server_ts: datetime
    has_original: bool = False
    has_image: bool = True
    last_enhance_mode: str | None = None
    gps_lat: float | None = None
    gps_lng: float | None = None
    accuracy_m: float | None = None
    has_gps: bool = False
    gps_altitude: float | None = None
    baro_altitude: float | None = None


class IdentifyRecordsResponse(BaseModel):
    items: list[IdentifyRecordItem]
    total: int
    matched: int | None = None


class UserRegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=6, max_length=128)
    display_name: str | None = Field(default=None, max_length=64)
    email: str | None = Field(default=None, max_length=254)


class UserLoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=32)
    password: str = Field(min_length=1, max_length=128)


class UserStats(BaseModel):
    identify_count: int = Field(ge=0, default=0)


class UserProfileResponse(BaseModel):
    id: str
    username: str
    email: str | None = None
    display_name: str
    has_avatar: bool = False
    created_at: datetime
    stats: UserStats = Field(default_factory=UserStats)


class UserProfileUpdateRequest(BaseModel):
    display_name: str | None = Field(default=None, max_length=64)


class AuthTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserProfileResponse


class UserPublicItem(BaseModel):
    id: str
    username: str
    display_name: str
    has_avatar: bool = False


class MemberAvatarItem(BaseModel):
    user_id: str
    display_name: str
    has_avatar: bool = False


class TeamMemberItem(BaseModel):
    user_id: str
    username: str
    display_name: str
    role: str
    joined_at: datetime
    has_avatar: bool = False


class TeamSummary(BaseModel):
    id: str
    name: str
    description: str
    owner_id: str
    member_count: int
    my_role: str
    has_avatar: bool = False
    created_at: datetime


class TeamDetail(TeamSummary):
    members: list[TeamMemberItem]


class TeamCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    description: str = Field(default="", max_length=500)


class TeamUpdateRequest(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=64)
    description: str | None = Field(default=None, max_length=500)


class TeamInviteRequest(BaseModel):
    query: str = Field(min_length=1, max_length=128)


class TeamInviteResponse(BaseModel):
    ok: bool = True
    invite_id: str
    mail_id: str
    invitee: UserPublicItem


class TeamListResponse(BaseModel):
    items: list[TeamSummary]


class MailItem(BaseModel):
    id: str
    mail_type: str
    title: str
    body: str
    payload: dict[str, Any] = Field(default_factory=dict)
    is_read: bool
    action_status: str | None = None
    sender: UserPublicItem | None = None
    created_at: datetime


class MailListResponse(BaseModel):
    items: list[MailItem]
    unread_count: int


class MailUnreadResponse(BaseModel):
    unread_count: int


class OkResponse(BaseModel):
    ok: bool = True


class MapIdentifyPoint(BaseModel):
    request_id: str
    label_main: str
    scene: str
    risk_level: int = Field(ge=0, le=3, default=0)
    summary: str = ""
    server_ts: datetime
    gps_lat: float
    gps_lng: float
    accuracy_m: float | None = None


class MapSosPoint(BaseModel):
    incident_id: str
    event_type: str
    device_id: str
    user_id: str | None = None
    display_name: str | None = None
    server_ts: datetime
    client_ts: datetime | None = None
    gps_lat: float
    gps_lng: float
    accuracy_m: float | None = None


class MapMemberPoint(BaseModel):
    user_id: str
    username: str
    display_name: str
    team_id: str | None = None
    server_ts: datetime
    client_ts: datetime | None = None
    gps_lat: float
    gps_lng: float
    accuracy_m: float | None = None
    gps_altitude: float | None = None
    baro_altitude: float | None = None


class MapLayersResponse(BaseModel):
    identify: list[MapIdentifyPoint]
    sos: list[MapSosPoint]
    members: list[MapMemberPoint]


class SensorPayloadMixin(BaseModel):
    """IMU + extended GPS (excludes satellite count and UTC)."""
    gps_speed: float | None = None
    gps_heading: float | None = Field(default=None, ge=0, le=360)
    gps_altitude: float | None = None
    accel_x: float | None = None
    accel_y: float | None = None
    accel_z: float | None = None
    gyro_x: float | None = None
    gyro_y: float | None = None
    gyro_z: float | None = None
    mag_x: float | None = None
    mag_y: float | None = None
    mag_z: float | None = None
    pitch: float | None = None
    roll: float | None = None
    imu_heading: float | None = Field(default=None, ge=0, le=360)
    baro_altitude: float | None = None


class LocationReportRequest(SensorPayloadMixin):
    gps_lat: float = Field(ge=-90, le=90)
    gps_lng: float = Field(ge=-180, le=180)
    accuracy_m: float | None = Field(default=None, ge=0)
    team_id: str | None = None
    client_ts: datetime | None = None


class LocationReportResponse(BaseModel):
    ok: bool = True
    server_ts: datetime


class MapTrackPoint(BaseModel):
    gps_lat: float
    gps_lng: float
    server_ts: datetime
    source: str = "sensor"
    gps_speed: float | None = None
    gps_altitude: float | None = None


class MapTrajectory(BaseModel):
    user_id: str
    username: str
    display_name: str
    points: list[MapTrackPoint]


class MapTrajectoriesResponse(BaseModel):
    items: list[MapTrajectory]


EnhanceMode = Literal["denoise", "enhance", "both"]
EnhanceStrength = Literal["light", "normal", "strong"]


class EnhanceRequest(BaseModel):
    mode: EnhanceMode = "both"
    strength: EnhanceStrength = "normal"


class EnhancePreviewResponse(BaseModel):
    preview_data_url: str
    mode: EnhanceMode
    strength: EnhanceStrength


class EnhanceApplyResponse(BaseModel):
    ok: bool = True
    has_original: bool = False
    mode: EnhanceMode
    strength: EnhanceStrength


class CameraBindRequest(BaseModel):
    device_id: str = Field(min_length=3, max_length=64)
    device_secret: str = Field(min_length=8, max_length=128)
    name: str | None = Field(default=None, max_length=64)


class CameraDeviceItem(BaseModel):
    device_id: str
    name: str
    device_type: str
    is_bound: bool
    bound_at: datetime | None = None
    created_at: datetime


class CameraBindResponse(BaseModel):
    ok: bool = True
    device: CameraDeviceItem


class CameraDeviceListResponse(BaseModel):
    items: list[CameraDeviceItem]


class CameraProvisionResponse(BaseModel):
    device_id: str
    device_secret: str
    bind_uri: str
    upload_url: str


class CameraUploadResponse(BaseModel):
    ok: bool = True
    request_id: str
    label_main: str
    risk_level: int = Field(ge=0, le=3)
    summary: str = ""
    latency_ms: int = Field(ge=0)


class ChatMessageItem(BaseModel):
    id: str
    team_id: str
    sender: UserPublicItem
    body: str
    created_at: datetime
    is_mine: bool = False


class ChatMessageCreateRequest(BaseModel):
    body: str = Field(min_length=1, max_length=2000)


class ChatMessagesResponse(BaseModel):
    items: list[ChatMessageItem]
    has_more: bool = False


class ChatConversationItem(BaseModel):
    team_id: str
    team_name: str
    member_count: int
    has_avatar: bool = False
    member_avatars: list[MemberAvatarItem] = Field(default_factory=list)
    last_message: ChatMessageItem | None = None
    unread_count: int = Field(ge=0, default=0)


class ChatConversationsResponse(BaseModel):
    items: list[ChatConversationItem]
    total_unread: int = Field(ge=0, default=0)


ReportJobStatus = Literal["pending", "aggregating", "identifying", "writing", "done", "failed", "cancelled"]


class ReportJobCreateRequest(BaseModel):
    team_id: str
    user_id: str
    date: str = Field(description="YYYY-MM-DD")
    report_type: Literal["safety", "travel"] = "safety"
    force_reidentify: bool = False


class ReportJobUpdateRequest(BaseModel):
    markdown: str = Field(min_length=1)


class ReportJobItem(BaseModel):
    id: str
    team_id: str
    team_name: str = ""
    subject_user_id: str
    subject_display_name: str = ""
    requested_by_user_id: str
    requested_by_display_name: str = ""
    report_date: str
    report_type: Literal["safety", "travel"] = "safety"
    status: ReportJobStatus
    phase_detail: dict[str, Any] = Field(default_factory=dict)
    timeline_json: dict[str, Any] | None = None
    markdown: str | None = None
    markdown_generated: str | None = None
    is_edited: bool = False
    edited_at: datetime | None = None
    created_at: datetime
    updated_at: datetime
    finished_at: datetime | None = None


class ReportJobListResponse(BaseModel):
    items: list[ReportJobItem]


class ModelStatusResponse(BaseModel):
    mode: Literal["vision", "report", "idle", "busy"]
    report_job_active: bool = False


class ReportPreviewResponse(BaseModel):
    report_date: str
    identify_count: int = 0
    sos_count: int = 0
    chat_messages_by_subject: int = 0
    has_data: bool = False
    suggested_report_date: str | None = None
    empty_hint: str | None = None


class CompanionStateResponse(BaseModel):
    name: str = "小欧"
    mood: str = "idle"
    level: int = 1
    energy: int = 100
    total_steps: int = 0
    message: str = "你好，我是小欧！"
    pose_json: dict[str, Any] = Field(default_factory=dict)
    updated_at: datetime | None = None


class CompanionStateUpdateRequest(BaseModel):
    mood: str | None = None
    level: int | None = Field(default=None, ge=1, le=99)
    energy: int | None = Field(default=None, ge=0, le=100)
    total_steps: int | None = Field(default=None, ge=0)
    message: str | None = Field(default=None, max_length=200)
    pose_json: dict[str, Any] | None = None


class MotionSessionCreateRequest(BaseModel):
    client_ts: datetime | None = None


class MotionSessionUpdateRequest(BaseModel):
    status: str | None = None
    steps: int | None = Field(default=None, ge=0)
    fall_count: int | None = Field(default=None, ge=0)
    pose_score: float | None = Field(default=None, ge=0.0, le=100.0)
    summary_json: dict[str, Any] | None = None
    ended_at: datetime | None = None


class MotionSnapshotItem(BaseModel):
    client_ts: datetime | None = None
    steps: int = 0
    activity: str = "idle"
    fall_detected: bool = False
    pose_keypoints: dict[str, Any] = Field(default_factory=dict)
    sensor_json: dict[str, Any] = Field(default_factory=dict)


class MotionSnapshotBatchRequest(BaseModel):
    items: list[MotionSnapshotItem] = Field(default_factory=list, max_length=100)


class MotionSessionItem(BaseModel):
    id: str
    status: str
    started_at: datetime
    ended_at: datetime | None = None
    steps: int = 0
    fall_count: int = 0
    pose_score: float = 0.0
    summary_json: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime
    updated_at: datetime


class MotionSessionListResponse(BaseModel):
    items: list[MotionSessionItem]
    total: int


class MotionSnapshotResponseItem(BaseModel):
    id: str
    session_id: str
    client_ts: datetime | None = None
    steps: int = 0
    activity: str = "idle"
    fall_detected: bool = False
    pose_keypoints: dict[str, Any] = Field(default_factory=dict)
    sensor_json: dict[str, Any] = Field(default_factory=dict)
    server_ts: datetime


class MotionSnapshotListResponse(BaseModel):
    items: list[MotionSnapshotResponseItem]
    total: int


class EnvironmentLatestResponse(BaseModel):
    server_ts: datetime
    gps_lat: float | None = None
    gps_lng: float | None = None
    accuracy_m: float | None = None
    gps_altitude: float | None = None
    baro_altitude: float | None = None
    temperature: float | None = None
    humidity: float | None = None
    gps_speed: float | None = None
    gps_heading: float | None = None
    imu_heading: float | None = None
    pitch: float | None = None
    roll: float | None = None
    accel_x: float | None = None
    accel_y: float | None = None
    accel_z: float | None = None
    gyro_x: float | None = None
    gyro_y: float | None = None
    gyro_z: float | None = None
    source: str = "none"
    label: str | None = None

