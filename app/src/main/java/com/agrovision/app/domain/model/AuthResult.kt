package com.agrovision.app.domain.model

/**
 * 统一认证错误模型
 *
 * 用于在 AuthRepository 与 ViewModel 之间传递语义化的错误信息，
 * 替代原先裸的 Exception，便于 UI 层做差异化提示。
 */
sealed class AuthError(open val message: String) {
    /** 网络连接失败（无网、超时、DNS 等） */
    data class NetworkError(val cause: Throwable) : AuthError("网络连接失败，请检查网络")

    /** 服务器返回 5xx 或业务码异常 */
    data class ServerError(val code: Int, override val message: String) : AuthError(message)

    /** 凭证错误（手机号或验证码不正确） */
    data object InvalidCredentials : AuthError("手机号或验证码错误")

    /** 操作过于频繁（防暴力破解触发） */
    data object RateLimited : AuthError("操作过于频繁，请稍后再试")

    /** Token 已过期且 refresh 失败 */
    data object TokenExpired : AuthError("登录已过期，请重新登录")

    /** 其他未分类错误 */
    data class Unknown(val cause: Throwable) : AuthError(cause.message ?: "未知错误")
}

/**
 * 携带 [AuthError] 的异常，便于在 Result.failure 链路中传递语义化错误。
 */
class AuthException(val error: AuthError) : Exception(error.message)
