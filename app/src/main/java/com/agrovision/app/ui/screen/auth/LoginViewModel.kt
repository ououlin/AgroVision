package com.agrovision.app.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.domain.model.AuthError
import com.agrovision.app.domain.model.AuthException
import com.agrovision.app.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val code: String = "",
    val countdown: Int = 0,
    val isSending: Boolean = false,
    val isLoggingIn: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val user: User? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // ===== 防暴力破解：内存态计数（ViewModel 生命周期内有效，后端需配合硬限制）=====
    /** 登录失败次数，达到 5 次锁定 5 分钟 */
    private var loginFailCount = 0
    /** 锁定截止时间戳（毫秒），0 表示未锁定 */
    private var lockUntilTimestamp = 0L
    /** 本小时已发送验证码次数，达到 5 次拒绝 */
    private var sendCountThisHour = 0
    /** 本小时计数起始时间戳 */
    private var hourWindowStart = System.currentTimeMillis()

    private val phoneRegex = Regex("^1[3-9]\\d{9}$")

    fun onPhoneChanged(phone: String) {
        // 只允许数字且长度 <= 11，最终校验由 phoneRegex 完成
        if (phone.length <= 11 && phone.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(phone = phone, error = null)
        }
    }

    fun onCodeChanged(code: String) {
        if (code.length <= 6 && code.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(code = code, error = null)
        }
    }

    fun sendCode() {
        val phone = _uiState.value.phone
        if (!phoneRegex.matches(phone)) {
            _uiState.value = _uiState.value.copy(error = "请输入正确的手机号")
            return
        }

        // 防暴力：倒计时未结束
        if (_uiState.value.countdown > 0) {
            _uiState.value = _uiState.value.copy(error = "请 ${_uiState.value.countdown}s 后再试")
            return
        }

        // 防暴力：每小时最多 5 次
        val now = System.currentTimeMillis()
        if (now - hourWindowStart > 60 * 60 * 1000L) {
            hourWindowStart = now
            sendCountThisHour = 0
        }
        if (sendCountThisHour >= 5) {
            _uiState.value = _uiState.value.copy(error = "发送过于频繁，请稍后再试")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            val result = authRepository.sendCode(phone)
            result.fold(
                onSuccess = { success ->
                    sendCountThisHour++
                    if (success) {
                        _uiState.value = _uiState.value.copy(isSending = false, countdown = 60)
                        startCountdown()
                    } else {
                        // 服务端返回业务失败（success=false）
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            error = "验证码发送失败，请稍后重试"
                        )
                    }
                },
                onFailure = { e ->
                    // 不再模拟成功：展示真实错误
                    val msg = (e as? AuthException)?.error?.message
                        ?: e.message
                        ?: "验证码发送失败，请检查网络"
                    _uiState.value = _uiState.value.copy(isSending = false, error = msg)
                }
            )
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            for (i in 60 downTo 0) {
                _uiState.value = _uiState.value.copy(countdown = i)
                if (i > 0) delay(1000)
            }
        }
    }

    fun login() {
        val phone = _uiState.value.phone
        val code = _uiState.value.code
        if (!phoneRegex.matches(phone) || code.length != 6) {
            _uiState.value = _uiState.value.copy(error = "请输入正确的手机号和验证码")
            return
        }

        // 防暴力：登录锁定检查
        val now = System.currentTimeMillis()
        if (now < lockUntilTimestamp) {
            val remainSec = (lockUntilTimestamp - now) / 1000
            _uiState.value = _uiState.value.copy(error = "登录失败次数过多，请 ${remainSec}s 后重试")
            return
        }

        // 防重复登录：isLoggingIn guard
        if (_uiState.value.isLoggingIn) {
            Timber.d("login 已在进行中，忽略重复请求")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingIn = true, error = null)
            val result = authRepository.login(phone, code)
            result.fold(
                onSuccess = { user ->
                    // 登录成功，重置失败计数
                    loginFailCount = 0
                    lockUntilTimestamp = 0L
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        loginSuccess = true,
                        user = user
                    )
                },
                onFailure = { e ->
                    loginFailCount++
                    // 失败 5 次锁定 5 分钟
                    if (loginFailCount >= 5) {
                        lockUntilTimestamp = System.currentTimeMillis() + 5 * 60 * 1000L
                        loginFailCount = 0
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            error = "登录失败次数过多，已锁定 5 分钟"
                        )
                    } else {
                        val msg = (e as? AuthException)?.error?.let { error ->
                            when (error) {
                                is AuthError.InvalidCredentials -> "手机号或验证码错误"
                                is AuthError.RateLimited -> "操作过于频繁，请稍后再试"
                                is AuthError.NetworkError -> "网络连接失败，请检查网络"
                                is AuthError.ServerError -> error.message
                                is AuthError.TokenExpired -> "登录已过期，请重新登录"
                                is AuthError.Unknown -> error.message
                            }
                        } ?: e.message ?: "登录失败，请重试"
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            error = "登录失败：$msg（剩余 ${5 - loginFailCount} 次尝试）"
                        )
                    }
                }
            )
        }
    }

    fun loginAsGuest() {
        val user = authRepository.loginAsGuest()
        _uiState.value = _uiState.value.copy(loginSuccess = true, user = user)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
