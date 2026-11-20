package com.agrovision.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.ui.navigation.AgroVisionNavHost
import com.agrovision.app.ui.screen.settings.SettingsViewModel
import com.agrovision.app.ui.theme.AgroVisionTheme
import com.agrovision.app.ui.theme.FontSize
import com.agrovision.app.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ===== 上架合规 + 安全：release 版本禁止截屏/录屏（防止输入 API Key 时被录屏）=====
        // 注意：仅对 release 生效；debug 时为了开发演示和截图保持放开。
        if (!BuildConfig.DEBUG) {
            try {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } catch (t: Throwable) {
                Timber.w(t, "设置 FLAG_SECURE 失败")
            }
        }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            // 注意：这里**不再直接请求定位权限**，完全由 HomeScreen 中用户主动触发
            // 「定位不可用」标签 → 先弹 PermissionRationaleDialog → 再真正请求权限，
            // 这样符合国内应用市场「不得启动就弹权限」的审核要求。

            // 语言变化时更新 Locale 配置
            LaunchedEffect(settings.language) {
                applyLanguage(settings.language)
            }

            AgroVisionTheme(
                themeMode = ThemeMode.fromKey(settings.themeMode),
                dynamicColor = settings.materialYou,
                fontSize = FontSize.fromKey(settings.fontSize)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AgroVisionNavHost()
                }
            }
        }
    }

    private fun applyLanguage(lang: String) {
        val locale = Locale(if (lang == "en") "en" else "zh")
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
