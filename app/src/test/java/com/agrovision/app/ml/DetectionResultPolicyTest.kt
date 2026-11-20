package com.agrovision.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionResultPolicyTest {

    @Test
    fun `maps every crop exposed by detection UI`() {
        val expected = mapOf(
            "苹果" to "apple",
            "蓝莓" to "blueberry",
            "樱桃" to "cherry",
            "玉米" to "corn",
            "葡萄" to "grape",
            "柑橘" to "orange",
            "桃" to "peach",
            "辣椒" to "pepper",
            "马铃薯" to "potato",
            "树莓" to "raspberry",
            "大豆" to "soybean",
            "南瓜" to "squash",
            "草莓" to "strawberry",
            "番茄" to "tomato"
        )

        expected.forEach { (displayName, modelCode) ->
            assertEquals(modelCode, DetectionResultPolicy.normalizeCropType(displayName))
        }
    }

    @Test
    fun `automatic mode does not restrict model classes`() {
        assertNull(DetectionResultPolicy.normalizeCropType("自动识别"))
        assertNull(DetectionResultPolicy.normalizeCropType(null))
    }

    @Test
    fun `healthy classes remain valid classification results`() {
        assertFalse(DetectionResultPolicy.isBackground("Apple___healthy"))
        assertFalse(DetectionResultPolicy.isBackground("Tomato___healthy"))
    }

    @Test
    fun `background classes are filtered`() {
        assertTrue(DetectionResultPolicy.isBackground("background"))
        assertTrue(DetectionResultPolicy.isBackground("background_without_leaves"))
    }
}
