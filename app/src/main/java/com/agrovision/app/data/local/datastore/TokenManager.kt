package com.agrovision.app.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * Token 安全存储 — AES256 加密
 *
 * 管理 access_token / refresh_token / expires_at 以及用户基础信息。
 * - [saveTokens] 同时记录过期时间戳，便于 [AuthRepository.initialize] 主动判断是否需要刷新
 * - [clearTokens] 只清 token 相关 key，保留 user_id / user_role，避免登出后丢失用户偏好
 * - [clear] 已废弃，仅保留向后兼容，新代码应使用 [clearTokens]
 */
@Singleton
class TokenManager(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "agrovision_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 保存 token 并记录过期时间戳。
     *
     * @param expireIn access token 有效期（秒），来自服务端 [LoginResult.expireIn]
     */
    fun saveTokens(accessToken: String, refreshToken: String, expireIn: Long) {
        val expiresAt = System.currentTimeMillis() + expireIn * 1000L
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getAccessToken(): String = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
    fun getRefreshToken(): String = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""

    /**
     * 获取 access token 过期时间戳（毫秒）。
     * @return 0 表示无记录（旧版本升级或未登录）
     */
    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    /**
     * 判断 access token 是否已过期。
     * 无过期记录（返回 0）时视为已过期，触发主动刷新。
     */
    fun isAccessTokenExpired(): Boolean {
        val expiresAt = getExpiresAt()
        if (expiresAt == 0L) return true
        // 预留 30 秒缓冲，避免边界时刻请求到服务端已过期
        return System.currentTimeMillis() >= expiresAt - 30_000L
    }

    fun saveLoginState(isLoggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun saveUserId(userId: String) { prefs.edit().putString(KEY_USER_ID, userId).apply() }
    fun getUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""
    fun saveUserRole(role: String) { prefs.edit().putString(KEY_USER_ROLE, role).apply() }
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "GUEST") ?: "GUEST"

    /**
     * 只清除 token 相关 key（access / refresh / expires_at / is_logged_in），
     * 保留 user_id / user_role 等用户偏好。
     */
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_IS_LOGGED_IN)
            .apply()
    }

    /**
     * 清空全部存储。
     * @deprecated 新代码应使用 [clearTokens] 以保留用户偏好；仅在需要完全重置时使用。
     */
    @Deprecated("使用 clearTokens() 以保留用户偏好，仅完全重置时调用本方法", ReplaceWith("clearTokens()"))
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ROLE = "user_role"
    }
}
