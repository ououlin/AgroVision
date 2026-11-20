package com.agrovision.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.agrovision.app.data.local.datastore.SettingsDataStore
import com.agrovision.app.data.remote.WeatherResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.Locale
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 天气与定位仓库
 *
 * 支持双数据源：
 * 1. 和风天气 API（用户在设置页配置了 API Key 后使用）
 * 2. wttr.in 免费天气服务（默认兜底，无需 API Key，基于 IP 定位）
 *
 * 定位使用 FusedLocationProviderClient + Geocoder 反查城市名。
 */
@Singleton
class WeatherRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val QWEATHER_BASE_URL = "https://devapi.qweather.com/v7/weather/now"
        private const val WTTR_URL = "https://wttr.in/?format=j1"
        /** wttr.in 带坐标的 URL 模板 */
        private const val WTTR_LOCATION_URL = "https://wttr.in/%s,%s?format=j1"
    }

    private val fusedLocationClient =
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)

    /**
     * 获取天气和定位信息
     * @return Pair(weather, location)
     */
    suspend fun getWeatherAndLocation(): Pair<String, String> {
        val location = withContext(Dispatchers.IO) { getLocationName() }
        val weather = withContext(Dispatchers.IO) { getWeather(location) }
        return weather to location
    }

    // ======================== 定位相关 ========================

    /** 获取设备定位并反查城市名 */
    @SuppressLint("MissingPermission")
    private suspend fun getLocationName(): String {
        if (!hasLocationPermission()) {
            Timber.w("WeatherRepo: 无定位权限")
            return "定位不可用"
        }

        return try {
            val location = withTimeoutOrNull(5000) { getLastLocation() }
            if (location == null) {
                Timber.w("WeatherRepo: lastLocation 返回 null（模拟器常见）")
                return "定位不可用"
            }
            reverseGeocode(location.latitude, location.longitude)
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo: 定位失败")
            "定位不可用"
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { e ->
                Timber.w(e, "WeatherRepo: lastLocation onFailure")
                cont.resume(null)
            }
    }

    /** 通过 Geocoder 将经纬度反查为城市名 */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val result = addresses.firstOrNull()?.let {
                            listOfNotNull(it.locality, it.subLocality, it.adminArea)
                                .firstOrNull() ?: "当前位置"
                        } ?: "当前位置"
                        if (cont.isActive) cont.resume(result)
                    }
                }
            } else {
                val addresses = withTimeoutOrNull(3000) {
                    geocoder.getFromLocation(lat, lng, 1)
                }
                val address = addresses?.firstOrNull()
                address?.let {
                    listOfNotNull(it.locality, it.subLocality, it.adminArea)
                        .firstOrNull() ?: "当前位置"
                } ?: "当前位置"
            }
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo: 反查地址失败")
            "当前位置"
        }
    }

    // ======================== 天气相关 ========================

    /**
     * 获取天气描述文字。
     * 策略：用户配置了和风天气 Key → 调和风 API；否则 → 用 wttr.in 兜底。
     *
     * @param locationText 已获取到的位置文本（如"北京市"），仅用于日志
     */
    private suspend fun getWeather(locationText: String): String {
        // 1. 读取用户配置的和风天气 API Key
        val apiKey = try {
            settingsDataStore.settings.first().weatherApiKey
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo: 读取 weatherApiKey 失败，回退到 wttr.in")
            ""
        }

        // 2. 有和风 Key → 优先调用和风天气 API
        if (apiKey.isNotBlank()) {
            val result = fetchQWeather(apiKey)
            if (result != null) {
                Timber.d("WeatherRepo: 和风天气获取成功: $result")
                return result
            }
            Timber.w("WeatherRepo: 和风天气失败，回退到 wttr.in")
        }

        // 3. 无 Key 或和风失败 → wttr.in 兜底
        return try {
            fetchWttrIn()
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo: wttr.in 也失败了")
            "--"
        }
    }

    /** 调用和风天气 API 获取实时天气 */
    @SuppressLint("MissingPermission")
    private suspend fun fetchQWeather(apiKey: String): String? {
        return try {
            val location = withTimeoutOrNull(5000) { getLastLocation() }
            if (location == null) {
                Timber.w("WeatherRepo[QWeather]: 无法获取坐标")
                return null
            }

            val locationParam = "${location.longitude},${location.latitude}"
            val url = "$QWEATHER_BASE_URL?location=$locationParam&key=$apiKey"

            val request = Request.Builder().url(url).build()
            val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
            if (!response.isSuccessful) {
                Timber.w("WeatherRepo[QWeather]: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null

            val weatherResponse = gson.fromJson(body, WeatherResponse::class.java)
            val now = weatherResponse.now
            if (weatherResponse.code == "200" && now != null) {
                "${now.text} ${now.temp}°C"
            } else {
                Timber.w("WeatherRepo[QWeather]: code=${weatherResponse.code}")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo[QWeather]: 异常")
            null
        }
    }

    /**
     * 调用 wttr.in 免费天气服务（JSON 格式）。
     * 支持基于 IP 自动定位，也可传入坐标提高精度。
     */
    private suspend fun fetchWttrIn(): String {
        return try {
            // 尝试带坐标请求以获得更高精度
            val url = tryBuildWttrUrlWithLocation() ?: WTTR_URL

            Timber.d("WeatherRepo[wttr.in]: 请求 $url")
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "AgroVision/1.0")  // wttr.in 要求合理的 UA
                .build()

            val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
            val body = response.body?.string() ?: return "--"

            if (!response.isSuccessful) {
                Timber.w("WeatherRepo[wttr.in]: HTTP ${response.code}")
                return "--"
            }

            parseWttrInResponse(body)
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo[wttr.in]: 异常")
            "--"
        }
    }

    /** 尝试构建带坐标的 wttr.in URL（需要定位权限且有缓存定位） */
    @SuppressLint("MissingPermission")
    private suspend fun tryBuildWttrUrlWithLocation(): String? {
        if (!hasLocationPermission()) return null
        return try {
            val loc = withTimeoutOrNull(3000) { getLastLocation() } ?: return null
            WTTR_LOCATION_URL.format(loc.latitude, loc.longitude)
        } catch (_: Exception) {
            null
        }
    }

    /** 解析 wttr.in JSON 响应，提取当前天气描述 + 温度 */
    private fun parseWttrInResponse(jsonStr: String): String {
        return try {
            val jsonObj = JsonParser.parseString(jsonStr).asJsonObject
            val currentCondition = jsonObj.getAsJsonArray("current_condition")?.get(0)?.asJsonObject
                ?: run {
                    Timber.w("WeatherRepo[wttr.in]: 无 current_condition 字段")
                    return "--"
                }
            val tempC = currentCondition.get("temp_C")?.asString ?: "--"
            // wttr.in 天气描述数组（中文），取第一个
            val langArr = currentCondition.getAsJsonArray("lang_zh")?.get(0)?.asString
                ?: currentCondition.get("weatherDesc")?.asJsonArray?.get(0)?.asJsonObject?.get("value")?.asString
                ?: "未知"
            "$langArr ${tempC}°C"
        } catch (e: Exception) {
            Timber.w(e, "WeatherRepo[wttr.in]: 解析响应失败")
            "--"
        }
    }

    // ======================== 权限检查 ========================

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
}
