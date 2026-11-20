package com.agrovision.app.domain.model

/**
 * 用户模型 — 企业级多角色体系
 */
data class User(
    val userId: String = "",
    val nickname: String = "农智眼用户",
    val avatar: String = "",
    val phone: String? = null,
    val email: String? = null,
    val role: UserRole = UserRole.GUEST,
    val token: String = "",
    val refreshToken: String = "",
    val vip: Boolean = false,
    val createTime: Long = System.currentTimeMillis()
) {
    val isLoggedIn: Boolean get() = token.isNotEmpty() || isGuest
    val isGuest: Boolean get() = role == UserRole.GUEST
    val canUseCloud: Boolean get() = role != UserRole.GUEST
}

enum class UserRole(val label: String, val level: Int) {
    GUEST("游客", 0),
    USER("普通用户", 1),
    EXPERT("农业专家", 2),
    ADMIN("管理员", 3),
    SUPER_ADMIN("超级管理员", 4)
}

/**
 * 登录状态
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object NotLoggedIn : AuthState()
    data class LoggedIn(val user: User) : AuthState()
}
