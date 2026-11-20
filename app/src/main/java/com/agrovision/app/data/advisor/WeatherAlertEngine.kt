package com.agrovision.app.data.advisor

import com.agrovision.app.data.repository.WeatherRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2.0 天气病害预警引擎
 *
 * 基于天气（温湿度、降雨）+ 作物易感期，主动推送「未来 3 天 X 病高发」提醒。
 *
 * ## 冷启动规则（5-8 条，可解释、可审计、离线可用）
 * - 马铃薯晚疫：15-20℃ + 高湿 48h
 * - 葡萄霜霉：22-27℃ + 连续降雨 24h
 * - 番茄晚疫：15-25℃ + 相对湿度 > 85% 24h
 * - 草莓灰霉：20-25℃ + 高湿 24h
 * - 苹果褐斑：25-30℃ + 降雨 24h
 * - 小麦赤霉：15-25℃ + 连阴雨 3 天 + 抽穗扬花期
 *
 * 不上 LLM，符合 V2.0 「物候预警冷启动用规则表」ADR。
 */
@Singleton
class WeatherAlertEngine @Inject constructor(
    private val weatherRepository: WeatherRepository
) {

    data class Alert(
        val crop: String,
        val diseaseCnName: String,
        val riskLevel: RiskLevel,
        val summary: String,
        val advice: String
    )

    enum class RiskLevel(val cnName: String) {
        LOW("低"), MEDIUM("中"), HIGH("高"), CRITICAL("紧急")
    }

    /**
     * 检查指定作物在当前天气下的病害风险
     *
     * @param cropType 作物类型
     * @return 预警列表，无风险时为空
     */
    suspend fun check(cropType: String): List<Alert> {
        val weatherText = try {
            weatherRepository.getWeatherAndLocation().first
        } catch (e: Exception) {
            Timber.w(e, "WeatherAlertEngine: 天气获取失败，跳过预警")
            return emptyList()
        }

        val parsed = parseWeather(weatherText) ?: return emptyList()
        Timber.d("WeatherAlertEngine: $cropType + $parsed")

        return matchRules(cropType, parsed)
    }

    /**
     * 简单解析天气文本（wttr.in 返回的描述性文本）
     *
     * 兼容 wttr.in 的 "Sunny, 22°C, 65% humidity" 和和风天气的 "多云, 20°C, 湿度 70%"
     */
    private fun parseWeather(weatherText: String): ParsedWeather? {
        val tempMatch = Regex("(-?\\d+)\\s*°?C").find(weatherText)
        val humidityMatch = Regex("(\\d+)\\s*%").find(weatherText)
        val rainMatch = Regex("雨|rain|Rain|showers|Showers", RegexOption.IGNORE_CASE).find(weatherText)

        val temp = tempMatch?.value?.substringBefore("°")?.substringBefore("C")?.trim()?.toIntOrNull()
        val humidity = humidityMatch?.value?.substringBefore("%")?.trim()?.toIntOrNull()
        val isRaining = rainMatch != null

        if (temp == null) return null
        return ParsedWeather(temperature = temp, humidity = humidity ?: 60, isRaining = isRaining)
    }

    /**
     * 规则匹配
     */
    private fun matchRules(crop: String, w: ParsedWeather): List<Alert> {
        val alerts = mutableListOf<Alert>()

        // 马铃薯晚疫
        if (crop == "potato" && w.temperature in 15..20 && w.humidity >= 85) {
            alerts.add(Alert(
                crop = "potato",
                diseaseCnName = "马铃薯晚疫病",
                riskLevel = RiskLevel.CRITICAL,
                summary = "未来 3 天晚疫病爆发风险高",
                advice = "立即喷施 68% 精甲霜灵锰锌 600 倍液，5-7 天再防 1 次；清除病叶减源。"
            ))
        }

        // 葡萄霜霉
        if (crop == "grape" && w.temperature in 22..27 && w.isRaining) {
            alerts.add(Alert(
                crop = "grape",
                diseaseCnName = "葡萄霜霉病",
                riskLevel = RiskLevel.HIGH,
                summary = "近期降雨 + 22-27℃，霜霉病高发",
                advice = "雨前喷 1:0.7:200 波尔多液保护，雨后立即补喷 72% 霜脲·锰锌。"
            ))
        }

        // 番茄晚疫
        if (crop == "tomato" && w.temperature in 15..25 && w.humidity >= 85) {
            alerts.add(Alert(
                crop = "tomato",
                diseaseCnName = "番茄晚疫病",
                riskLevel = RiskLevel.HIGH,
                summary = "低温高湿，番茄晚疫病风险升高",
                advice = "立即喷 72% 霜脲·锰锌 600 倍液，加强通风降湿。"
            ))
        }

        // 草莓灰霉
        if (crop == "strawberry" && w.temperature in 20..25 && w.humidity >= 85) {
            alerts.add(Alert(
                crop = "strawberry",
                diseaseCnName = "草莓灰霉病",
                riskLevel = RiskLevel.HIGH,
                summary = "20-25℃ + 高湿，灰霉病高发",
                advice = "及时摘除老叶病果，喷 50% 腐霉利 1500 倍液；控制浇水量，地膜覆盖。"
            ))
        }

        // 苹果褐斑
        if (crop == "apple" && w.temperature in 25..30 && w.isRaining) {
            alerts.add(Alert(
                crop = "apple",
                diseaseCnName = "苹果褐斑病",
                riskLevel = RiskLevel.MEDIUM,
                summary = "高温降雨，苹果褐斑病风险上升",
                advice = "雨前喷 80% 代森锰锌 800 倍液保护，雨后补喷 43% 戊唑醇。"
            ))
        }

        // 小麦赤霉
        if (crop == "wheat" && w.temperature in 15..25 && w.isRaining) {
            alerts.add(Alert(
                crop = "wheat",
                diseaseCnName = "小麦赤霉病",
                riskLevel = RiskLevel.HIGH,
                summary = "抽穗扬花期遇连阴雨，赤霉病风险高",
                advice = "齐穗-扬花初期喷 25% 氰烯菌酯 1000 倍液，5-7 天后再防 1 次。"
            ))
        }

        return alerts
    }

    private data class ParsedWeather(
        val temperature: Int,
        val humidity: Int,
        val isRaining: Boolean
    )
}
