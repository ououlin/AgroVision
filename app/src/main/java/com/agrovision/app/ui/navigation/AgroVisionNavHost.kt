package com.agrovision.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agrovision.app.BuildConfig
import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.domain.model.AuthState
import com.agrovision.app.ui.screen.about.AboutScreen
import com.agrovision.app.ui.screen.alert.AlertScreen
import com.agrovision.app.ui.screen.auth.LoginScreen
import com.agrovision.app.ui.screen.auth.SplashScreen
import com.agrovision.app.ui.screen.auth.WelcomeScreen
import com.agrovision.app.ui.screen.chat.ChatScreen
import com.agrovision.app.ui.screen.data.DataScreen
import com.agrovision.app.ui.screen.detection.DetectionScreen
import com.agrovision.app.ui.screen.diagnosis.DiagnosisScreen
import com.agrovision.app.ui.screen.encyclopedia.EncyclopediaScreen
import com.agrovision.app.ui.screen.encyclopedia.PestDetailScreen
import com.agrovision.app.ui.screen.farm.FarmScreen
import com.agrovision.app.ui.screen.farm.PlotCreateScreen
import com.agrovision.app.ui.screen.farm.PlotDetailScreen
import com.agrovision.app.ui.screen.farm.TreatmentCreateScreen
import com.agrovision.app.ui.screen.gis.GisMapScreen
import com.agrovision.app.ui.screen.history.HistoryDetailScreen
import com.agrovision.app.ui.screen.history.HistoryScreen
import com.agrovision.app.ui.screen.home.HomeScreen
import com.agrovision.app.ui.screen.inspection.InspectionScreen
import com.agrovision.app.ui.screen.manual.ManualScreen
import com.agrovision.app.ui.screen.monitor.MonitorScreen
import com.agrovision.app.ui.screen.ocr.OcrScreen
import com.agrovision.app.ui.screen.patrol.PatrolHubScreen
import com.agrovision.app.ui.screen.pesticide.PesticideCenterScreen
import com.agrovision.app.ui.screen.profile.ProfileScreen
import com.agrovision.app.ui.screen.settings.SettingsScreen
import com.agrovision.app.ui.screen.settings.ApiManagerScreen
import com.agrovision.app.ui.screen.settings.DevCenterScreen
import com.agrovision.app.ui.screen.agrisearch.AgriSearchScreen
import kotlinx.coroutines.delay

@Composable
fun AgroVisionNavHost() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    // ===== 合规：隐私协议门闩 =====
    // 只有 DataStore 中 privacyAgreed=true 才允许进入主页；否则即使 authState 是 LoggedIn
    // 也强制停留在 WelcomeScreen，避免任何 Hilt 单例（定位、相机、网络、设备信息）
    // 在未授权时被初始化。
    val privacyAgreed by authViewModel.privacyAgreed.collectAsState()

    if (!privacyAgreed || authState is AuthState.Loading) {
        SplashScreen(authState, {}, {})
        LaunchedEffect(privacyAgreed) {
            delay(2000)
            // 只有用户已同意协议的情况下才真正初始化认证信息（刷新 token / 同步）
            if (privacyAgreed && authState is AuthState.Loading) {
                authViewModel.initialize()
            }
        }
        // 隐私未同意 → SplashScreen 2s 过了也强制跳 WelcomeScreen
        if (!privacyAgreed) {
            AuthNavHost(
                onNavigateToHome = {
                    authViewModel.markPrivacyAgreed()
                    authViewModel.loginAsGuest()
                },
                onGuestEnter = {
                    authViewModel.markPrivacyAgreed()
                    authViewModel.loginAsGuest()
                }
            )
        }
        return
    }

    when (authState) {
        is AuthState.NotLoggedIn -> {
            AuthNavHost(
                onNavigateToHome = {
                    authViewModel.markPrivacyAgreed()
                    authViewModel.loginAsGuest()
                },
                onGuestEnter = {
                    authViewModel.markPrivacyAgreed()
                    authViewModel.loginAsGuest()
                }
            )
        }
        else -> MainAppNavHost()
    }
}

@Composable
fun AuthNavHost(onNavigateToHome: () -> Unit, onGuestEnter: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController, NavRoutes.Welcome.route) {
        composable(NavRoutes.Welcome.route) {
            WelcomeScreen(
                onLogin = { navController.navigate(NavRoutes.Login.route) },
                onGuest = onGuestEnter,
                onPrivacy = { navController.navigate(NavRoutes.About.createRoute("privacy")) },
                onTerms = { navController.navigate(NavRoutes.About.createRoute("terms")) }
            )
        }
        if (FeatureFlag.isV2AccountEnabled) {
            composable(NavRoutes.Login.route) {
                LoginScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLoginSuccess = onNavigateToHome
                )
            }
        }
        // 认证流程内也允许打开"关于"查看协议（WelcomeScreen → 协议链接跳转）
        composable(
            route = NavRoutes.About.route,
            arguments = listOf(navArgument("initialTab") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { entry ->
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                initialTab = entry.arguments?.getString("initialTab")
            )
        }
    }
}

@Composable
fun MainAppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = NavRoutes.bottomNavItems.any {
        currentDestination?.hierarchy?.any { d -> d.route == it.route } == true
    }

    // 用于首页"图片识别"入口：进入检测页后自动打开相册，消费后重置
    var pendingOpenGallery by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavRoutes.bottomNavItems.forEach { screen ->
                        val sel = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(if (sel) screen.selectedIcon else screen.unselectedIcon, screen.title) },
                            label = { Text(screen.title) },
                            selected = sel,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, NavRoutes.Home.route, Modifier.padding(innerPadding)) {
            composable(NavRoutes.Home.route) {
                HomeScreen(
                    onNavigateToDetection = { navController.goto(NavRoutes.Detection) },
                    onNavigateToGallery = {
                        pendingOpenGallery = true
                        navController.goto(NavRoutes.Detection)
                    },
                    onNavigateToEncyclopedia = { navController.navigate(NavRoutes.Encyclopedia.route) },
                    onNavigateToHistory = { navController.navigate(NavRoutes.History.route) },
                    onNavigateToChat = { navController.navigate(NavRoutes.Chat.createRoute()) },
                    onNavigateToPestDetail = { navController.navigate(NavRoutes.PestDetail.createRoute(it)) },
                    onNavigateToDiagnosis = { d, c, img -> navController.navigate(NavRoutes.Diagnosis.createRoute(d, c, img)) },
                    onNavigateToData = { navController.navigate(NavRoutes.Data.route) },
                    // ===== V2.0 新增导航回调 =====
                    onNavigateToFarm = { navController.navigate(NavRoutes.Farm.route) },
                    onNavigateToAgriSearch = { navController.navigate(NavRoutes.AgriSearch.route) },
                    onNavigateToPesticideCenter = { pest ->
                        navController.navigate(NavRoutes.PesticideCenter.createRoute(pest))
                    }
                )
            }
            composable(NavRoutes.Detection.route) {
                DetectionScreen(
                    onNavigateToPestDetail = { navController.navigate(NavRoutes.PestDetail.createRoute(it)) },
                    onNavigateToDiagnosis = { d, c, img -> navController.navigate(NavRoutes.Diagnosis.createRoute(d, c, img)) },
                    initialOpenGallery = pendingOpenGallery,
                    onGalleryConsumed = { pendingOpenGallery = false }
                )
            }
            composable(
                route = NavRoutes.Chat.route,
                arguments = listOf(navArgument("initialQuestion") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                ChatScreen(
                    initialQuestion = entry.arguments?.getString("initialQuestion") ?: "",
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            // V3.0 巡检中心（仅 debug 版可见）
            if (FeatureFlag.isV3Enabled) {
                composable(NavRoutes.Patrol.route) {
                    PatrolHubScreen(
                        onNavigateToGis = { navController.navigate(NavRoutes.Gis.route) },
                        onNavigateToInspection = { navController.navigate(NavRoutes.Inspection.route) },
                        onNavigateToAlert = { navController.navigate(NavRoutes.Alert.route) },
                        onNavigateToMonitor = { navController.navigate(NavRoutes.Monitor.route) },
                        onNavigateToHistory = { navController.navigate(NavRoutes.History.route) },
                        onNavigateToEncyclopedia = { navController.navigate(NavRoutes.Encyclopedia.route) }
                    )
                }
            }
            composable(NavRoutes.Profile.route) {
                ProfileScreen(
                    onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                    onNavigateToAbout = { navController.navigate(NavRoutes.About.route) },
                    onNavigateToHistory = { navController.navigate(NavRoutes.History.route) },
                    onNavigateToEncyclopedia = { navController.navigate(NavRoutes.Encyclopedia.route) },
                    onNavigateToOcr = { navController.navigate(NavRoutes.Ocr.route) },
                    onNavigateToGis = { navController.navigate(NavRoutes.Gis.route) },
                    onNavigateToInspection = { navController.navigate(NavRoutes.Inspection.route) },
                    onNavigateToAlert = { navController.navigate(NavRoutes.Alert.route) },
                    onNavigateToMonitor = { navController.navigate(NavRoutes.Monitor.route) }
                )
            }

            // Sub pages
            composable(NavRoutes.Encyclopedia.route) {
                EncyclopediaScreen(
                    onNavigateToPestDetail = { navController.navigate(NavRoutes.PestDetail.createRoute(it)) },
                    onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.PestDetail.route, listOf(navArgument("pestName") {
                type = NavType.StringType
                defaultValue = ""
            })) { entry ->
                PestDetailScreen(
                    pestName = entry.arguments?.getString("pestName") ?: "",
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetection = { navController.goto(NavRoutes.Detection) },
                    onNavigateToPesticideCenter = { pest ->
                        navController.navigate(NavRoutes.PesticideCenter.createRoute(pest))
                    }
                )
            }
            composable(NavRoutes.Diagnosis.route, listOf(
                navArgument("diseaseName") { type = NavType.StringType },
                navArgument("confidence") { type = NavType.FloatType },
                navArgument("imagePath") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )) { entry ->
                DiagnosisScreen(
                    diseaseName = entry.arguments?.getString("diseaseName") ?: "",
                    confidence = entry.arguments?.getFloat("confidence") ?: 0f,
                    imagePath = entry.arguments?.getString("imagePath") ?: "",
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { question ->
                        navController.navigate(NavRoutes.Chat.createRoute(question))
                    },
                    onNavigateToFarm = { navController.navigate(NavRoutes.Farm.route) },
                    onNavigateToPesticideCenter = { pest ->
                        navController.navigate(NavRoutes.PesticideCenter.createRoute(pest))
                    }
                )
            }
            composable(NavRoutes.History.route) {
                HistoryScreen(
                    onNavigateToDetail = { navController.navigate(NavRoutes.HistoryDetail.createRoute(it)) },
                    onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.HistoryDetail.route, listOf(navArgument("historyId") { type = NavType.LongType })) { entry ->
                HistoryDetailScreen(
                    historyId = entry.arguments?.getLong("historyId") ?: 0L,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDiagnosis = { d, c, img -> navController.navigate(NavRoutes.Diagnosis.createRoute(d, c, img)) }
                )
            }
            composable(NavRoutes.Data.route) {
                DataScreen(
                    onNavigateToEncyclopedia = { navController.navigate(NavRoutes.Encyclopedia.route) },
                    onNavigateToHistory = { navController.navigate(NavRoutes.History.route) },
                    onNavigateToPestDetail = { navController.navigate(NavRoutes.PestDetail.createRoute(it)) }
                )
            }
            // OCR 识别（仅 debug 版可见）
            if (FeatureFlag.isOcrEnabled) {
                composable(NavRoutes.Ocr.route) { OcrScreen(onNavigateBack = { navController.popBackStack() }) }
            }
            composable(NavRoutes.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAbout = { navController.navigate(NavRoutes.About.route) },
                    onNavigateToManual = { navController.navigate(NavRoutes.Manual.route) },
                    onNavigateToApiManager = { navController.navigate(NavRoutes.ApiManager.route) },
                    onNavigateToDevCenter = { navController.navigate(NavRoutes.DevCenter.route) },
                    onNavigateToLogin = { navController.navigate(NavRoutes.Login.route) }
                )
            }
            composable(NavRoutes.ApiManager.route) { ApiManagerScreen(onNavigateBack = { navController.popBackStack() }) }
            if (BuildConfig.DEBUG) {
                composable(NavRoutes.DevCenter.route) { DevCenterScreen(onNavigateBack = { navController.popBackStack() }) }
            }
            composable(
                route = NavRoutes.About.route,
                arguments = listOf(navArgument("initialTab") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                })
            ) { entry ->
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialTab = entry.arguments?.getString("initialTab")
                )
            }
            composable(NavRoutes.Manual.route) {
                ManualScreen(onNavigateBack = { navController.popBackStack() })
            }
            // V2.0 路由
            composable(NavRoutes.AgriSearch.route) {
                AgriSearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPestDetail = { navController.navigate(NavRoutes.PestDetail.createRoute(it)) }
                )
            }
            composable(NavRoutes.Farm.route) {
                FarmScreen(
                    onNavigateToPlotDetail = { navController.navigate(NavRoutes.PlotDetail.createRoute(it)) },
                    onNavigateToPlotCreate = { navController.navigate(NavRoutes.PlotCreate.route) },
                    onNavigateBack = null
                )
            }
            composable(NavRoutes.PlotDetail.route, listOf(navArgument("plotId") { type = NavType.LongType })) { entry ->
                PlotDetailScreen(
                    plotId = entry.arguments?.getLong("plotId") ?: 0L,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTreatmentCreate = { navController.navigate(NavRoutes.TreatmentCreate.createRoute(it)) }
                )
            }
            composable(NavRoutes.PlotCreate.route) {
                PlotCreateScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = NavRoutes.TreatmentCreate.route,
                arguments = listOf(
                    navArgument("plotId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("chemicalName") { type = NavType.StringType; defaultValue = "" },
                    navArgument("dosage") { type = NavType.StringType; defaultValue = "" },
                    navArgument("safetyDays") { type = NavType.IntType; defaultValue = 14 }
                )
            ) { entry ->
                TreatmentCreateScreen(
                    plotId = entry.arguments?.getLong("plotId") ?: 0L,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreatePlot = { navController.navigate(NavRoutes.PlotCreate.route) }
                )
            }
            composable(
                route = NavRoutes.PesticideCenter.route,
                arguments = listOf(
                    navArgument("initialPest") { type = NavType.StringType; defaultValue = "" },
                    navArgument("initialTab") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { entry ->
                PesticideCenterScreen(
                    initialTab = entry.arguments?.getInt("initialTab") ?: 0,
                    onNavigateBack = { navController.popBackStack() },
                    onSaveToFarm = { chemicalName, dosage, safetyDays ->
                        navController.navigate(
                            NavRoutes.TreatmentCreate.createRoute(
                                chemicalName = chemicalName,
                                dosage = dosage,
                                safetyDays = safetyDays
                            )
                        )
                    }
                )
            }
            // V3.0 子页面（仅 debug 版可见）
            if (FeatureFlag.isV3Enabled) {
                composable(NavRoutes.Gis.route) { GisMapScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(NavRoutes.Inspection.route) { InspectionScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(NavRoutes.Alert.route) { AlertScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(NavRoutes.Monitor.route) { MonitorScreen(onNavigateBack = { navController.popBackStack() }) }
            }
        }
    }
}

private fun androidx.navigation.NavController.goto(route: NavRoutes) {
    navigate(route.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
}
