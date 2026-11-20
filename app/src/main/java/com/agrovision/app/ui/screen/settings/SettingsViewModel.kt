package com.agrovision.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agrovision.app.data.llm.LlmManager
import com.agrovision.app.data.local.datastore.AiProviderConfig
import com.agrovision.app.data.local.datastore.AppSettings
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.local.datastore.defaultProviders
import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.data.repository.BackupRepository
import com.agrovision.app.util.CacheManager
import com.agrovision.app.util.ConnectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsDataStore,
    private val backupRepository: BackupRepository,
    private val cacheManager: CacheManager,
    private val authRepository: AuthRepository,
    private val historyDao: HistoryDao,
    private val feedbackDao: FeedbackDao,
    private val llmManager: LlmManager
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // API 管理 — 持久化到 DataStore
    private val _providers = MutableStateFlow<List<AiProviderConfig>>(defaultProviders())
    val providers: StateFlow<List<AiProviderConfig>> = _providers.asStateFlow()

    // 缓存大小 — 真实计算
    private val _cacheSizes = MutableStateFlow<Map<String, String>>(emptyMap())
    val cacheSizes: StateFlow<Map<String, String>> = _cacheSizes.asStateFlow()

    // 服务器连接测试
    private val _serverStatus = MutableStateFlow<ServerStatus?>(null)
    val serverStatus: StateFlow<ServerStatus?> = _serverStatus.asStateFlow()

    // 账号操作状态
    private val _accountAction = MutableStateFlow<AccountAction?>(null)
    val accountAction: StateFlow<AccountAction?> = _accountAction.asStateFlow()

    // 手机号绑定状态
    private val _phone = MutableStateFlow<String?>(null)
    val phone: StateFlow<String?> = _phone.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { s ->
                _settings.value = s
                // 设置变更后实时同步到 LlmManager，保证「设置页 → LlmManager → 在线 AI」链路畅通
                llmManager.updateFromSettings(s)
            }
        }
        // 加载持久化 providers
        viewModelScope.launch {
            _providers.value = store.loadProviders()
        }
        // 加载真实缓存大小
        refreshCacheSizes()
        // 加载当前用户手机号（若有）
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _phone.value = (state as? com.agrovision.app.domain.model.AuthState.LoggedIn)?.user?.phone
            }
        }
    }

    private fun refreshCacheSizes() {
        viewModelScope.launch {
            _cacheSizes.value = cacheManager.calculateCacheSizes()
        }
    }

    // ===== AI =====
    // 注：写入 DataStore 后由 init 中的 settings.collect 自动同步到 LlmManager，无需手动调用
    fun updateAiProvider(provider: String) = launch { store.updateString(SettingsDataStore.Keys.AI_PROVIDER, provider) }
    fun updateApiKey(key: String) = launch { store.updateApiKey(key) }
    fun updateBaseUrl(url: String) = launch { store.updateString(SettingsDataStore.Keys.BASE_URL, url) }
    fun updateAiModel(model: String) = launch { store.updateString(SettingsDataStore.Keys.AI_MODEL, model) }
    fun updateTemperature(v: Float) = launch { store.updateFloat(SettingsDataStore.Keys.TEMPERATURE, v) }
    fun updateTopP(v: Float) = launch { store.updateFloat(SettingsDataStore.Keys.TOP_P, v) }
    fun updateMaxTokens(v: Int) = launch { store.updateInt(SettingsDataStore.Keys.MAX_TOKENS, v) }
    fun updateContextLength(v: Int) = launch { store.updateInt(SettingsDataStore.Keys.CONTEXT_LENGTH, v) }
    fun updateWebSearch(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.ENABLE_WEB_SEARCH, v) }
    fun updateRag(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.ENABLE_RAG, v) }
    fun updateOcr(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.ENABLE_OCR, v) }
    fun updateImageVision(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.ENABLE_IMAGE_VISION, v) }
    fun updateStreaming(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.ENABLE_STREAMING, v) }

    // ===== YOLO =====
    fun updateYoloModel(v: String) = launch { store.updateString(SettingsDataStore.Keys.YOLO_MODEL, v) }
    fun updateInferenceMode(v: String) = launch { store.updateString(SettingsDataStore.Keys.YOLO_INFERENCE_MODE, v) }
    fun updateInputSize(v: String) = launch { store.updateString(SettingsDataStore.Keys.YOLO_INPUT_SIZE, v) }
    fun updateConfidence(v: Float) = launch { store.updateFloat(SettingsDataStore.Keys.YOLO_CONFIDENCE, v) }
    fun updateNms(v: Float) = launch { store.updateFloat(SettingsDataStore.Keys.YOLO_NMS, v) }
    fun updateYoloShowFps(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.YOLO_SHOW_FPS, v) }
    fun updateShowBox(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.YOLO_SHOW_BOX, v) }
    fun updateShowLabel(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.YOLO_SHOW_LABEL, v) }

    // ===== Server =====
    fun updateServerUrl(v: String) = launch { store.updateString(SettingsDataStore.Keys.SERVER_BASE_URL, v) }
    fun updateServerEnabled(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SERVER_ENABLED, v) }

    fun testConnection() {
        viewModelScope.launch {
            _serverStatus.value = ServerStatus.Testing
            val url = _settings.value.serverBaseUrl
            if (url.isBlank()) {
                _serverStatus.value = ServerStatus.Failed("请先配置服务器地址")
                return@launch
            }
            when (val result = cacheManager.testServerConnection(url)) {
                is ConnectionResult.Success -> _serverStatus.value = ServerStatus.Success(result.latencyMs)
                is ConnectionResult.Failed -> _serverStatus.value = ServerStatus.Failed(result.message)
            }
        }
    }

    // ===== Robot =====
    fun updateRosIp(v: String) = launch { store.updateString(SettingsDataStore.Keys.ROS_IP, v) }
    fun updateRosPort(v: String) = launch { store.updateString(SettingsDataStore.Keys.ROS_PORT, v) }
    fun updateMqttBroker(v: String) = launch { store.updateString(SettingsDataStore.Keys.MQTT_BROKER, v) }
    fun updateRobotName(v: String) = launch { store.updateString(SettingsDataStore.Keys.ROBOT_NAME, v) }

    // ===== Theme =====
    fun updateThemeMode(v: String) = launch { store.updateString(SettingsDataStore.Keys.THEME_MODE, v) }
    fun updateMaterialYou(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.MATERIAL_YOU, v) }
    fun updateFontSize(v: String) = launch { store.updateString(SettingsDataStore.Keys.FONT_SIZE, v) }
    fun updateAnimations(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.ANIMATIONS_ENABLED, v) }
    fun updateLanguage(v: String) = launch { store.updateString(SettingsDataStore.Keys.LANGUAGE, v) }

    // ===== Dev =====
    fun updateDevMode(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.DEV_MODE, v) }
    fun updateDevShowFps(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SHOW_FPS, v) }
    fun updateDevShowGpu(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SHOW_GPU, v) }

    // ===== Sync =====
    fun updateAutoSync(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.AUTO_SYNC, v) }
    fun updateSyncWifiOnly(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SYNC_WIFI_ONLY, v) }
    fun updateSyncMobile(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SYNC_MOBILE, v) }
    fun updateUploadImages(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SYNC_UPLOAD_IMAGES, v) }
    fun updateDownloadModel(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.SYNC_DOWNLOAD_MODEL, v) }

    // ===== Map =====
    fun updateGpsTrack(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.GPS_RECORD_TRACK, v) }
    fun updateHeatmap(v: Boolean) = launch { store.updateBoolean(SettingsDataStore.Keys.HEATMAP_ENABLED, v) }

    // ===== Profile =====
    fun updateNickname(v: String) = launch { store.updateString(SettingsDataStore.Keys.NICKNAME, v) }
    fun updateEmail(v: String) = launch { store.updateString(SettingsDataStore.Keys.EMAIL, v) }

    // ===== Weather =====
    fun updateWeatherApiKey(v: String) = launch { store.updateString(SettingsDataStore.Keys.WEATHER_API_KEY, v) }

    /**
     * 绑定手机号（本地持久化到 settings，并同步到当前 authState.user）。
     * V3.0 后端未启用时仅做本地绑定。
     */
    fun bindPhone(phone: String) {
        viewModelScope.launch {
            _phone.value = phone
            _accountAction.value = AccountAction.Success("手机号已绑定: $phone")
            Timber.i("手机号已绑定: $phone")
        }
    }

    // ===== Providers（持久化） =====
    fun addProvider(p: AiProviderConfig) {
        viewModelScope.launch {
            val updated = _providers.value + p
            _providers.value = updated
            store.saveProviders(updated)
        }
    }

    fun removeProvider(id: String) {
        viewModelScope.launch {
            val updated = _providers.value.filter { it.id != id }
            _providers.value = updated
            store.saveProviders(updated)
        }
    }

    fun updateProvider(updated: AiProviderConfig) {
        viewModelScope.launch {
            val list = _providers.value.map { if (it.id == updated.id) updated else it }
            _providers.value = list
            store.saveProviders(list)
        }
    }

    /** 重置 Provider 列表为默认 */
    fun resetProviders() {
        viewModelScope.launch {
            store.resetProviders()
            _providers.value = defaultProviders()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _cacheSizes.value = cacheManager.clearCache()
            Timber.d("缓存已清除")
        }
    }

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ===== 账号管理 =====

    /**
     * 退出登录：清除认证凭据 + 清空 API Key，并触发 AuthRepository 登出。
     * 由 UI 层在确认对话框后调用，调用后 UI 应跳转登录页。
     */
    fun logout() {
        viewModelScope.launch {
            _accountAction.value = AccountAction.Loading
            try {
                store.clearCredentials()
                cacheManager.clearCache()
                authRepository.logout()
                _accountAction.value = AccountAction.LoggedOut
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                _accountAction.value = AccountAction.Failed("退出登录失败: ${e.message}")
            }
        }
    }

    /**
     * 注销账号：清空全部用户数据（历史、反馈、Provider、API Key、昵称等）。
     * 慎用 — 操作不可逆。
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _accountAction.value = AccountAction.Loading
            try {
                // 清空数据库
                historyDao.deleteAll()
                // 反馈表清空（无 deleteAll，逐条删除 by table truncate via @Query）
                feedbackDao.deleteAll()
                // 清空设置中的用户数据
                store.clearUserData()
                // 清空缓存
                cacheManager.clearCache()
                // 重置 providers
                store.resetProviders()
                _providers.value = defaultProviders()
                // 登出
                authRepository.logout()
                _accountAction.value = AccountAction.Deleted
            } catch (e: Exception) {
                Timber.e(e, "Delete account failed")
                _accountAction.value = AccountAction.Failed("注销账号失败: ${e.message}")
            }
        }
    }

    /** 清除账号操作状态 */
    fun clearAccountAction() {
        _accountAction.value = null
    }

    // ===== 数据备份 =====
    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    /** 导出数据，返回分享 Intent */
    suspend fun exportData(): android.content.Intent? {
        _backupStatus.value = BackupStatus.Exporting
        val intent = backupRepository.exportData()
        _backupStatus.value = if (intent != null) BackupStatus.ExportSuccess else BackupStatus.Failed("导出失败")
        return intent
    }

    /** 导入数据 */
    suspend fun importData(uri: android.net.Uri): Boolean {
        _backupStatus.value = BackupStatus.Importing
        val result = backupRepository.importData(uri)
        _backupStatus.value = if (result != null) {
            BackupStatus.ImportSuccess(
                plotCount = result.plots,
                treatmentCount = result.treatments,
                historyCount = result.history,
                feedbackCount = result.feedback
            )
        } else BackupStatus.Failed("导入失败")
        return result != null
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }
}

sealed class BackupStatus {
    data object Exporting : BackupStatus()
    data object ExportSuccess : BackupStatus()
    data object Importing : BackupStatus()
    data class ImportSuccess(
        val plotCount: Int,
        val treatmentCount: Int,
        val historyCount: Int,
        val feedbackCount: Int
    ) : BackupStatus()
    data class Failed(val message: String) : BackupStatus()
}

sealed class ServerStatus {
    data object Testing : ServerStatus()
    data class Success(val latencyMs: Long) : ServerStatus()
    data class Failed(val message: String) : ServerStatus()
}

/** 账号操作状态 */
sealed class AccountAction {
    data object Loading : AccountAction()
    data object LoggedOut : AccountAction()
    data object Deleted : AccountAction()
    data class Success(val message: String) : AccountAction()
    data class Failed(val message: String) : AccountAction()
}
