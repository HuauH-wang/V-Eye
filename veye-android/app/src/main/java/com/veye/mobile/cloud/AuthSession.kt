package com.veye.mobile.cloud

import android.content.Context
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.dto.LoginRequest
import com.veye.mobile.cloud.dto.ProfileUpdateRequest
import com.veye.mobile.cloud.dto.RegisterRequest
import com.veye.mobile.cloud.dto.UserProfileDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

object AuthSession {
  suspend fun login(context: Context, username: String, password: String): UserProfileDto {
    val res = AppServices.api().login(LoginRequest(username.trim(), password))
    CloudConfig.saveSession(
      context,
      accessToken = res.access_token,
      username = res.user.username,
      displayName = res.user.display_name,
    )
    return res.user
  }

  suspend fun register(
    context: Context,
    username: String,
    password: String,
    displayName: String?,
  ): UserProfileDto {
    val res = AppServices.api().register(
      RegisterRequest(
        username = username.trim(),
        password = password,
        display_name = displayName?.trim()?.ifBlank { null },
      ),
    )
    CloudConfig.saveSession(
      context,
      accessToken = res.access_token,
      username = res.user.username,
      displayName = res.user.display_name,
    )
    return res.user
  }

  fun logout(context: Context) {
    CloudConfig.clearSession(context)
    AppServices.invalidateApi()
  }

  suspend fun refreshMe(context: Context): UserProfileDto {
    val user = AppServices.api().me()
    CloudConfig.saveSession(
      context,
      accessToken = CloudConfig.ACCESS_TOKEN,
      username = user.username,
      displayName = user.display_name,
    )
    return user
  }

  suspend fun updateDisplayName(context: Context, displayName: String): UserProfileDto {
    val user = AppServices.api().updateProfile(ProfileUpdateRequest(displayName.trim()))
    CloudConfig.saveSession(
      context,
      accessToken = CloudConfig.ACCESS_TOKEN,
      username = user.username,
      displayName = user.display_name,
    )
    return user
  }

  suspend fun uploadAvatar(context: Context, bytes: ByteArray, filename: String): UserProfileDto {
    val part = MultipartBody.Part.createFormData(
      name = "avatar",
      filename = filename,
      body = bytes.toRequestBody("image/jpeg".toMediaType()),
    )
    val user = AppServices.api().uploadAvatar(part)
    CloudConfig.saveSession(
      context,
      accessToken = CloudConfig.ACCESS_TOKEN,
      username = user.username,
      displayName = user.display_name,
    )
    return user
  }

  suspend fun deleteAvatar(context: Context): UserProfileDto {
    val user = AppServices.api().deleteAvatar()
    CloudConfig.saveSession(
      context,
      accessToken = CloudConfig.ACCESS_TOKEN,
      username = user.username,
      displayName = user.display_name,
    )
    return user
  }
}
