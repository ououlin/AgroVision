package com.agrovision.app.domain.advisor

import kotlin.math.ceil

data class SprayCalculation(
    val totalProduct: Double,
    val totalWaterLiters: Double,
    val tankCount: Int,
    val fullTankProduct: Double,
    val lastTankWaterLiters: Double,
    val lastTankProduct: Double
)

object SprayCalculator {
    fun calculate(
        areaMu: Double,
        labelDosePerMu: Double,
        waterPerMuLiters: Double,
        tankCapacityLiters: Double
    ): SprayCalculation {
        require(areaMu > 0) { "作业面积必须大于 0" }
        require(labelDosePerMu > 0) { "标签用量必须大于 0" }
        require(waterPerMuLiters > 0) { "亩用水量必须大于 0" }
        require(tankCapacityLiters > 0) { "药箱容量必须大于 0" }

        val totalProduct = areaMu * labelDosePerMu
        val totalWater = areaMu * waterPerMuLiters
        val tankCount = ceil(totalWater / tankCapacityLiters).toInt().coerceAtLeast(1)
        val fullTankProduct = labelDosePerMu * tankCapacityLiters / waterPerMuLiters
        val lastTankWater = totalWater - tankCapacityLiters * (tankCount - 1)
        val lastTankProduct = labelDosePerMu * lastTankWater / waterPerMuLiters

        return SprayCalculation(
            totalProduct = totalProduct,
            totalWaterLiters = totalWater,
            tankCount = tankCount,
            fullTankProduct = fullTankProduct,
            lastTankWaterLiters = lastTankWater,
            lastTankProduct = lastTankProduct
        )
    }
}
