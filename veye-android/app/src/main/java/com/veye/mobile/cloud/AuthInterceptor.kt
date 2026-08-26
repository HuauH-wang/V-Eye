package com.veye.mobile.cloud

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val req = chain.request()
    val builder = req.newBuilder()
    val apiKey = CloudConfig.API_KEY
    if (apiKey.isNotBlank()) {
      builder.header("X-API-Key", apiKey)
    }
    val token = CloudConfig.ACCESS_TOKEN
    if (token.isNotBlank()) {
      builder.header("Authorization", "Bearer $token")
    }
    return chain.proceed(builder.build())
  }
}
