package com.veye.mobile.ui.components

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.veye.mobile.cloud.dto.MapLayersResponseDto
import com.veye.mobile.cloud.dto.MapTrajectoryDto
import com.veye.mobile.util.Gcj02

data class MapLayerVisibility(
  val identify: Boolean = true,
  val sos: Boolean = true,
  val members: Boolean = true,
  val trajectories: Boolean = true,
)

enum class MapBaseLayer { Street, Satellite }

private val DEFAULT_CENTER = LatLng(30.5928, 114.3055)
private val TRAJECTORY_COLORS = intArrayOf(
  Color.parseColor("#1d3557"),
  Color.parseColor("#e63946"),
  Color.parseColor("#2a9d8f"),
  Color.parseColor("#f4a261"),
  Color.parseColor("#7209b7"),
  Color.parseColor("#457b9d"),
)

private fun toGcj(lat: Double, lng: Double): LatLng {
  val (gLat, gLng) = Gcj02.wgs84ToGcj02(lat, lng)
  return LatLng(gLat, gLng)
}

@Composable
fun ObservatoryMapView(
  layers: MapLayersResponseDto?,
  trajectories: List<MapTrajectoryDto>,
  baseLayer: MapBaseLayer,
  visibility: MapLayerVisibility,
  modifier: Modifier = Modifier,
  focusRequestId: String? = null,
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val mapView = remember { MapView(context) }
  val aMapHolder = remember { arrayOfNulls<AMap>(1) }

  DisposableEffect(lifecycle, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      mapView.onDestroy()
    }
  }

  AndroidView(
    modifier = modifier,
    factory = {
      mapView.apply {
        map?.let { aMap ->
          aMapHolder[0] = aMap
          aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11f))
          aMap.uiSettings.isZoomControlsEnabled = true
          aMap.uiSettings.isScaleControlsEnabled = true
        }
      }
    },
    update = { view ->
      val aMap = view.map ?: return@AndroidView
      aMapHolder[0] = aMap
      aMap.mapType = when (baseLayer) {
        MapBaseLayer.Street -> AMap.MAP_TYPE_NORMAL
        MapBaseLayer.Satellite -> AMap.MAP_TYPE_SATELLITE
      }
      aMap.clear()

      val boundsBuilder = LatLngBounds.builder()
      var hasBounds = false
      var focusLatLng: LatLng? = null

      fun track(latLng: LatLng) {
        boundsBuilder.include(latLng)
        hasBounds = true
      }

      layers?.let { data ->
        if (visibility.identify) {
          data.identify.forEach { p ->
            val latLng = toGcj(p.gps_lat, p.gps_lng)
            if (p.request_id == focusRequestId) focusLatLng = latLng
            track(latLng)
            aMap.addMarker(
              MarkerOptions()
                .position(latLng)
                .title(p.label_main)
                .snippet("${p.scene} · 风险${p.risk_level}\n${p.summary.take(80)}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
            )
            p.accuracy_m?.takeIf { it > 0 }?.let { acc ->
              aMap.addCircle(
                CircleOptions()
                  .center(latLng)
                  .radius(acc)
                  .strokeColor(Color.argb(80, 64, 145, 108))
                  .fillColor(Color.argb(30, 64, 145, 108)),
              )
            }
          }
        }
        if (visibility.sos) {
          data.sos.forEach { p ->
            val latLng = toGcj(p.gps_lat, p.gps_lng)
            track(latLng)
            aMap.addMarker(
              MarkerOptions()
                .position(latLng)
                .title("SOS · ${p.event_type}")
                .snippet("${p.display_name ?: p.device_id}\n${p.server_ts}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
            )
          }
        }
        if (visibility.members) {
          data.members.forEach { p ->
            val latLng = toGcj(p.gps_lat, p.gps_lng)
            track(latLng)
            aMap.addMarker(
              MarkerOptions()
                .position(latLng)
                .title(p.display_name)
                .snippet("@${p.username}\n${p.server_ts}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
            )
          }
        }
      }

      if (visibility.trajectories) {
        trajectories.forEachIndexed { index, traj ->
          if (traj.points.size < 2) return@forEachIndexed
          val points = traj.points.map { toGcj(it.gps_lat, it.gps_lng) }
          points.forEach { track(it) }
          aMap.addPolyline(
            PolylineOptions()
              .addAll(points)
              .color(TRAJECTORY_COLORS[index % TRAJECTORY_COLORS.size])
              .width(8f),
          )
        }
      }

      when {
        focusLatLng != null -> aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(focusLatLng, 14f))
        hasBounds -> runCatching {
          aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
        }
      }
    },
  )
}
