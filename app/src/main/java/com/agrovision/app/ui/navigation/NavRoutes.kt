package com.agrovision.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavRoutes(
    val route: String, val title: String,
    val selectedIcon: ImageVector, val unselectedIcon: ImageVector,
    val hasBottomBar: Boolean = true
) {
    // 农户主导航：按日常任务组织，不暴露技术模块名称
    data object Home : NavRoutes("home", "首页", Icons.Filled.Home, Icons.Outlined.Home)
    data object Detection : NavRoutes("detection", "问诊", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)
    data object Chat : NavRoutes("chat?initialQuestion={initialQuestion}", "问农技", Icons.Filled.Forum, Icons.Outlined.Forum, false) {
        fun createRoute(initialQuestion: String = "") =
            "chat?initialQuestion=${android.net.Uri.encode(initialQuestion)}"
    }
    data object Patrol : NavRoutes("patrol", "巡检中心", Icons.Filled.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing)
    data object Profile : NavRoutes("profile", "我的", Icons.Filled.Person, Icons.Outlined.Person)

    // 子页面
    data object PestDetail : NavRoutes("pest_detail?pestName={pestName}", "病虫详情",
        Icons.Filled.MenuBook, Icons.Outlined.MenuBook, false) {
        fun createRoute(n: String) = "pest_detail?pestName=${android.net.Uri.encode(n)}"
    }
    data object Diagnosis : NavRoutes("diagnosis?diseaseName={diseaseName}&confidence={confidence}&imagePath={imagePath}", "AI诊断",
        Icons.Filled.Biotech, Icons.Outlined.Biotech, false) {
        fun createRoute(d: String, c: Float, imagePath: String = ""): String {
            val encodedImage = android.net.Uri.encode(imagePath)
            return "diagnosis?diseaseName=${android.net.Uri.encode(d)}&confidence=$c&imagePath=$encodedImage"
        }
    }
    data object Encyclopedia : NavRoutes("encyclopedia", "病虫百科",
        Icons.Filled.MenuBook, Icons.Outlined.MenuBook, false)
    data object History : NavRoutes("history", "历史记录",
        Icons.Filled.History, Icons.Outlined.History, false)
    data object HistoryDetail : NavRoutes("history_detail/{historyId}", "历史详情",
        Icons.Filled.History, Icons.Outlined.History, false) {
        fun createRoute(id: Long) = "history_detail/$id"
    }
    data object Ocr : NavRoutes("ocr", "OCR识别",
        Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner, false)
    data object Settings : NavRoutes("settings", "设置",
        Icons.Filled.Settings, Icons.Outlined.Settings, false)
    data object About : NavRoutes("about?initialTab={initialTab}", "关于",
        Icons.Filled.Info, Icons.Outlined.Info, false) {
        /**
         * @param initialTab 可选初始锚点：
         *   - "terms"  → 滚动到《用户协议》
         *   - "privacy" → 滚动到《隐私政策》
         *   - 其他值/null → 默认显示顶部 Logo+版本
         */
        fun createRoute(initialTab: String? = null) =
            if (initialTab.isNullOrBlank()) "about" else "about?initialTab=$initialTab"
    }
    data object Data : NavRoutes("data", "数据中心",
        Icons.Filled.BarChart, Icons.Outlined.BarChart, false)
    data object Gis : NavRoutes("gis", "GIS地图",
        Icons.Filled.Map, Icons.Outlined.Map, false)
    data object Inspection : NavRoutes("inspection", "巡检任务",
        Icons.Filled.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing, false)
    data object Alert : NavRoutes("alert", "告警中心",
        Icons.Filled.Notifications, Icons.Outlined.Notifications, false)
    data object Monitor : NavRoutes("monitor", "系统监控",
        Icons.Filled.Dashboard, Icons.Outlined.Dashboard, false)

    // V3.0 认证流
    data object Splash : NavRoutes("splash", "", Icons.Filled.Yard, Icons.Outlined.Yard, false)
    data object Welcome : NavRoutes("welcome", "", Icons.Filled.Yard, Icons.Outlined.Yard, false)
    data object Login : NavRoutes("login", "登录", Icons.Filled.Person, Icons.Outlined.Person, false)

    // V3.0 设置中心子页
    data object ApiManager : NavRoutes("api_manager", "API管理", Icons.Filled.Api, Icons.Outlined.Api, false)
    data object DevCenter : NavRoutes("dev_center", "开发者中心", Icons.Filled.Terminal, Icons.Outlined.Terminal, false)
    data object Manual : NavRoutes("manual", "用户使用手册", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, false)

    // V2.0 路由
    /** 农技搜索（独立入口，RAG only 不调 LLM） */
    data object AgriSearch : NavRoutes("agri_search", "农技搜索",
        Icons.Filled.Search, Icons.Outlined.Search, false)

    /** 我的农场（地块列表 + 摘要） */
    data object Farm : NavRoutes("farm", "农事",
        Icons.Filled.Agriculture, Icons.Outlined.Agriculture, false)

    /** 地块详情（含用药记录时间线） */
    data object PlotDetail : NavRoutes("plot_detail/{plotId}", "地块详情",
        Icons.Filled.Agriculture, Icons.Outlined.Agriculture, false) {
        fun createRoute(plotId: Long) = "plot_detail/$plotId"
    }

    /** 添加地块 */
    data object PlotCreate : NavRoutes("plot_create", "新建地块",
        Icons.Filled.Add, Icons.Outlined.Add, false)

    /** 添加用药记录 */
    data object TreatmentCreate : NavRoutes(
        "treatment_create?plotId={plotId}&chemicalName={chemicalName}&dosage={dosage}&safetyDays={safetyDays}",
        "添加用药",
        Icons.Filled.Healing, Icons.Outlined.Healing, false) {
        fun createRoute(
            plotId: Long = 0L,
            chemicalName: String = "",
            dosage: String = "",
            safetyDays: Int = 14
        ) = "treatment_create?plotId=$plotId" +
            "&chemicalName=${android.net.Uri.encode(chemicalName)}" +
            "&dosage=${android.net.Uri.encode(dosage)}" +
            "&safetyDays=$safetyDays"
    }

    /** 病虫害配药、本地农资目录与采购清单 */
    data object PesticideCenter : NavRoutes(
        "pesticide_center?initialPest={initialPest}&initialTab={initialTab}",
        "植保用药",
        Icons.Filled.Medication,
        Icons.Outlined.Medication,
        false
    ) {
        fun createRoute(initialPest: String = "", initialTab: Int = 0) =
            "pesticide_center?initialPest=${android.net.Uri.encode(initialPest)}&initialTab=$initialTab"
    }

    companion object {
        val bottomNavItems: List<NavRoutes>
            get() = listOf(Home, Detection, Farm, Profile)
    }
}
