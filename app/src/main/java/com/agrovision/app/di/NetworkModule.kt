package com.agrovision.app.di

import android.content.Context
import com.agrovision.app.BuildConfig
import com.agrovision.app.data.local.datastore.SecureKeyStore
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.local.datastore.TokenManager
import com.agrovision.app.data.remote.AgroVisionApi
import com.agrovision.app.data.remote.AuthApi
import com.agrovision.app.data.remote.SyncService
import com.agrovision.app.data.repository.AuthRepository
import com.agrovision.app.network.AuthInterceptor
import com.agrovision.app.network.TokenAuthenticator
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网络 DI 模块 — OkHttp、Retrofit、API 接口、云同步、认证与设置存储。
 *
 * 双层 OkHttpClient 设计：
 * - **bareOkHttpClient**：仅含日志拦截器，**不挂载 AuthInterceptor / TokenAuthenticator**。
 *   供 [AuthApi]（登录/刷新/登出接口）使用，避免认证接口自身 401 时形成递归刷新死循环。
 * - **authOkHttpClient**：挂载 [AuthInterceptor] + [TokenAuthenticator]，供业务 API（[AgroVisionApi]）使用。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** BASE_URL 由 build.gradle.kts 的 buildConfigField 注入，区分 debug/release */
    private val BASE_URL: String = BuildConfig.BASE_URL

    // ===== OkHttpClient =====

    /**
     * 裸 OkHttpClient — 仅日志，无认证拦截。
     * 供 AuthApi（登录/refreshToken/logout）使用，避免 401 递归刷新。
     */
    @Provides
    @Singleton
    @Named("bareOkHttpClient")
    fun provideBareOkHttpClient(): OkHttpClient {
        val logging = loggingInterceptor()
        return baseBuilder()
            .addInterceptor(logging)
            .build()
    }

    /**
     * 默认 OkHttpClient（无限定符）— 委托给 bareOkHttpClient。
     * 供 LLM Provider、DevCenterViewModel、CacheManager 等需要通用 HTTP 客户端的组件使用。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@Named("bareOkHttpClient") client: OkHttpClient): OkHttpClient = client

    /**
     * 认证 OkHttpClient — 挂载 AuthInterceptor（注入 Bearer）+ TokenAuthenticator（401 自动刷新）。
     * 供业务 API（AgroVisionApi）使用。
     */
    @Provides
    @Singleton
    @Named("authOkHttpClient")
    fun provideAuthOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = loggingInterceptor()
        return baseBuilder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()
    }

    // ===== Retrofit =====

    /**
     * 裸 Retrofit — 基于 bareOkHttpClient，供 AuthApi 使用。
     */
    @Provides
    @Singleton
    @Named("bareRetrofit")
    fun provideBareRetrofit(
        @Named("bareOkHttpClient") okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    /**
     * 认证 Retrofit — 基于 authOkHttpClient，供业务 API 使用。
     */
    @Provides
    @Singleton
    @Named("authRetrofit")
    fun provideAuthRetrofit(
        @Named("authOkHttpClient") okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    // ===== API 接口 =====

    @Provides
    @Singleton
    fun provideAgroVisionApi(@Named("authRetrofit") retrofit: Retrofit): AgroVisionApi =
        retrofit.create(AgroVisionApi::class.java)

    /**
     * AuthApi 使用 bareRetrofit —— 登录/刷新接口不应被 AuthInterceptor 拦截，
     * 否则 refresh 接口 401 会触发 TokenAuthenticator 再次调 refresh 形成死循环。
     */
    @Provides
    @Singleton
    fun provideAuthApi(@Named("bareRetrofit") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    /**
     * bareAuthApi — 供 TokenAuthenticator 内部使用，与 [provideAuthApi] 同源（bareRetrofit），
     * 但作为独立注入点便于测试和语义区分。
     */
    @Provides
    @Singleton
    @Named("bareAuthApi")
    fun provideBareAuthApi(@Named("bareRetrofit") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideSyncService(
        api: AgroVisionApi,
        historyDao: com.agrovision.app.data.local.dao.HistoryDao,
        plotDao: com.agrovision.app.data.local.dao.PlotDao,
        treatmentDao: com.agrovision.app.data.local.dao.TreatmentDao
    ): SyncService = SyncService(api, historyDao, plotDao, treatmentDao)

    // ===== 认证 & 设置 =====
    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideAuthRepository(
        tokenManager: TokenManager,
        authApi: AuthApi
    ): AuthRepository = AuthRepository(tokenManager, authApi)

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        secureKeyStore: SecureKeyStore
    ): SettingsDataStore = SettingsDataStore(context, secureKeyStore)

    @Provides
    @Singleton
    fun provideSecureKeyStore(
        @ApplicationContext context: Context
    ): SecureKeyStore = SecureKeyStore(context)

    // ===== 私有工具 =====

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // Release 包禁止打印请求/响应明文，防止 Token 等敏感信息泄露
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                    else HttpLoggingInterceptor.Level.NONE
            // 脱敏 Authorization 头，避免 API Key 明文打到 logcat
            redactHeader("Authorization")
            redactHeader("x-api-key")
        }

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
}
