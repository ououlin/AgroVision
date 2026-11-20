package com.agrovision.app.data.diagnosis

import com.agrovision.app.domain.model.PestInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalQuestionMatcherTest {
    @Test
    fun `default suggestions all have a specific local topic`() {
        val questions = listOf(
            "苹果黑星病怎么防治？",
            "果树什么时候施肥最好？",
            "叶子发黄是什么原因？",
            "农药安全间隔期怎么算？"
        )
        questions.forEach { question ->
            assertNotEquals(question, LocalQuestionTopic.FALLBACK, LocalQuestionMatcher.classify(question))
        }
    }

    @Test
    fun `management recommendations are supported offline`() {
        val expected = mapOf(
            "果园全年防治日历怎么安排？" to LocalQuestionTopic.ANNUAL_CALENDAR,
            "低毒农药怎么选择？" to LocalQuestionTopic.LOW_TOXIC_PESTICIDE,
            "打药时怎么避免产生抗药性？" to LocalQuestionTopic.PESTICIDE_ROTATION,
            "喷药时要不要加助剂？" to LocalQuestionTopic.SPRAY_ADJUVANT,
            "最近天气变化要注意什么？" to LocalQuestionTopic.WEATHER
        )
        expected.forEach { (question, topic) ->
            assertEquals(question, topic, LocalQuestionMatcher.classify(question))
        }
    }

    @Test
    fun `pest name is extracted from a complete question`() {
        val blackScab = PestInfo(enName = "Apple Scab", cnName = "苹果黑星病")
        assertEquals(
            blackScab,
            LocalQuestionMatcher.findMentionedPest("苹果黑星病怎么防治？", listOf(blackScab))
        )
    }
}
