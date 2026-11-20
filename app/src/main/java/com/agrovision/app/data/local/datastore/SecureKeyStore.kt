package com.agrovision.app.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * 敏感 Key 安全存储 — 基于 EncryptedSharedPreferences（AES256-GCM）
 *
 * 用于加密存储 API Key 等敏感凭据，避免明文落在 DataStore。
 * 与 [TokenManager] 共用同一套加密方案（Jetpack Security Crypto），但使用独立 prefs 文件
 * 以便清理时互不影响（如「退出登录」只清 token，「修改 API Key」只清 key）。
 *
 * 线程安全：[SharedPreferences] 的 `getString/edit/apply` 内部已同步，可直接在任意线程调用。
 */
@Singleton
class SecureKeyStore(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "agrovision_secure_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val KEY_API_KEY = "llm_api_key"
    }
}
