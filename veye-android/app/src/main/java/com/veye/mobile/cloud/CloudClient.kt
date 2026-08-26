package com.veye.mobile.cloud

import com.veye.mobile.cloud.dto.IdentifyDetailsJsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object CloudClient {
  private val moshi: Moshi = Moshi.Builder()
    .add(IdentifyDetailsJsonAdapter)
    .add(KotlinJsonAdapterFactory())
    .build()

  fun okhttp(): OkHttpClient {
    val logging = HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }
    return OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .addInterceptor(AuthInterceptor())
      .addInterceptor(logging)
      .build()
  }

  fun create(): CloudApi {
    return Retrofit.Builder()
      .baseUrl(CloudConfig.BASE_URL)
      .client(okhttp())
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(CloudApi::class.java)
  }

  fun recordImageUrl(requestId: String): String =
    "${CloudConfig.BASE_URL}vision/records/$requestId/image"

  fun recordOriginalImageUrl(requestId: String): String =
    "${CloudConfig.BASE_URL}vision/records/$requestId/image/original"

  fun avatarUrl(): String = "${CloudConfig.BASE_URL}auth/me/avatar"

  fun teamAvatarUrl(teamId: String): String = "${CloudConfig.BASE_URL}teams/$teamId/avatar"

  fun memberAvatarUrl(teamId: String, userId: String): String =
    "${CloudConfig.BASE_URL}teams/$teamId/members/$userId/avatar"
}
