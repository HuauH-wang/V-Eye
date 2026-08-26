package com.veye.mobile.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.veye.mobile.ui.screens.MapViewModel
import com.veye.mobile.ui.theme.UiSpace
import com.veye.mobile.ui.theme.VeyeColors

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapControlsSheet(
  visible: Boolean,
  onDismiss: () -> Unit,
  navController: NavHostController,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  if (!visible) return

  val mainEntry = navController.currentBackStackEntry
    ?.takeIf { it.destination.route == "main" }
    ?: return

  val vm: MapViewModel = viewModel(mainEntry)
  val layers by vm.layers.collectAsState()
  val trajectories by vm.trajectories.collectAsState()
  val teams by vm.teams.collectAsState()
  val selectedTeamLabel by vm.selectedTeamLabel.collectAsState()
  val loading by vm.loading.collectAsState()
  val baseLayer by vm.baseLayer.collectAsState()
  val visibility by vm.visibility.collectAsState()
  val context = LocalContext.current
  var teamExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(visible) {
    if (visible) vm.refreshTeams()
  }

  val identifyCount = layers?.identify?.size ?: 0
  val sosCount = layers?.sos?.size ?: 0
  val memberCount = layers?.members?.size ?: 0
  val trajCount = trajectories.size

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = VeyeColors.Bg,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = UiSpace.lg)
        .padding(bottom = UiSpace.xl),
      verticalArrangement = Arrangement.spacedBy(UiSpace.md),
    ) {
      Text(
        text = "地图设置",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VeyeColors.Primary,
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiSpace.xs),
      ) {
        FilterChip(
          selected = baseLayer == MapBaseLayer.Street,
          onClick = { vm.setBaseLayer(MapBaseLayer.Street) },
          label = { Text("街道") },
        )
        FilterChip(
          selected = baseLayer == MapBaseLayer.Satellite,
          onClick = { vm.setBaseLayer(MapBaseLayer.Satellite) },
          label = { Text("卫星") },
        )
        FilterChip(
          selected = visibility.identify,
          onClick = { vm.setVisibility(visibility.copy(identify = !visibility.identify)) },
          label = { Text("识别 $identifyCount") },
        )
        FilterChip(
          selected = visibility.sos,
          onClick = { vm.setVisibility(visibility.copy(sos = !visibility.sos)) },
          label = { Text("SOS $sosCount") },
        )
        FilterChip(
          selected = visibility.members,
          onClick = { vm.setVisibility(visibility.copy(members = !visibility.members)) },
          label = { Text("队员 $memberCount") },
        )
        FilterChip(
          selected = visibility.trajectories,
          onClick = { vm.setVisibility(visibility.copy(trajectories = !visibility.trajectories)) },
          label = { Text("轨迹 $trajCount") },
        )
      }

      ExposedDropdownMenuBox(expanded = teamExpanded, onExpandedChange = { teamExpanded = it }) {
        OutlinedTextField(
          value = selectedTeamLabel,
          onValueChange = {},
          readOnly = true,
          modifier = Modifier.fillMaxWidth().menuAnchor(),
          label = { Text("小队") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(teamExpanded) },
        )
        ExposedDropdownMenu(expanded = teamExpanded, onDismissRequest = { teamExpanded = false }) {
          DropdownMenuItem(
            text = { Text("仅本人 SOS") },
            onClick = { vm.selectTeam(null); teamExpanded = false },
          )
          teams.forEach { team ->
            DropdownMenuItem(
              text = { Text(team.name) },
              onClick = { vm.selectTeam(team.id); teamExpanded = false },
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.sm)) {
        Button(
          onClick = { vm.refreshLayers() },
          enabled = !loading,
          modifier = Modifier.weight(1f),
        ) { Text(if (loading) "刷新中…" else "刷新图层") }
        TextButton(
          onClick = {
            val lm = context.getSystemService(LocationManager::class.java)
            val hasPerm = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return@TextButton
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
              ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
              vm.reportLocation(loc.latitude, loc.longitude, loc.accuracy)
            }
          },
          modifier = Modifier.weight(1f),
        ) { Text("上报位置") }
      }
    }
  }
}
