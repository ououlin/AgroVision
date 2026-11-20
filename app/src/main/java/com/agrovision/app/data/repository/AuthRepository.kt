package com.agrovision.app.data.repository

import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.data.local.datastore.TokenManager
import com.agrovision.app.data.remote.AuthApi
import com.agrovision.app.data.remote.LoginRequest
import com.agrovision.app.data.remote.SendCodeRequest
import com.agrovision.app.data.remote.RefreshRequest
import com.agrovision.app.domain.model.AuthError
import com.agrovision.app.domain.model.AuthException
import com.agrovision.app.domain.model.AuthState
import com.agrovision.app.domain.model.User
import com.agrovision.app.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Singleton

/**
 * 认证仓库 — 统一管理登录状态
 *
 * ## 线程安全
 * [authMutex] 保护 [_authState] 写操作和 token 存储操作；网络调用在锁外执行以避免长时间持锁。
 *
 * ## 登录态判定
 * [authState] 是全应用唯一可信源。`User.isLoggedIn` 和 `TokenManager.isLoggedIn()` 仅为辅助方法。
 *
 * ## Token 刷新策略
 * - [initialize] 启动时检查 `expiresAt`，距过期 < 5 分钟则主动 refresh
 * - 业务请求 401 由 [TokenAuthenticator] 被动刷新
 */
@Singleton
class AuthRepository(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** 保护 _authState 写操作与 token 存储；不保护网络调用 */
    private val authMutex = Mutex()

    /**
     * 初始化：检查本地 Token
     *
     * V2.0 改造：FeatureFlag.isV2AccountEnabled 启用账号体系时走网络认证。
     * 关闭时回落到 V1.0 行为（直接 NotLoggedIn，由调用方走游客模式）。
     * 利用 expiresAt 主动判断：token 未过期则直接恢复登录态，避免每次启动都强制 refresh。
     */
    suspend fun initialize() {
        authMutex.withLock {
            _authState.value = AuthState.Loading
        }
        try {
            // V2.0 账号体系未启用时，跳过网络认证
            if (!FeatureFlag.isV2AccountEnabled) {
                val localUserId = tokenManager.getUserId().ifBlank { "local_user" }
                tokenManager.saveUserId(localUserId)
                tokenManager.saveUserRole(UserRole.GUEST.name)
                tokenManager.saveLoginState(true)
                authMutex.withLock {
                    _authState.value = AuthState.LoggedIn(
                        User(
                            userId = localUserId,
                            nickname = "本地用户",
                            role = UserRole.GUEST
                        )
                    )
                }
                return
            }

            val token = tokenManager.getAccessToken()
            if (token.isEmpty()) {
                authMutex.withLock { _authState.value = AuthState.NotLoggedIn }
                return
            }

            // 主动判断：token 未过期且距过期 > 5 分钟，直接恢复登录态，无需 refresh
            if (!tokenManager.isAccessTokenExpired()) {
                val expiresAt = tokenManager.getExpiresAt()
                val remainingMs = expiresAt - System.currentTimeMillis()
                if (remainingMs > 5 * 60 * 1000L) {
                    Timber.d("Token 仍有效（剩余 ${remainingMs / 1000}s），直接恢复登录态")
                    val user = restoreUserFromStorage(token)
                    authMutex.withLock { _authState.value = AuthState.LoggedIn(user) }
                    return
                }
            }

            // 距过期 < 5 分钟或已过期，尝试 refresh
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isEmpty()) {
                Timber.w("无 refresh token，无法续期，清除并退出")
                tokenManager.clearTokens()
                authMutex.withLock { _authState.value = AuthState.NotLoggedIn }
                return
            }

            try {
                val result = authApi.refreshToken(RefreshRequest(refreshToken))
                if (result.code == 200 && result.data != null) {
                    val data = result.data
                    tokenManager.saveTokens(data.accessToken, data.refreshToken, data.expireIn)
                    val user = mapToUser(data)
                    authMutex.withLock { _authState.value = AuthState.LoggedIn(user) }
                } else {
                    Timber.w("refresh 业务失败 code=${result.code}，清除并退出")
                    tokenManager.clearTokens()
                    authMutex.withLock { _authState.value = AuthState.NotLoggedIn }
                }
            } catch (e: Exception) {
                Timber.w(e, "Token refresh failed, clearing credentials")
                tokenManager.clearTokens()
                authMutex.withLock { _authState.value = AuthState.NotLoggedIn }
            }
        } catch (e: Exception) {
            Timber.e(e, "Auth init failed")
            authMutex.withLock { _authState.value = AuthState.NotLoggedIn }
        }
    }

    /**
     * 发送验证码
     *
     * @return Result<Boolean> true=发送成功；failure 携带 [AuthException]
     */
    suspend fun sendCode(phone: String): Result<Boolean> {
        return try {
            Timber.d("sendCode → phone=$phone, baseUrl=${com.agrovision.app.BuildConfig.BASE_URL}")
            val result = authApi.sendCode(SendCodeRequest(phone))
            Timber.d("sendCode ← code=${result.code}, message=${result.message}")
            if (result.code == 200) {
                Result.success(result.data?.success ?: false)
            } else {
                // 业务失败（如手机号频次限制），不模拟成功
                Result.failure(AuthException(AuthError.ServerError(result.code, result.message.ifEmpty { "验证码发送失败" })))
            }
        } catch (e: Exception) {
            Timber.w(e, "sendCode 网络异常: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(AuthException(AuthError.NetworkError(e)))
        }
    }

    /**
     * 手机号+验证码登录
     *
     * @return Result<User> failure 携带 [AuthException]
     */
    suspend fun login(phone: String, code: String): Result<User> {
        return try {
            Timber.d("login → phone=$phone, baseUrl=${com.agrovision.app.BuildConfig.BASE_URL}")
            val result = authApi.login(LoginRequest(phone, code))
            Timber.d("login ← code=${result.code}, message=${result.message}")
            if (result.code != 200 || result.data == null) {
                // 区分凭证错误 vs 服务器错误
                val error = if (result.code == 401 || result.code == 4003) {
                    AuthError.InvalidCredentials
                } else {
                    AuthError.ServerError(result.code, result.message.ifEmpty { "登录失败" })
                }
                return Result.failure(AuthException(error))
            }

            val data = result.data
            val user = mapToUser(data)
            authMutex.withLock {
                tokenManager.saveTokens(data.accessToken, data.refreshToken, data.expireIn)
                tokenManager.saveUserId(user.userId)
                tokenManager.saveUserRole(user.role.name)
                tokenManager.saveLoginState(true)
                _authState.value = AuthState.LoggedIn(user)
            }
            Result.success(user)
        } catch (e: Exception) {
            Timber.w(e, "login 网络异常")
            Result.failure(AuthException(AuthError.NetworkError(e)))
        }
    }

    /**
     * 游客登录
     *
     * 保存 loginState=true 以保持与 [User.isLoggedIn]（token.isNotEmpty() || isGuest）一致，
     * 避免游客登录后 TokenManager.isLoggedIn() 与 AuthState 矛盾。
     */
    fun loginAsGuest(): User {
        val user = User(
            userId = "guest_${System.currentTimeMillis()}",
            nickname = "游客用户",
            role = UserRole.GUEST
        )
        tokenManager.saveUserId(user.userId)
        tokenManager.saveUserRole(user.role.name)
        tokenManager.saveLoginState(true)
        _authState.value = AuthState.LoggedIn(user)
        return user
    }

    /**
     * 退出登录
     *
     * logout 接口失败时记录 warning 日志，但仍清除本地状态（保证用户可退出）。
     */
    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: Exception) {
            // 不静默吞错，记录日志便于排查后端问题；本地仍继续清除
            Timber.w(e, "logout 接口调用失败，继续清除本地状态")
        }
        authMutex.withLock {
            tokenManager.clearTokens()
            _authState.value = AuthState.NotLoggedIn
        }
    }

    /**
     * 获取当前用户
     */
    fun getCurrentUser(): User? {
        val state = _authState.value
        return if (state is AuthState.LoggedIn) state.user else null
    }

    private fun mapToUser(data: com.agrovision.app.data.remote.LoginResult): User {
        return User(
            userId = data.userId,
            nickname = data.nickname.ifEmpty { "农智眼用户" },
            phone = data.phone,
            avatar = data.avatar,
            role = try { UserRole.valueOf(data.role) } catch (_: Exception) { UserRole.USER },
            token = data.accessToken,
            refreshToken = data.refreshToken
        )
    }

    /**
     * 从本地存储恢复 User（token 未过期时使用，避免每次启动都 refresh）。
     */
    private fun restoreUserFromStorage(token: String): User {
        return User(
            userId = tokenManager.getUserId(),
            nickname = "农智眼用户",
            role = try { UserRole.valueOf(tokenManager.getUserRole()) } catch (_: Exception) { UserRole.USER },
            token = token,
            refreshToken = tokenManager.getRefreshToken()
        )
    }
}
