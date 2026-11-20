package com.agrovision.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PesticideCatalogTest {
    @Test
    fun `product ids are unique`() {
        assertEquals(PesticideCatalog.products.size, PesticideCatalog.products.map { it.id }.distinct().size)
    }

    @Test
    fun `recommended ingredient products sort first`() {
        val results = PesticideCatalog.search(recommendedIngredients = listOf("苯醚甲环唑"))
        assertTrue(results.first().matchesIngredient("苯醚甲环唑"))
    }

    @Test
    fun `search matches registered target`() {
        val results = PesticideCatalog.search(query = "红蜘蛛")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { "红蜘蛛" in it.registeredTargets })
    }
}
