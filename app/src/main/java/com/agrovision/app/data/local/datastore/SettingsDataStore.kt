package com.agrovision.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Singleton

/**
 * DataStore 名称：
 *   - 正式迁移后：使用 DataStore("agrovision_settings.preferences_pb")
 *   - 迁移来源：旧 SharedPreferences 文件 "agrovision_settings.xml"（如果存在）
 *
 * 注意：因为 DataStore 的 SharedPreferencesMigration 在不同版本有不同签名：
 *   1) 旧版（1.0.0-rc02 以前）：
 *      fun SharedPreferencesMigration(ctx, name, shouldMigrate = { true }, keyMapping = { null })
 *      —— shouldMigrate / keyMapping 是函数类型
 *   2) 新版（1.1+）：
 *      fun SharedPreferencesMigration(ctx, name, keyMapping = emptyMap(), ...)
 *      —— keyMapping 变成 Map<String, Preferences.Key<*>>
 *
 * 为了在项目依赖的版本中 100% 编译通过，下面采用最通用的"两参数"构造：
 *   `SharedPreferencesMigration(context = ..., sharedPreferencesName = ...)`
 *   —— 这个两参数构造在 alpha 到 1.1.x 的**所有版本中都存在**，迁移全部 key，行为一致。
 */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "agrovision_settings",
    produceMigrations = { ctx ->
        listOf(
            // 迁移旧 SharedPreferences XML → DataStore（主文件）
            SharedPreferencesMigration(ctx, "agrovision_settings"),
            // 兜底：迁移用户目录下另一种常见命名（兼容老版本包目录默认命名）
            SharedPreferencesMigration(ctx, "${ctx.packageName}_preferences")
        )
    }
)

data class AppSettings(
    // AI Provider
    val aiProvider: String = "deepseek",
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val aiModel: String = "deepseek-chat",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 4096,
    val contextLength: Int = 8192,
    val enableWebSearch: Boolean = false,
    val enableRag: Boolean = true,
    val enableOcr: Boolean = true,
    val enableImageVision: Boolean = false,
    val enableStreaming: Boolean = true,

    // YOLO
    val yoloModel: String = "yolo11n",
    val yoloInferenceMode: String = "auto",
    val yoloInputSize: String = "640",
    val yoloConfidence: Float = 0.4f,
    val yoloNms: Float = 0.5f,
    val yoloShowFps: Boolean = true,
    val yoloShowBox: Boolean = true,
    val yoloShowLabel: Boolean = true,

    // Server
    val serverBaseUrl: String = "",
    val serverEnabled: Boolean = false,

    // Robot
    val rosIp: String = "",
    val rosPort: String = "9090",
    val mqttBroker: String = "",
    val robotName: String = "go2",

    // Map
    val gpsRecordTrack: Boolean = false,
    val heatmapEnabled: Boolean = true,

    // Data Sync
    val autoSync: Boolean = false,
    val syncWifiOnly: Boolean = true,
    val syncMobileNetwork: Boolean = false,
    val syncUploadImages: Boolean = true,
    val syncDownloadModel: Boolean = false,

    // Theme
    val themeMode: String = "system",
    val materialYou: Boolean = true,
    val fontSize: String = "normal",
    val language: String = "zh",
    val animationsEnabled: Boolean = true,

    // Developer
    val devMode: Boolean = false,
    val showFps: Boolean = false,
    val showGpuStatus: Boolean = false,

    // User
    val nickname: String = "",
    val email: String = "",

    // Weather
    val weatherApiKey: String = "",

    // ===== 合规：隐私协议签署状态（首次启动勾选后置 true，App 启动时必须检查）=====
    val privacyAgreed: Boolean = false
)

@Singleton
class SettingsDataStore(
    @ApplicationContext private val context: Context,
    private val secureKeyStore: SecureKeyStore
) {
    companion object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val API_KEY = stringPreferencesKey("api_key") // 保留用于兼容旧版本读取，新写入走 SecureKeyStore
        val API_KEY_VERSION = longPreferencesKey("api_key_version") // 触发 settings Flow 重新发射
        val BASE_URL = stringPreferencesKey("base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val ENABLE_RAG = booleanPreferencesKey("enable_rag")
        val ENABLE_OCR = booleanPreferencesKey("enable_ocr")
        val ENABLE_IMAGE_VISION = booleanPreferencesKey("enable_image_vision")
        val ENABLE_STREAMING = booleanPreferencesKey("enable_streaming")

        val YOLO_MODEL = stringPreferencesKey("yolo_model")
        val YOLO_INFERENCE_MODE = stringPreferencesKey("yolo_inference_mode")
        val YOLO_INPUT_SIZE = stringPreferencesKey("yolo_input_size")
        val YOLO_CONFIDENCE = floatPreferencesKey("yolo_confidence")
        val YOLO_NMS = floatPreferencesKey("yolo_nms")
        val YOLO_SHOW_FPS = booleanPreferencesKey("yolo_show_fps")
        val YOLO_SHOW_BOX = booleanPreferencesKey("yolo_show_box")
        val YOLO_SHOW_LABEL = booleanPreferencesKey("yolo_show_label")

        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val SERVER_ENABLED = booleanPreferencesKey("server_enabled")

        val ROS_IP = stringPreferencesKey("ros_ip")
        val ROS_PORT = stringPreferencesKey("ros_port")
        val MQTT_BROKER = stringPreferencesKey("mqtt_broker")
        val ROBOT_NAME = stringPreferencesKey("robot_name")

        val GPS_RECORD_TRACK = booleanPreferencesKey("gps_record_track")
        val HEATMAP_ENABLED = booleanPreferencesKey("heatmap_enabled")

        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val SYNC_MOBILE = booleanPreferencesKey("sync_mobile")
        val SYNC_UPLOAD_IMAGES = booleanPreferencesKey("sync_upload_images")
        val SYNC_DOWNLOAD_MODEL = booleanPreferencesKey("sync_download_model")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val LANGUAGE = stringPreferencesKey("language")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")

        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val SHOW_GPU = booleanPreferencesKey("show_gpu")

        val NICKNAME = stringPreferencesKey("nickname")
        val EMAIL = stringPreferencesKey("email")

        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")

        // ===== 合规：隐私协议签署状态（必须持久化，不得仅存内存）=====
        val PRIVACY_AGREED = booleanPreferencesKey("privacy_agreed")

        // AI Provider 列表（JSON 序列化）
        val PROVIDERS = stringPreferencesKey("ai_providers_json")

        // AI 对话历史（JSON 序列化，最近 20 条）
        val CHAT_HISTORY = stringPreferencesKey("chat_history_json")
    }

    private val gson = Gson()

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            aiProvider = prefs[Keys.AI_PROVIDER] ?: "deepseek",
            // API Key 从加密存储读取（不再明文存 DataStore）
            apiKey = secureKeyStore.getApiKey(),
            baseUrl = prefs[Keys.BASE_URL] ?: "https://api.deepseek.com",
            aiModel = prefs[Keys.AI_MODEL] ?: "deepseek-chat",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            topP = prefs[Keys.TOP_P] ?: 0.9f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 4096,
            contextLength = prefs[Keys.CONTEXT_LENGTH] ?: 8192,
            enableWebSearch = prefs[Keys.ENABLE_WEB_SEARCH] ?: false,
            enableRag = prefs[Keys.ENABLE_RAG] ?: true,
            enableOcr = prefs[Keys.ENABLE_OCR] ?: true,
            enableImageVision = prefs[Keys.ENABLE_IMAGE_VISION] ?: false,
            enableStreaming = prefs[Keys.ENABLE_STREAMING] ?: true,
            yoloModel = prefs[Keys.YOLO_MODEL] ?: "yolo11n",
            yoloInferenceMode = prefs[Keys.YOLO_INFERENCE_MODE] ?: "auto",
            yoloInputSize = prefs[Keys.YOLO_INPUT_SIZE] ?: "640",
            yoloConfidence = prefs[Keys.YOLO_CONFIDENCE] ?: 0.4f,
            yoloNms = prefs[Keys.YOLO_NMS] ?: 0.5f,
            yoloShowFps = prefs[Keys.YOLO_SHOW_FPS] ?: true,
            yoloShowBox = prefs[Keys.YOLO_SHOW_BOX] ?: true,
            yoloShowLabel = prefs[Keys.YOLO_SHOW_LABEL] ?: true,
            serverBaseUrl = prefs[Keys.SERVER_BASE_URL] ?: "",
            serverEnabled = prefs[Keys.SERVER_ENABLED] ?: false,
            rosIp = prefs[Keys.ROS_IP] ?: "",
            rosPort = prefs[Keys.ROS_PORT] ?: "9090",
            mqttBroker = prefs[Keys.MQTT_BROKER] ?: "",
            robotName = prefs[Keys.ROBOT_NAME] ?: "go2",
            gpsRecordTrack = prefs[Keys.GPS_RECORD_TRACK] ?: false,
            heatmapEnabled = prefs[Keys.HEATMAP_ENABLED] ?: true,
            autoSync = prefs[Keys.AUTO_SYNC] ?: false,
            syncWifiOnly = prefs[Keys.SYNC_WIFI_ONLY] ?: true,
            syncMobileNetwork = prefs[Keys.SYNC_MOBILE] ?: false,
            syncUploadImages = prefs[Keys.SYNC_UPLOAD_IMAGES] ?: true,
            syncDownloadModel = prefs[Keys.SYNC_DOWNLOAD_MODEL] ?: false,
            themeMode = prefs[Keys.THEME_MODE] ?: "system",
            materialYou = prefs[Keys.MATERIAL_YOU] ?: true,
            fontSize = prefs[Keys.FONT_SIZE] ?: "normal",
            language = prefs[Keys.LANGUAGE] ?: "zh",
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            devMode = prefs[Keys.DEV_MODE] ?: false,
            showFps = prefs[Keys.SHOW_FPS] ?: false,
            showGpuStatus = prefs[Keys.SHOW_GPU] ?: false,
            nickname = prefs[Keys.NICKNAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            weatherApiKey = prefs[Keys.WEATHER_API_KEY] ?: "",
            privacyAgreed = prefs[Keys.PRIVACY_AGREED] ?: false
        )
    }

    suspend fun updateString(key: Preferences.Key<String>, value: String) {
        context.settingsStore.edit { it[key] = value }
    }

    /**
     * 更新 API Key — 写入加密存储 [SecureKeyStore]，不落 DataStore 明文。
     * 同时翻转一个 trigger 标志位，强制 settings Flow 重新发射，让监听方收到最新 apiKey。
     */
    suspend fun updateApiKey(key: String) {
        secureKeyStore.saveApiKey(key)
        context.settingsStore.edit { prefs ->
            // 翻转时间戳作为触发器，保证 DataStore 发生变更通知
            prefs[Keys.API_KEY_VERSION] = System.currentTimeMillis()
        }
    }

    suspend fun updateFloat(key: Preferences.Key<Float>, value: Float) {
        context.settingsStore.edit { it[key] = value }
    }

    suspend fun updateInt(key: Preferences.Key<Int>, value: Int) {
        context.settingsStore.edit { it[key] = value }
    }

    suspend fun updateBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsStore.edit { it[key] = value }
    }

    /**
     * 合规：设置《用户协议》《隐私政策》已同意。
     * 必须在 WelcomeScreen 用户点击「立即登录」或「游客体验」且勾选 checkbox 后调用；
     * 必须持久化后才允许初始化任何涉及用户隐私的 SDK（定位、相机、ML Kit、网络等）。
     */
    suspend fun setPrivacyAgreed(agreed: Boolean) {
        context.settingsStore.edit { it[Keys.PRIVACY_AGREED] = agreed }
    }

    // ===== AI Provider 列表持久化 =====

    /** 一次性读取持久化的 Provider 列表，无则返回默认列表 */
    suspend fun loadProviders(): List<AiProviderConfig> {
        val json = context.settingsStore.data.first()[Keys.PROVIDERS]
        if (json.isNullOrBlank()) return defaultProviders()
        return try {
            val type = object : TypeToken<List<AiProviderConfig>>() {}.type
            gson.fromJson(json, type) ?: defaultProviders()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse providers json, fallback to defaults")
            defaultProviders()
        }
    }

    /** 持久化 Provider 列表 */
    suspend fun saveProviders(providers: List<AiProviderConfig>) {
        val json = gson.toJson(providers)
        context.settingsStore.edit { it[Keys.PROVIDERS] = json }
    }

    /** 重置 Provider 列表为默认 */
    suspend fun resetProviders() = saveProviders(defaultProviders())

    // ===== AI 对话历史持久化 =====

    /** 读取持久化的对话历史（List<Pair<content, isUser>>） */
    suspend fun loadChatHistory(): List<Pair<String, Boolean>> {
        val json = context.settingsStore.data.first()[Keys.CHAT_HISTORY]
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ChatHistoryEntry>>() {}.type
            val list: List<ChatHistoryEntry> = gson.fromJson(json, type) ?: emptyList()
            list.map { it.content to it.isUser }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse chat history json")
            emptyList()
        }
    }

    /** 持久化对话历史（最多保留最近 20 条） */
    suspend fun saveChatHistory(history: List<Pair<String, Boolean>>) {
        val entries = history.takeLast(20).map { ChatHistoryEntry(it.first, it.second) }
        val json = gson.toJson(entries)
        context.settingsStore.edit { it[Keys.CHAT_HISTORY] = json }
    }

    /** 清空对话历史 */
    suspend fun clearChatHistory() {
        context.settingsStore.edit { it.remove(Keys.CHAT_HISTORY) }
    }

    /** 对话历史序列化 DTO */
    private data class ChatHistoryEntry(
        val content: String,
        val isUser: Boolean
    )

    // ===== 账号相关：清空认证 + 用户数据 =====

    /**
     * 清空用户相关数据（昵称/邮箱/Provider/API Key/服务器配置等）。
     * 用于"注销账号"。不重置主题外观/语言等设备级偏好。
     */
    suspend fun clearUserData() {
        secureKeyStore.clearApiKey()
        context.settingsStore.edit { prefs ->
            prefs.remove(Keys.API_KEY)
            prefs.remove(Keys.API_KEY_VERSION)
            prefs.remove(Keys.BASE_URL)
            prefs.remove(Keys.AI_MODEL)
            prefs.remove(Keys.AI_PROVIDER)
            prefs.remove(Keys.NICKNAME)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.WEATHER_API_KEY)
            prefs.remove(Keys.SERVER_BASE_URL)
            prefs.remove(Keys.SERVER_ENABLED)
            prefs.remove(Keys.PROVIDERS)
            prefs.remove(Keys.CHAT_HISTORY)
            prefs.remove(Keys.ROS_IP)
            prefs.remove(Keys.ROS_PORT)
            prefs.remove(Keys.MQTT_BROKER)
        }
    }

    /**
     * 仅清空认证凭据（退出登录）。保留用户偏好与其他配置。
     */
    suspend fun clearCredentials() {
        secureKeyStore.clearApiKey()
        context.settingsStore.edit { prefs ->
            prefs.remove(Keys.API_KEY)
            prefs.remove(Keys.API_KEY_VERSION)
        }
    }
}
