package com.agrovision.app.data.remote

import retrofit2.http.*
import com.google.gson.annotations.SerializedName

/**
 * 认证 API 接口
 */
interface AuthApi {

    @POST("api/v2/auth/sendCode")
    suspend fun sendCode(@Body request: SendCodeRequest): ApiResponse<CodeResult>

    @POST("api/v2/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResult>

    @POST("api/v2/auth/refreshToken")
    suspend fun refreshToken(@Body request: RefreshRequest): ApiResponse<LoginResult>

    @POST("api/v2/auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @GET("api/v2/user/info")
    suspend fun getUserInfo(): ApiResponse<UserInfoDto>

    @PUT("api/v2/user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<UserInfoDto>
}

// ===== Request =====
data class SendCodeRequest(
    @SerializedName("phone") val phone: String
)

data class LoginRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class UpdateProfileRequest(
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

// ===== Response =====
data class CodeResult(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("expire_in") val expireIn: Int = 60
)

data class LoginResult(
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("phone") val phone: String = "",
    @SerializedName("avatar") val avatar: String = "",
    @SerializedName("role") val role: String = "USER",
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("expire_in") val expireIn: Long = 7200
)

data class UserInfoDto(
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("phone") val phone: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("avatar") val avatar: String = "",
    @SerializedName("role") val role: String = "USER",
    @SerializedName("vip") val vip: Boolean = false
)
