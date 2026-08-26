plugins {
  id("com.android.application") version "8.6.1"
  id("org.jetbrains.kotlin.android") version "2.0.21"
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
  id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

import java.util.Properties

val versionProps = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = versionProps.getProperty("APP_VERSION_NAME", "0.3.4")
val appVersionCode = versionProps.getProperty("APP_VERSION_CODE", "8").toInt()

val localProps = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) {
    file.inputStream().use { load(it) }
  }
}

fun localProp(name: String): String? =
  localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFile = localProp("RELEASE_STORE_FILE")?.let { rootProject.file(it) }
val releaseStorePassword = localProp("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProp("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProp("RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
  releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
  namespace = "com.veye.mobile"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.veye.mobile"
    minSdk = 26
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersionName

    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a")
    }

    manifestPlaceholders["AMAP_API_KEY"] =
      localProp("AMAP_API_KEY_RELEASE") ?: localProp("AMAP_API_KEY_DEBUG") ?: ""

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }

    val amapKey = when {
      !localProp("AMAP_API_KEY_RELEASE").isNullOrBlank() -> localProp("AMAP_API_KEY_RELEASE")
      !localProp("AMAP_API_KEY_DEBUG").isNullOrBlank() -> localProp("AMAP_API_KEY_DEBUG")
      else -> ""
    }
    buildConfigField("String", "AMAP_API_KEY", "\"${amapKey ?: ""}\"")
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseStoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      val debugAmap = localProp("AMAP_API_KEY_DEBUG") ?: localProp("AMAP_API_KEY_RELEASE").orEmpty()
      buildConfigField("String", "AMAP_API_KEY", "\"$debugAmap\"")
    }
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
      val releaseAmap = localProp("AMAP_API_KEY_RELEASE") ?: localProp("AMAP_API_KEY_DEBUG").orEmpty()
      buildConfigField("String", "AMAP_API_KEY", "\"$releaseAmap\"")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.activity:activity-compose:1.10.0")

  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.material:material-icons-extended")

  implementation("androidx.navigation:navigation-compose:2.8.6")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")

  implementation("androidx.datastore:datastore-preferences:1.1.2")

  implementation("io.coil-kt:coil-compose:2.7.0")

  implementation("io.noties.markwon:core:4.6.2")
  implementation("io.noties.markwon:image-coil:4.6.2")
  implementation("io.noties.markwon:ext-strikethrough:4.6.2")
  implementation("io.noties.markwon:ext-tables:4.6.2")
  implementation("io.noties.markwon:linkify:4.6.2")

  implementation("androidx.work:work-runtime-ktx:2.10.0")

  implementation("com.google.accompanist:accompanist-permissions:0.35.0-alpha")

  implementation("com.google.mlkit:barcode-scanning:17.3.0")
  implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
  implementation("androidx.camera:camera-camera2:1.4.1")
  implementation("androidx.camera:camera-lifecycle:1.4.1")
  implementation("androidx.camera:camera-view:1.4.1")

  implementation("com.amap.api:3dmap-location-search:10.1.200_loc6.4.9_sea9.7.4")
}

