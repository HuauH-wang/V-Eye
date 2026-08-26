package com.veye.mobile.cloud

import com.veye.mobile.cloud.dto.AuthTokenResponse
import com.veye.mobile.cloud.dto.BindDeviceRequest
import com.veye.mobile.cloud.dto.BindDeviceResponse
import com.veye.mobile.cloud.dto.CameraDeviceListResponse
import com.veye.mobile.cloud.dto.ChatConversationsResponse
import com.veye.mobile.cloud.dto.ChatMessageCreateRequest
import com.veye.mobile.cloud.dto.ChatMessageDto
import com.veye.mobile.cloud.dto.ChatMessagesResponse
import com.veye.mobile.cloud.dto.EnvironmentLatestDto
import com.veye.mobile.cloud.dto.EnhanceApplyResponseDto
import com.veye.mobile.cloud.dto.EnhancePreviewResponseDto
import com.veye.mobile.cloud.dto.EnhanceRequestDto
import com.veye.mobile.cloud.dto.MailItemDto
import com.veye.mobile.cloud.dto.ProfileUpdateRequest
import com.veye.mobile.cloud.dto.HealthResponse
import com.veye.mobile.cloud.dto.IdentifyRecordsResponse
import com.veye.mobile.cloud.dto.IdentifyResponse
import com.veye.mobile.cloud.dto.LocationReportRequestDto
import com.veye.mobile.cloud.dto.CompanionStateDto
import com.veye.mobile.cloud.dto.CompanionStateUpdateRequest
import com.veye.mobile.cloud.dto.MotionSessionCreateRequest
import com.veye.mobile.cloud.dto.MotionSessionDto
import com.veye.mobile.cloud.dto.MotionSessionListResponse
import com.veye.mobile.cloud.dto.MotionSnapshotBatchRequest
import com.veye.mobile.cloud.dto.MotionSnapshotListResponse
import com.veye.mobile.cloud.dto.MotionSessionUpdateRequest
import com.veye.mobile.cloud.dto.LocationReportResponseDto
import com.veye.mobile.cloud.dto.MapLayersResponseDto
import com.veye.mobile.cloud.dto.MapTrajectoriesResponseDto
import com.veye.mobile.cloud.dto.LoginRequest
import com.veye.mobile.cloud.dto.MailListResponse
import com.veye.mobile.cloud.dto.MailUnreadResponse
import com.veye.mobile.cloud.dto.OkResponse
import com.veye.mobile.cloud.dto.RegisterRequest
import com.veye.mobile.cloud.dto.SosRequest
import com.veye.mobile.cloud.dto.SosResponse
import com.veye.mobile.cloud.dto.ModelStatusResponse
import com.veye.mobile.cloud.dto.ReportJobCreateRequest
import com.veye.mobile.cloud.dto.ReportJobDto
import com.veye.mobile.cloud.dto.ReportJobListResponse
import com.veye.mobile.cloud.dto.ReportJobUpdateRequest
import com.veye.mobile.cloud.dto.ReportPreviewResponse
import com.veye.mobile.cloud.dto.SosRecordsResponse
import com.veye.mobile.cloud.dto.TeamUpdateRequest
import com.veye.mobile.cloud.dto.TeamCreateRequest
import com.veye.mobile.cloud.dto.TeamDetailDto
import com.veye.mobile.cloud.dto.TeamInviteRequest
import com.veye.mobile.cloud.dto.TeamListResponse
import com.veye.mobile.cloud.dto.UserProfileDto
import com.veye.mobile.cloud.dto.UserPublicDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudApi {
  @GET("health")
  suspend fun health(): HealthResponse

  @Multipart
  @POST("vision/identify")
  suspend fun identify(
    @Part image: MultipartBody.Part,
    @Part("scene") scene: RequestBody,
    @Part("lang") lang: RequestBody?,
    @Part("device_id") deviceId: RequestBody?,
    @Part("client_ts") clientTs: RequestBody?,
    @Part("gps_lat") gpsLat: RequestBody?,
    @Part("gps_lng") gpsLng: RequestBody?,
  ): IdentifyResponse

  @POST("sos")
  suspend fun sos(@Body body: SosRequest): SosResponse

  @GET("sos/records")
  suspend fun listSosRecords(
    @Query("team_id") teamId: String? = null,
    @Query("days") days: Int = 90,
    @Query("limit") limit: Int = 100,
  ): SosRecordsResponse

  @DELETE("sos/records/{incidentId}")
  suspend fun deleteSosRecord(@Path("incidentId") incidentId: String): OkResponse

  @GET("reports/model-status")
  suspend fun reportModelStatus(): ModelStatusResponse

  @GET("reports/preview")
  suspend fun reportPreview(
    @Query("team_id") teamId: String,
    @Query("user_id") userId: String,
    @Query("date") date: String,
  ): ReportPreviewResponse

  @POST("reports/jobs")
  suspend fun createReportJob(@Body body: ReportJobCreateRequest): ReportJobDto

  @GET("reports/jobs")
  suspend fun listReportJobs(
    @Query("team_id") teamId: String? = null,
    @Query("user_id") userId: String? = null,
    @Query("date") date: String? = null,
    @Query("status") status: String? = null,
    @Query("limit") limit: Int = 30,
  ): ReportJobListResponse

  @GET("reports/jobs/{jobId}")
  suspend fun getReportJob(@Path("jobId") jobId: String): ReportJobDto

  @PATCH("reports/jobs/{jobId}")
  suspend fun updateReportJob(@Path("jobId") jobId: String, @Body body: ReportJobUpdateRequest): ReportJobDto

  @DELETE("reports/jobs/{jobId}")
  suspend fun deleteReportJob(@Path("jobId") jobId: String): OkResponse

  @POST("auth/register")
  suspend fun register(@Body body: RegisterRequest): AuthTokenResponse

  @POST("auth/login")
  suspend fun login(@Body body: LoginRequest): AuthTokenResponse

  @GET("auth/me")
  suspend fun me(): UserProfileDto

  @PATCH("auth/me")
  suspend fun updateProfile(@Body body: ProfileUpdateRequest): UserProfileDto

  @Multipart
  @POST("auth/me/avatar")
  suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): UserProfileDto

  @DELETE("auth/me/avatar")
  suspend fun deleteAvatar(): UserProfileDto

  @GET("vision/records")
  suspend fun listRecords(
    @Query("limit") limit: Int = 100,
    @Query("offset") offset: Int = 0,
    @Query("sort") sort: String = "time_desc",
  ): IdentifyRecordsResponse

  @DELETE("vision/records/{id}")
  suspend fun deleteRecord(@Path("id") id: String): OkResponse

  @POST("vision/records/{id}/enhance/preview")
  suspend fun previewEnhance(
    @Path("id") id: String,
    @Body body: EnhanceRequestDto,
  ): EnhancePreviewResponseDto

  @POST("vision/records/{id}/enhance/apply")
  suspend fun applyEnhance(
    @Path("id") id: String,
    @Body body: EnhanceRequestDto,
  ): EnhanceApplyResponseDto

  @POST("vision/records/{id}/enhance/restore")
  suspend fun restoreRecord(@Path("id") id: String): OkResponse

  @GET("teams")
  suspend fun listTeams(): TeamListResponse

  @POST("teams")
  suspend fun createTeam(@Body body: TeamCreateRequest): TeamDetailDto

  @GET("teams/{id}")
  suspend fun getTeam(@Path("id") id: String): TeamDetailDto

  @PATCH("teams/{id}")
  suspend fun updateTeam(@Path("id") id: String, @Body body: TeamUpdateRequest): TeamDetailDto

  @DELETE("teams/{id}")
  suspend fun disbandTeam(@Path("id") id: String): OkResponse

  @Multipart
  @POST("teams/{id}/avatar")
  suspend fun uploadTeamAvatar(@Path("id") id: String, @Part avatar: MultipartBody.Part): TeamDetailDto

  @DELETE("teams/{id}/avatar")
  suspend fun deleteTeamAvatar(@Path("id") id: String): TeamDetailDto

  @POST("teams/{id}/invite")
  suspend fun inviteMember(
    @Path("id") id: String,
    @Body body: TeamInviteRequest,
  ): OkResponse

  @POST("teams/{id}/members/{memberId}/kick")
  suspend fun kickMember(
    @Path("id") id: String,
    @Path("memberId") memberId: String,
  ): OkResponse

  @POST("teams/{id}/leave")
  suspend fun leaveTeam(@Path("id") id: String): OkResponse

  @GET("teams/users/lookup")
  suspend fun lookupUsers(@Query("q") query: String): List<UserPublicDto>

  @GET("mail")
  suspend fun listMail(@Query("unread_only") unreadOnly: Boolean = false): MailListResponse

  @GET("mail/unread-count")
  suspend fun unreadMailCount(): MailUnreadResponse

  @PATCH("mail/{id}/read")
  suspend fun markMailRead(@Path("id") id: String): MailItemDto

  @POST("mail/{id}/accept")
  suspend fun acceptMail(@Path("id") id: String): OkResponse

  @POST("mail/{id}/decline")
  suspend fun declineMail(@Path("id") id: String): OkResponse

  @DELETE("mail/{id}")
  suspend fun deleteMail(@Path("id") id: String): OkResponse

  @POST("devices/bind")
  suspend fun bindDevice(@Body body: BindDeviceRequest): BindDeviceResponse

  @GET("devices")
  suspend fun listDevices(): CameraDeviceListResponse

  @DELETE("devices/{deviceId}")
  suspend fun unbindDevice(@Path("deviceId") deviceId: String): OkResponse

  @GET("teams/chat/conversations")
  suspend fun listChatConversations(): ChatConversationsResponse

  @GET("teams/{id}/messages")
  suspend fun listChatMessages(
    @Path("id") teamId: String,
    @Query("limit") limit: Int = 50,
    @Query("since") since: String? = null,
    @Query("before") before: String? = null,
  ): ChatMessagesResponse

  @POST("teams/{id}/messages")
  suspend fun sendChatMessage(
    @Path("id") teamId: String,
    @Body body: ChatMessageCreateRequest,
  ): ChatMessageDto

  @POST("teams/{id}/messages/read")
  suspend fun markChatRead(@Path("id") teamId: String): OkResponse

  @GET("map/layers")
  suspend fun mapLayers(
    @Query("team_id") teamId: String? = null,
    @Query("days") days: Int = 30,
  ): MapLayersResponseDto

  @GET("map/trajectories")
  suspend fun mapTrajectories(
    @Query("team_id") teamId: String,
    @Query("days") days: Int = 1,
  ): MapTrajectoriesResponseDto

  @POST("map/location")
  suspend fun reportMapLocation(@Body body: LocationReportRequestDto): LocationReportResponseDto

  @GET("companion/xiaoou")
  suspend fun getCompanionXiaoOu(): CompanionStateDto

  @PATCH("companion/xiaoou")
  suspend fun patchCompanionXiaoOu(@Body body: CompanionStateUpdateRequest): CompanionStateDto

  @POST("motion/sessions")
  suspend fun startMotionSession(@Body body: MotionSessionCreateRequest = MotionSessionCreateRequest()): MotionSessionDto

  @GET("motion/sessions")
  suspend fun listMotionSessions(@Query("limit") limit: Int = 30): MotionSessionListResponse

  @PATCH("motion/sessions/{sessionId}")
  suspend fun updateMotionSession(
    @Path("sessionId") sessionId: String,
    @Body body: MotionSessionUpdateRequest,
  ): MotionSessionDto

  @POST("motion/sessions/{sessionId}/snapshots")
  suspend fun uploadMotionSnapshots(
    @Path("sessionId") sessionId: String,
    @Body body: MotionSnapshotBatchRequest,
  ): MotionSnapshotListResponse

  @GET("environment/latest")
  suspend fun environmentLatest(): EnvironmentLatestDto
}
