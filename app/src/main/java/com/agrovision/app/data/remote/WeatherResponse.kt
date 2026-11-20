package com.agrovision.app.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 和风天气实时天气响应
 */
data class WeatherResponse(
    val code: String = "",
    val now: WeatherNow? = null
)

data class WeatherNow(
    val text: String = "",
    val temp: String = "",
    @SerializedName("humidity")
    val humidity: String = ""
)
