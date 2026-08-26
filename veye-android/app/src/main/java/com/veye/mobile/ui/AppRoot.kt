package com.veye.mobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.veye.mobile.BuildConfig
import com.veye.mobile.cloud.CloudAuthState
import com.veye.mobile.cloud.CloudConnectionState
import com.veye.mobile.cloud.ConnectionStatus
import com.veye.mobile.ui.companion.CompanionViewModel
import com.veye.mobile.ui.components.AppTopBar
import com.veye.mobile.ui.components.CloudTickerBar
import com.veye.mobile.ui.components.CompanionDetailSheet
import com.veye.mobile.ui.components.DraggableCompanionBubble
import com.veye.mobile.ui.components.LibraryFilterSheet
import com.veye.mobile.ui.components.MapControlsSheet
import com.veye.mobile.ui.components.MotionSettingsSheet
import com.veye.mobile.ui.components.NatureBackground
import com.veye.mobile.ui.components.ProfileMenuSheet
import com.veye.mobile.ui.screens.MapViewModel
import com.veye.mobile.ui.screens.AuthScreen
import com.veye.mobile.ui.screens.CreateReportDialog
import com.veye.mobile.ui.screens.CreateTeamDialog
import com.veye.mobile.ui.screens.EnvironmentScreen
import com.veye.mobile.ui.screens.MapScreen
import com.veye.mobile.ui.screens.MotionScreen
import com.veye.mobile.ui.screens.ReportScreen
import com.veye.mobile.ui.screens.SystemNotificationsDialog
import com.veye.mobile.ui.screens.TeamChatScreen
import com.veye.mobile.ui.screens.TeamChatViewModel
import com.veye.mobile.ui.screens.TeamScreen
import com.veye.mobile.ui.screens.TeamViewModel
import com.veye.mobile.ui.screens.TutorialLibraryScreen
import com.veye.mobile.ui.theme.VeyeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class Tab(val route: String, val label: String, val pageTitle: String, val icon: @Composable () -> Unit) {
  data object Library : Tab("library", "图鉴", "识别图鉴", { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) })
  data object Team : Tab("team", "组队", "组队群聊", { Icon(Icons.Default.People, contentDescription = null) })
  data object Report : Tab("report", "报告", "工作报告", { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) })
  data object Map : Tab("map", "地图", "观测地图", { Icon(Icons.Default.Map, contentDescription = null) })
  data object Environment : Tab("environment", "环境", "环境监测", { Icon(Icons.Default.Eco, contentDescription = null) })
  data object Motion : Tab("motion", "运动", "运动健康", { Icon(Icons.Default.DirectionsRun, contentDescription = null) })
}

@Composable
private fun TabPage(tab: Tab, onOpenChat: (String, String) -> Unit) {
  when (tab) {
    Tab.Library -> TutorialLibraryScreen()
    Tab.Team -> TeamScreen(onOpenChat = onOpenChat)
    Tab.Report -> ReportScreen()
    Tab.Map -> MapScreen()
    Tab.Environment -> EnvironmentScreen()
    Tab.Motion -> MotionScreen()
  }
}

@Composable
fun AppRoot() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val loggedIn by CloudAuthState.loggedIn.collectAsState()
  val connectionStatus by CloudConnectionState.status.collectAsState()
  val canEnterMain = loggedIn && when (connectionStatus) {
    ConnectionStatus.Ready, ConnectionStatus.Checking -> true
    else -> false
  }

  LaunchedEffect(Unit) {
    CloudConnectionState.probe(context)
  }

  LaunchedEffect(canEnterMain) {
    while (canEnterMain) {
      delay(30_000)
      CloudConnectionState.probe(context, showProgress = false)
      if (CloudConnectionState.status.value != ConnectionStatus.Ready) break
    }
  }

  if (!canEnterMain) {
    AuthScreen(
      onLoggedIn = { scope.launch { CloudConnectionState.probe(context) } },
      onConnectionChanged = { scope.launch { CloudConnectionState.probe(context, invalidateApi = true) } },
    )
  } else {
    MainAppShell()
  }
}

@Composable
private fun MainAppShell() {
  val context = LocalContext.current
  val teamVm: TeamViewModel = viewModel()
  val chatVm: TeamChatViewModel = viewModel()
  val mapVm: MapViewModel = viewModel()
  val companionVm: CompanionViewModel = viewModel()
  val notifyUnread by teamVm.unread.collectAsState()
  val mapTeamId by mapVm.selectedTeamId.collectAsState()
  val companion by companionVm.state.collectAsState()
  val navController = rememberNavController()
  val scope = rememberCoroutineScope()
  val tabs = listOf(Tab.Library, Tab.Team, Tab.Report, Tab.Map, Tab.Environment, Tab.Motion)
  val connectionStatus by CloudConnectionState.status.collectAsState()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route
  val inChat = currentRoute?.startsWith("chat/") == true
  val onMainRoute = currentRoute == "main" || currentRoute == null
  var savedTabPage by rememberSaveable { mutableIntStateOf(0) }
  val pagerState = rememberPagerState(
    initialPage = savedTabPage.coerceIn(0, tabs.lastIndex),
    pageCount = { tabs.size },
  )
  LaunchedEffect(pagerState.currentPage) {
    savedTabPage = pagerState.currentPage
  }
  val currentTabIndex = if (onMainRoute) pagerState.currentPage else -1
  val currentTab = if (currentTabIndex in tabs.indices) tabs[currentTabIndex] else null
  val currentPageTitle = when {
    inChat -> "群聊"
    currentTab != null -> currentTab.pageTitle
    else -> "V-Eye"
  }
  var showNotifications by remember { mutableStateOf(false) }
  var showProfile by remember { mutableStateOf(false) }
  var showLibraryFilter by remember { mutableStateOf(false) }
  var showCreateTeam by remember { mutableStateOf(false) }
  var showCreateReport by remember { mutableStateOf(false) }
  var showMapControls by remember { mutableStateOf(false) }
  var showMotionSettings by remember { mutableStateOf(false) }
  var showCompanionDetail by remember { mutableStateOf(false) }

  val tabSettingsLabel = when (currentTab) {
    Tab.Library -> "图鉴筛选"
    Tab.Team -> "创建小队"
    Tab.Report -> "生成报告"
    Tab.Map -> "地图设置"
    Tab.Environment -> "刷新环境"
    Tab.Motion -> "运动设置"
    else -> "页面设置"
  }

  LaunchedEffect(Unit) {
    teamVm.refresh()
    companionVm.refreshFromCloud()
    companionVm.grantDailyEnergyBoost()
  }
  LaunchedEffect(pagerState.currentPage, onMainRoute) {
    if (onMainRoute && currentTab != null) {
      companionVm.onTabChanged(currentTab.route)
    }
  }
  LaunchedEffect(connectionStatus) {
    companionVm.updateConnectionStatus(connectionStatus)
  }
  LaunchedEffect(notifyUnread) {
    companionVm.updateTeamUnread(notifyUnread)
  }
  LaunchedEffect(mapTeamId) {
    companionVm.updateMapTeamSelected(!mapTeamId.isNullOrBlank())
  }

  ProfileMenuSheet(visible = showProfile, onDismiss = { showProfile = false }, onLoggedOut = { teamVm.refresh() })
  CompanionDetailSheet(
    visible = showCompanionDetail,
    companion = companion,
    onDismiss = { showCompanionDetail = false },
    onGoMotion = {
      scope.launch { pagerState.animateScrollToPage(tabs.indexOf(Tab.Motion)) }
    },
    onGoReport = {
      scope.launch { pagerState.animateScrollToPage(tabs.indexOf(Tab.Report)) }
    },
    onOpenConnection = { showProfile = true },
  )
  LibraryFilterSheet(visible = showLibraryFilter, onDismiss = { showLibraryFilter = false })
  MapControlsSheet(
    visible = showMapControls,
    onDismiss = { showMapControls = false },
    navController = navController,
  )
  MotionSettingsSheet(visible = showMotionSettings, onDismiss = { showMotionSettings = false })
  if (showNotifications) {
    SystemNotificationsDialog(onDismiss = { showNotifications = false }, vm = teamVm)
  }
  CreateTeamDialog(
    visible = showCreateTeam,
    onDismiss = { showCreateTeam = false },
    onCreate = { name, desc ->
      teamVm.createTeam(name, desc)
      chatVm.refreshConversations()
    },
  )
  CreateReportDialog(visible = showCreateReport, onDismiss = { showCreateReport = false })

  NatureBackground {
    Box(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(if (inChat) WindowInsets.statusBars else WindowInsets.systemBars),
    ) {
    Column(
      Modifier.fillMaxSize(),
    ) {
      CloudTickerBar(
        message = "V-Eye · $currentPageTitle · v${BuildConfig.VERSION_NAME}",
        connectionStatus = connectionStatus,
      )

      if (!inChat) {
        AppTopBar(
          title = currentPageTitle,
          settingsContentDescription = tabSettingsLabel,
          notifyUnread = notifyUnread,
          onOpenTabSettings = {
            when (currentTab) {
              Tab.Library -> showLibraryFilter = true
              Tab.Team -> showCreateTeam = true
              Tab.Report -> showCreateReport = true
              Tab.Map -> showMapControls = true
              Tab.Environment -> { /* 环境页内自带刷新 */ }
              Tab.Motion -> showMotionSettings = true
              else -> Unit
            }
          },
          onOpenNotifications = { showNotifications = true },
          onOpenProfile = { showProfile = true },
        )
      }

      Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
          if (!inChat) {
            NavigationBar(containerColor = VeyeColors.Surface, tonalElevation = 0.dp) {
              tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                  selected = onMainRoute && pagerState.currentPage == index,
                  onClick = {
                    scope.launch {
                      if (!onMainRoute) {
                        navController.navigate("main") {
                          popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                          launchSingleTop = true
                          restoreState = true
                        }
                      }
                      pagerState.animateScrollToPage(index)
                    }
                  },
                  icon = tab.icon,
                  label = {
                    Text(
                      tab.label,
                      fontWeight = if (onMainRoute && pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                    )
                  },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VeyeColors.PrimaryLight,
                    selectedTextColor = VeyeColors.Primary,
                    indicatorColor = VeyeColors.AccentSoft,
                    unselectedIconColor = VeyeColors.Muted,
                    unselectedTextColor = VeyeColors.Muted,
                  ),
                )
              }
            }
          }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = "main",
          modifier = Modifier.padding(innerPadding),
        ) {
          composable("main") {
            HorizontalPager(
              state = pagerState,
              modifier = Modifier.fillMaxSize(),
              beyondViewportPageCount = 1,
            ) { page ->
              TabPage(
                tab = tabs[page],
                onOpenChat = { teamId, teamName ->
                  navController.navigate("chat/$teamId/${Uri.encode(teamName)}")
                },
              )
            }
          }
          composable(
            route = "chat/{teamId}/{teamName}",
            arguments = listOf(
              navArgument("teamId") { type = NavType.StringType },
              navArgument("teamName") { type = NavType.StringType },
            ),
          ) { backStack ->
            val teamId = backStack.arguments?.getString("teamId") ?: return@composable
            val teamName = Uri.decode(backStack.arguments?.getString("teamName").orEmpty())
            TeamChatScreen(
              teamId = teamId,
              teamName = teamName.ifBlank { "群聊" },
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
      if (!inChat) {
        DraggableCompanionBubble(
          companion = companion,
          onClick = { showCompanionDetail = true },
          compact = companion.bubbleCompact,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
