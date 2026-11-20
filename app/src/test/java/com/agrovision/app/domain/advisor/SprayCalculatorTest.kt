package com.agrovision.app.domain.advisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SprayCalculatorTest {
    @Test
    fun `calculates full tanks and a partial final tank`() {
        val result = SprayCalculator.calculate(
            areaMu = 10.0,
            labelDosePerMu = 30.0,
            waterPerMuLiters = 30.0,
            tankCapacityLiters = 16.0
        )

        assertEquals(300.0, result.totalProduct, 0.001)
        assertEquals(300.0, result.totalWaterLiters, 0.001)
        assertEquals(19, result.tankCount)
        assertEquals(16.0, result.fullTankProduct, 0.001)
        assertEquals(12.0, result.lastTankWaterLiters, 0.001)
        assertEquals(12.0, result.lastTankProduct, 0.001)
    }

    @Test
    fun `one small job uses one partial tank`() {
        val result = SprayCalculator.calculate(0.5, 20.0, 30.0, 16.0)

        assertEquals(1, result.tankCount)
        assertEquals(15.0, result.lastTankWaterLiters, 0.001)
        assertEquals(10.0, result.lastTankProduct, 0.001)
    }

    @Test
    fun `rejects zero or negative inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            SprayCalculator.calculate(0.0, 20.0, 30.0, 16.0)
        }
    }
}
