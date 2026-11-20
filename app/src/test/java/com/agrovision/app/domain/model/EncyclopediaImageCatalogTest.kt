package com.agrovision.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncyclopediaImageCatalogTest {
    @Test
    fun `maps current model label to local asset`() {
        assertEquals(
            "file:///android_asset/encyclopedia/apple_apple_scab.jpg",
            EncyclopediaImageCatalog.assetUri("Apple___Apple_scab")
        )
    }

    @Test
    fun `normalizes legacy encyclopedia labels`() {
        assertEquals(
            "file:///android_asset/encyclopedia/corn_maize_common_rust.jpg",
            EncyclopediaImageCatalog.assetUri("Corn___Common_rust")
        )
        assertEquals(
            "file:///android_asset/encyclopedia/cherry_including_sour_powdery_mildew.jpg",
            EncyclopediaImageCatalog.assetUri("Cherry___Powdery_mildew")
        )
    }

    @Test
    fun `does not claim an unrelated image for unsupported entry`() {
        assertNull(EncyclopediaImageCatalog.assetUri("apple_brown_spot"))
    }
}
