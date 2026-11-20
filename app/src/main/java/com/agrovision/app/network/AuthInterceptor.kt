package com.agrovision.app.network

import com.agrovision.app.config.FeatureFlag
import com.agrovision.app.data.local.datastore.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证拦截器 — 为业务请求自动注入 `Authorization: Bearer <token>` 头。
 *
 * 设计要点：
 * 1. **FeatureFlag 守卫**：V2 账号体系未启用时跳过注入，与 AuthRepository 的离线模式一致
 * 2. **Token 脱敏**：日志中只输出 `Bearer ***`，绝不打印明文，防止通过 Logcat 泄露
 * 3. **已有头不覆盖**：若请求已自带 Authorization 头（如 refreshToken 接口），则跳过
 * 4. **无 token 静默放行**：登录/发送验证码等匿名接口不需要 token，直接放行由后端鉴权
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // V2 账号体系未启用时，不注入 token（离线/游客模式）
        if (!FeatureFlag.isV2AccountEnabled) {
            return chain.proceed(request)
        }

        // 请求已自带 Authorization 头，不覆盖
        if (request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        val token = tokenManager.getAccessToken()
        if (token.isEmpty()) {
            return chain.proceed(request)
        }

        val authedRequest = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        // 脱敏日志：仅记录 Bearer ***，任何 build type 下都不打印明文
        Timber.d("AuthInterceptor → ${request.method} ${request.url.encodedPath} | Bearer ${mask(token)}")

        return chain.proceed(authedRequest)
    }

    /** Token 脱敏：只保留前4位和后2位，中间用 *** 代替 */
    private fun mask(token: String): String {
        if (token.length <= 8) return "***"
        return token.take(4) + "***" + token.takeLast(2)
    }
}
