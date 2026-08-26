package com.veye.mobile

import android.app.Application
import coil.Coil
import com.amap.api.maps.MapsInitializer
import com.veye.mobile.cloud.CloudConfig

class VeyeApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    CloudConfig.initialize(this)
    Coil.setImageLoader(AppServices.imageLoader(this))
    if (BuildConfig.AMAP_API_KEY.isNotBlank()) {
      MapsInitializer.updatePrivacyShow(this, true, true)
      MapsInitializer.updatePrivacyAgree(this, true)
      MapsInitializer.initialize(this)
    }
  }
}
