package com.agrovision.app.data.remote

import retrofit2.http.*
import com.google.gson.annotations.SerializedName

/**
 * AgroVision 云端 API 接口 — Retrofit
 */
interface AgroVisionApi {

    /** 上传检测记录 */
    @POST("api/v2/detections")
    suspend fun uploadDetection(@Body request: DetectionSyncRequest): ApiResponse<SyncResult>

    /** 批量上传 */
    @POST("api/v2/detections/batch")
    suspend fun uploadDetections(@Body requests: List<DetectionSyncRequest>): ApiResponse<SyncResult>

    /** 下载云端记录 */
    @GET("api/v2/detections")
    suspend fun getDetections(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<DetectionSyncPage>

    /** 获取统计概览 */
    @GET("api/v2/stats/overview")
    suspend fun getStatsOverview(): ApiResponse<StatsResponse>

    /** 检查模型更新 */
    @GET("api/v2/models/check")
    suspend fun checkModelUpdate(
        @Query("current_version") version: String
    ): ApiResponse<ModelUpdateResponse>

    /** 健康检查 */
    @GET("api/health")
    suspend fun healthCheck(): ApiResponse<HealthStatus>

    // ============ V2.0 地块 + 用药记录同步 ============

    /** 上传地块元数据（新增或更新） */
    @POST("api/v2/plots")
    suspend fun upsertPlot(@Body request: PlotSyncRequest): ApiResponse<PlotSyncResult>

    /** 批量上传地块 */
    @POST("api/v2/plots/batch")
    suspend fun upsertPlots(@Body requests: List<PlotSyncRequest>): ApiResponse<PlotSyncResult>

    /** 拉取云端地块列表（换机恢复） */
    @GET("api/v2/plots")
    suspend fun getPlots(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): ApiResponse<PlotSyncPage>

    /** 上传用药记录 */
    @POST("api/v2/treatments")
    suspend fun upsertTreatment(@Body request: TreatmentSyncRequest): ApiResponse<TreatmentSyncResult>

    /** 批量上传用药记录 */
    @POST("api/v2/treatments/batch")
    suspend fun upsertTreatments(@Body requests: List<TreatmentSyncRequest>): ApiResponse<TreatmentSyncResult>

    /** 拉取云端用药记录 */
    @GET("api/v2/treatments")
    suspend fun getTreatments(
        @Query("plot_id") plotId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): ApiResponse<TreatmentSyncPage>
}

// ===== V2.0 Plot / Treatment DTO =====

/**
 * 地块同步请求（不含原图，只含元数据）
 *
 * @param remoteId 云端 ID（更新时填，新增时空字符串）
 * @param name 地块名称
 * @param cropType 作物类型
 * @param area 面积（亩，可空）
 * @param longitude 经度（可空）
 * @param latitude 纬度（可空）
 * @param phenologyStage 物候期
 * @param note 备注
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 * @param isDeleted 是否软删除（用于云端对齐）
 */
data class PlotSyncRequest(
    @SerializedName("remote_id") val remoteId: String = "",
    @SerializedName("name") val name: String,
    @SerializedName("crop_type") val cropType: String,
    @SerializedName("area") val area: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("phenology_stage") val phenologyStage: String = "",
    @SerializedName("note") val note: String = "",
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("is_deleted") val isDeleted: Boolean = false
)

data class PlotSyncResult(
    @SerializedName("synced") val synced: Int = 0,
    @SerializedName("failed") val failed: Int = 0,
    @SerializedName("ids") val ids: List<RemoteIdMapping> = emptyList()
)

/** 本地 ID ↔ 云端 ID 映射（同步后回填） */
data class RemoteIdMapping(
    @SerializedName("local_id") val localId: Long,
    @SerializedName("remote_id") val remoteId: String
)

data class PlotSyncPage(
    @SerializedName("items") val items: List<PlotSyncItem> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1
)

data class PlotSyncItem(
    @SerializedName("remote_id") val remoteId: String,
    @SerializedName("name") val name: String,
    @SerializedName("crop_type") val cropType: String,
    @SerializedName("area") val area: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("phenology_stage") val phenologyStage: String = "",
    @SerializedName("note") val note: String = "",
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long
)

/**
 * 用药记录同步请求
 *
 * @param remote_id 云端 ID
 * @param plot_remote_id 关联地块的云端 ID（不在本地保留 plotId 本地 ID，由同步逻辑查找）
 * @param history_remote_id 关联识别历史的云端 ID（可空）
 * @param chemical_name 药剂名称
 * @param dosage 用量
 * @param applied_at 施用日期
 * @param safety_interval_days 安全间隔期天数
 * @param expected_harvest_at 预计采收日期（可空）
 * @param status 状态
 * @param note 备注
 * @param created_at 创建时间戳
 * @param is_deleted 是否软删除
 */
data class TreatmentSyncRequest(
    @SerializedName("remote_id") val remoteId: String = "",
    @SerializedName("plot_remote_id") val plotRemoteId: String,
    @SerializedName("history_remote_id") val historyRemoteId: String? = null,
    @SerializedName("chemical_name") val chemicalName: String,
    @SerializedName("dosage") val dosage: String,
    @SerializedName("applied_at") val appliedAt: Long,
    @SerializedName("safety_interval_days") val safetyIntervalDays: Int = 0,
    @SerializedName("expected_harvest_at") val expectedHarvestAt: Long? = null,
    @SerializedName("status") val status: String = "applied",
    @SerializedName("note") val note: String = "",
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("is_deleted") val isDeleted: Boolean = false
)

data class TreatmentSyncResult(
    @SerializedName("synced") val synced: Int = 0,
    @SerializedName("failed") val failed: Int = 0,
    @SerializedName("ids") val ids: List<RemoteIdMapping> = emptyList()
)

data class TreatmentSyncPage(
    @SerializedName("items") val items: List<TreatmentSyncItem> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1
)

data class TreatmentSyncItem(
    @SerializedName("remote_id") val remoteId: String,
    @SerializedName("plot_remote_id") val plotRemoteId: String,
    @SerializedName("chemical_name") val chemicalName: String,
    @SerializedName("dosage") val dosage: String,
    @SerializedName("applied_at") val appliedAt: Long,
    @SerializedName("safety_interval_days") val safetyIntervalDays: Int = 0,
    @SerializedName("expected_harvest_at") val expectedHarvestAt: Long? = null,
    @SerializedName("status") val status: String = "applied",
    @SerializedName("note") val note: String = "",
    @SerializedName("created_at") val createdAt: Long
)

// ===== Request DTO =====
data class DetectionSyncRequest(
    @SerializedName("image_path") val imagePath: String = "",
    @SerializedName("disease_name_cn") val diseaseNameCn: String = "",
    @SerializedName("disease_name_en") val diseaseNameEn: String = "",
    @SerializedName("confidence") val confidence: Float = 0f,
    @SerializedName("timestamp") val timestamp: Long = 0L,
    @SerializedName("device_id") val deviceId: String = ""
)

// ===== Response DTO =====
data class ApiResponse<T>(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: String = "",
    @SerializedName("data") val data: T? = null
)

data class SyncResult(
    @SerializedName("synced") val synced: Int = 0,
    @SerializedName("failed") val failed: Int = 0
)

data class DetectionSyncItem(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("disease_name_cn") val diseaseNameCn: String = "",
    @SerializedName("confidence") val confidence: Float = 0f,
    @SerializedName("timestamp") val timestamp: Long = 0L
)

data class DetectionSyncPage(
    @SerializedName("items") val items: List<DetectionSyncItem> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1
)

data class StatsResponse(
    @SerializedName("total_detections") val totalDetections: Int = 0,
    @SerializedName("accuracy_rate") val accuracyRate: Float = 0f,
    @SerializedName("top_diseases") val topDiseases: List<DiseaseStat> = emptyList(),
    @SerializedName("monthly_trend") val monthlyTrend: List<MonthCount> = emptyList()
)

data class DiseaseStat(
    @SerializedName("name") val name: String = "",
    @SerializedName("count") val count: Int = 0
)

data class MonthCount(
    @SerializedName("month") val month: String = "",
    @SerializedName("count") val count: Int = 0
)

data class ModelUpdateResponse(
    @SerializedName("has_update") val hasUpdate: Boolean = false,
    @SerializedName("latest_version") val latestVersion: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("change_log") val changeLog: String = "",
    @SerializedName("file_size") val fileSize: String = ""
)

data class HealthStatus(
    @SerializedName("status") val status: String = "ok",
    @SerializedName("version") val version: String = ""
)
