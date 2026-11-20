package com.agrovision.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    /**
     * 启动时先检查「隐私协议是否已签署」，签署后才允许初始化 SDK。
     *
     * 合规红线：任何读取用户信息（定位、设备ID、相机、网络请求后端API）的逻辑
     * 必须在用户点击 checkbox 同意协议之后执行。此 StateFlow 为 AgroVisionNavHost
     * 的启动门闩：
     *   - false → 强制停留在 WelcomeScreen（SplashScreen 2 秒过了也不进入主页）
     *   - true → 才 authRepository.initialize() 刷新 token / 同步等
     */
    val privacyAgreed: StateFlow<Boolean> = settingsDataStore.settings
        .map { it.privacyAgreed }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun initialize() {
        viewModelScope.launch {
            authRepository.initialize()
        }
    }

    fun loginAsGuest() {
        authRepository.loginAsGuest()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    /**
     * 合规：记录用户已同意《用户协议》与《隐私政策》。
     * 必须在 WelcomeScreen 勾选 checkbox + 点击「立即登录」或「游客体验」后调用，
     * 且在 navigate / loginAsGuest 之前调用，保证后续进入主页时
     * FusedLocationProviderClient / CameraX / NetworkModule 等隐私相关组件可
     * 合法初始化。
     */
    fun markPrivacyAgreed() {
        viewModelScope.launch {
            settingsDataStore.setPrivacyAgreed(true)
        }
    }
}
