package com.veye.mobile.cloud.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
  val status: String,
  val model: String,
  val vllm: Map<String, String>?,
)

@JsonClass(generateAdapter = true)
data class CandidateDto(
  val label: String,
  val confidence: Double,
)

data class IdentifyDetailsDto(
  val botanical_traits: List<String> = emptyList(),
  val toxicity_evidence: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class IdentifyResponse(
  val request_id: String,
  val label_main: String,
  val confidence: Double,
  val candidates: List<CandidateDto> = emptyList(),
  val risk_level: Int,
  val risk_tags: List<String> = emptyList(),
  val summary: String = "",
  val advice: String = "",
  val details: IdentifyDetailsDto = IdentifyDetailsDto(),
  val latency_ms: Long,
)

@JsonClass(generateAdapter = true)
data class SosRequest(
  val device_id: String,
  val event_type: String,
  val gps_lat: Double? = null,
  val gps_lng: Double? = null,
  val accuracy_m: Double? = null,
  val client_ts: String? = null,
)

@JsonClass(generateAdapter = true)
data class SosResponse(
  val ok: Boolean,
  val incident_id: String,
)

@JsonClass(generateAdapter = true)
data class OkResponse(val ok: Boolean = true)

@JsonClass(generateAdapter = true)
data class LoginRequest(val username: String, val password: String)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
  val username: String,
  val password: String,
  val display_name: String? = null,
  val email: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserStatsDto(val identify_count: Int = 0)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
  val id: String,
  val username: String,
  val email: String? = null,
  val display_name: String,
  val has_avatar: Boolean = false,
  val created_at: String,
  val stats: UserStatsDto = UserStatsDto(),
)

@JsonClass(generateAdapter = true)
data class AuthTokenResponse(
  val access_token: String,
  val token_type: String = "bearer",
  val user: UserProfileDto,
)

@JsonClass(generateAdapter = true)
data class IdentifyRecordItemDto(
  val request_id: String,
  val device_id: String? = null,
  val scene: String,
  val label_main: String,
  val confidence: Double,
  val risk_level: Int,
  val summary: String = "",
  val server_ts: String,
  val has_original: Boolean = false,
  val has_image: Boolean = true,
  val last_enhance_mode: String? = null,
  val gps_lat: Double? = null,
  val gps_lng: Double? = null,
  val accuracy_m: Double? = null,
)

@JsonClass(generateAdapter = true)
data class IdentifyRecordsResponse(
  val items: List<IdentifyRecordItemDto>,
  val total: Int,
  val matched: Int? = null,
)

@JsonClass(generateAdapter = true)
data class EnhanceRequestDto(
  val mode: String = "both",
  val strength: String = "normal",
)

@JsonClass(generateAdapter = true)
data class EnhancePreviewResponseDto(
  val preview_data_url: String,
  val mode: String,
  val strength: String,
)

@JsonClass(generateAdapter = true)
data class EnhanceApplyResponseDto(
  val has_original: Boolean = false,
  val mode: String = "",
  val strength: String = "",
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(val display_name: String? = null)

@JsonClass(generateAdapter = true)
data class MemberAvatarItemDto(
  val user_id: String,
  val display_name: String,
  val has_avatar: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TeamMemberDto(
  val user_id: String,
  val username: String,
  val display_name: String,
  val role: String,
  val joined_at: String,
  val has_avatar: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TeamSummaryDto(
  val id: String,
  val name: String,
  val description: String,
  val owner_id: String,
  val member_count: Int,
  val my_role: String,
  val has_avatar: Boolean = false,
  val created_at: String,
)

@JsonClass(generateAdapter = true)
data class TeamDetailDto(
  val id: String,
  val name: String,
  val description: String,
  val owner_id: String,
  val member_count: Int,
  val my_role: String,
  val has_avatar: Boolean = false,
  val created_at: String,
  val members: List<TeamMemberDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TeamListResponse(val items: List<TeamSummaryDto>)

@JsonClass(generateAdapter = true)
data class TeamCreateRequest(val name: String, val description: String = "")

@JsonClass(generateAdapter = true)
data class TeamUpdateRequest(val name: String? = null, val description: String? = null)

@JsonClass(generateAdapter = true)
data class TeamInviteRequest(val query: String)

@JsonClass(generateAdapter = true)
data class UserPublicDto(
  val id: String,
  val username: String,
  val display_name: String,
  val has_avatar: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class MailItemDto(
  val id: String,
  val mail_type: String,
  val title: String,
  val body: String,
  val payload: Map<String, String> = emptyMap(),
  val is_read: Boolean,
  val action_status: String? = null,
  val sender: UserPublicDto? = null,
  val created_at: String,
)

@JsonClass(generateAdapter = true)
data class MailListResponse(
  val items: List<MailItemDto>,
  val unread_count: Int,
)

@JsonClass(generateAdapter = true)
data class MailUnreadResponse(val unread_count: Int)

@JsonClass(generateAdapter = true)
data class BindDeviceRequest(
  val device_id: String,
  val device_secret: String,
  val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class CameraDeviceDto(
  val device_id: String,
  val name: String,
  val device_type: String,
  val is_bound: Boolean,
  val bound_at: String? = null,
  val created_at: String,
)

@JsonClass(generateAdapter = true)
data class BindDeviceResponse(
  val ok: Boolean = true,
  val device: CameraDeviceDto,
)

@JsonClass(generateAdapter = true)
data class CameraDeviceListResponse(val items: List<CameraDeviceDto>)

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
  val id: String,
  val team_id: String,
  val sender: UserPublicDto,
  val body: String,
  val created_at: String,
  val is_mine: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ChatMessagesResponse(
  val items: List<ChatMessageDto>,
  val has_more: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ChatMessageCreateRequest(val body: String)

@JsonClass(generateAdapter = true)
data class ChatConversationDto(
  val team_id: String,
  val team_name: String,
  val member_count: Int,
  val has_avatar: Boolean = false,
  val member_avatars: List<MemberAvatarItemDto> = emptyList(),
  val last_message: ChatMessageDto? = null,
  val unread_count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ChatConversationsResponse(
  val items: List<ChatConversationDto>,
  val total_unread: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SosRecordItemDto(
  val incident_id: String,
  val event_type: String,
  val device_id: String,
  val user_id: String? = null,
  val display_name: String? = null,
  val username: String? = null,
  val server_ts: String,
  val client_ts: String? = null,
  val gps_lat: Double? = null,
  val gps_lng: Double? = null,
  val accuracy_m: Double? = null,
  val has_gps: Boolean = false,
  val attributed: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class SosRecordsResponse(
  val items: List<SosRecordItemDto>,
  val total: Int,
)

@JsonClass(generateAdapter = true)
data class ReportPhaseDetailDto(
  val error: String? = null,
  val missing_total: Int? = null,
  val missing_done: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ReportJobCreateRequest(
  val team_id: String,
  val user_id: String,
  val date: String,
  val report_type: String = "safety",
  val force_reidentify: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ReportJobUpdateRequest(val markdown: String)

@JsonClass(generateAdapter = true)
data class ReportJobDto(
  val id: String,
  val team_id: String,
  val team_name: String = "",
  val subject_user_id: String,
  val subject_display_name: String = "",
  val requested_by_user_id: String = "",
  val requested_by_display_name: String = "",
  val report_date: String,
  val report_type: String = "safety",
  val status: String,
  val phase_detail: ReportPhaseDetailDto? = null,
  val markdown: String? = null,
  val markdown_generated: String? = null,
  val is_edited: Boolean = false,
  val edited_at: String? = null,
  val created_at: String,
  val updated_at: String,
  val finished_at: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReportJobListResponse(val items: List<ReportJobDto>)

@JsonClass(generateAdapter = true)
data class ReportPreviewResponse(
  val report_date: String,
  val identify_count: Int = 0,
  val sos_count: Int = 0,
  val chat_messages_by_subject: Int = 0,
  val has_data: Boolean = false,
  val suggested_report_date: String? = null,
  val empty_hint: String? = null,
)

@JsonClass(generateAdapter = true)
data class ModelStatusResponse(
  val mode: String,
  val report_job_active: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class MapIdentifyPointDto(
  val request_id: String,
  val label_main: String,
  val scene: String,
  val risk_level: Int = 0,
  val summary: String = "",
  val server_ts: String,
  val gps_lat: Double,
  val gps_lng: Double,
  val accuracy_m: Double? = null,
)

@JsonClass(generateAdapter = true)
data class MapSosPointDto(
  val incident_id: String,
  val event_type: String,
  val device_id: String,
  val user_id: String? = null,
  val display_name: String? = null,
  val server_ts: String,
  val client_ts: String? = null,
  val gps_lat: Double,
  val gps_lng: Double,
  val accuracy_m: Double? = null,
)

@JsonClass(generateAdapter = true)
data class MapMemberPointDto(
  val user_id: String,
  val username: String,
  val display_name: String,
  val team_id: String? = null,
  val server_ts: String,
  val client_ts: String? = null,
  val gps_lat: Double,
  val gps_lng: Double,
  val accuracy_m: Double? = null,
)

@JsonClass(generateAdapter = true)
data class MapLayersResponseDto(
  val identify: List<MapIdentifyPointDto> = emptyList(),
  val sos: List<MapSosPointDto> = emptyList(),
  val members: List<MapMemberPointDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MapTrackPointDto(
  val gps_lat: Double,
  val gps_lng: Double,
  val server_ts: String,
  val source: String = "sensor",
  val gps_speed: Double? = null,
  val gps_altitude: Double? = null,
)

@JsonClass(generateAdapter = true)
data class MapTrajectoryDto(
  val user_id: String,
  val username: String,
  val display_name: String,
  val points: List<MapTrackPointDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MapTrajectoriesResponseDto(
  val items: List<MapTrajectoryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LocationReportRequestDto(
  val gps_lat: Double,
  val gps_lng: Double,
  val accuracy_m: Double? = null,
  val team_id: String? = null,
  val client_ts: String? = null,
)

@JsonClass(generateAdapter = true)
data class LocationReportResponseDto(
  val ok: Boolean = true,
  val server_ts: String,
)

@JsonClass(generateAdapter = true)
data class CompanionStateDto(
  val name: String = "小欧",
  val mood: String = "idle",
  val level: Int = 1,
  val energy: Int = 100,
  val total_steps: Int = 0,
  val message: String = "你好，我是小欧！",
  val pose_json: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
  val updated_at: String? = null,
)

@JsonClass(generateAdapter = true)
data class CompanionStateUpdateRequest(
  val mood: String? = null,
  val level: Int? = null,
  val energy: Int? = null,
  val total_steps: Int? = null,
  val message: String? = null,
  val pose_json: Map<String, @JvmSuppressWildcards Any>? = null,
)

@JsonClass(generateAdapter = true)
data class MotionSessionCreateRequest(
  val client_ts: String? = null,
)

@JsonClass(generateAdapter = true)
data class MotionSessionUpdateRequest(
  val status: String? = null,
  val steps: Int? = null,
  val fall_count: Int? = null,
  val pose_score: Float? = null,
  val summary_json: Map<String, @JvmSuppressWildcards Any>? = null,
  val ended_at: String? = null,
)

@JsonClass(generateAdapter = true)
data class MotionSnapshotItem(
  val client_ts: String? = null,
  val steps: Int = 0,
  val activity: String = "idle",
  val fall_detected: Boolean = false,
  val pose_keypoints: Map<String, @JvmSuppressWildcards Map<String, Float>> = emptyMap(),
  val sensor_json: Map<String, @JvmSuppressWildcards Float> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class MotionSnapshotBatchRequest(
  val items: List<MotionSnapshotItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MotionSessionDto(
  val id: String,
  val status: String,
  val started_at: String,
  val ended_at: String? = null,
  val steps: Int = 0,
  val fall_count: Int = 0,
  val pose_score: Float = 0f,
  val summary_json: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
  val created_at: String,
  val updated_at: String,
)

@JsonClass(generateAdapter = true)
data class MotionSessionListResponse(
  val items: List<MotionSessionDto>,
  val total: Int,
)

@JsonClass(generateAdapter = true)
data class MotionSnapshotResponseItem(
  val id: String,
  val session_id: String,
  val client_ts: String? = null,
  val steps: Int = 0,
  val activity: String = "idle",
  val fall_detected: Boolean = false,
  val pose_keypoints: Map<String, @JvmSuppressWildcards Map<String, Float>> = emptyMap(),
  val sensor_json: Map<String, @JvmSuppressWildcards Float> = emptyMap(),
  val server_ts: String,
)

@JsonClass(generateAdapter = true)
data class MotionSnapshotListResponse(
  val items: List<MotionSnapshotResponseItem> = emptyList(),
  val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class EnvironmentLatestDto(
  val server_ts: String,
  val gps_lat: Double? = null,
  val gps_lng: Double? = null,
  val accuracy_m: Double? = null,
  val gps_altitude: Double? = null,
  val baro_altitude: Double? = null,
  val temperature: Double? = null,
  val humidity: Double? = null,
  val gps_speed: Double? = null,
  val gps_heading: Double? = null,
  val imu_heading: Double? = null,
  val pitch: Double? = null,
  val roll: Double? = null,
  val accel_x: Double? = null,
  val accel_y: Double? = null,
  val accel_z: Double? = null,
  val gyro_x: Double? = null,
  val gyro_y: Double? = null,
  val gyro_z: Double? = null,
  val source: String = "none",
  val label: String? = null,
)
