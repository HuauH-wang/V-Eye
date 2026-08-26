package com.veye.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.amap.api.maps.model.LatLng

private val DEFAULT_CENTER = LatLng(30.5928, 114.3055)

@Composable
fun AmapView(
  modifier: Modifier = Modifier,
  onMapReady: (AMap) -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val mapView = remember { MapView(context) }

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
          aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 12f))
          aMap.uiSettings.isZoomControlsEnabled = true
          onMapReady(aMap)
        }
      }
    },
    update = { view ->
      view.map?.let(onMapReady)
    },
  )
}
