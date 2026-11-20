package com.agrovision.app.network

import com.agrovision.app.data.local.datastore.TokenManager
import com.agrovision.app.data.remote.ApiResponse
import com.agrovision.app.data.remote.AuthApi
import com.agrovision.app.data.remote.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Token 认证器 — 401 时自动刷新 token 并重试请求。
 *
 * ## 工作机制
 * - OkHttp 收到 401 时回调 [authenticate]
 * - 用独立的 [bareAuthApi]（不经过本 Authenticator 的裸 client）调用 refreshToken 接口，避免递归
 * - 刷新成功 → 保存新 token → 返回带新 token 的重试请求
 * - 刷新失败 → 返回 null（放弃重试）→ 由 [AuthRepository] 后续感知 401 触发登出
 *
 * ## 并发控制
 * - [isRefreshing] AtomicBoolean 确保并发 401 时只触发一次刷新
 * - 其他请求在 refresh 期间直接返回 null，等待首次刷新完成后的后续重试
 *
 * ## 防无限重试
 * - 通过 [responseCount] 检查同一请求的重试次数，超过 2 次放弃
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    @Named("bareAuthApi") private val bareAuthApi: AuthApi
) : Authenticator {

    private val isRefreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        // 防止无限重试：同一请求已重试 2 次则放弃
        if (responseCount(response) >= 2) {
            Timber.w("TokenAuthenticator: 重试次数超限，放弃刷新")
            return null
        }

        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isEmpty()) {
            Timber.w("TokenAuthenticator: 无 refresh token，无法刷新")
            return null
        }

        // 并发控制：已有刷新在进行中，本次请求放弃（首次刷新成功后业务会自然重试）
        if (!isRefreshing.compareAndSet(false, true)) {
            Timber.d("TokenAuthenticator: 已有刷新进行中，跳过本次")
            return null
        }

        return try {
            runBlocking {
                val result = bareAuthApi.refreshToken(RefreshRequest(refreshToken))
                val data = result.data
                if (result.code == 200 && data != null) {
                    tokenManager.saveTokens(
                        accessToken = data.accessToken,
                        refreshToken = data.refreshToken,
                        expireIn = data.expireIn
                    )
                    Timber.d("TokenAuthenticator: token 刷新成功，重试请求")

                    // 返回带新 token 的重试请求
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${data.accessToken}")
                        .build()
                } else {
                    Timber.w("TokenAuthenticator: refresh 返回业务失败 code=${result.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "TokenAuthenticator: refresh 调用失败")
            null
        } finally {
            isRefreshing.set(false)
        }
    }

    /** 统计响应链中的重试次数 */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
