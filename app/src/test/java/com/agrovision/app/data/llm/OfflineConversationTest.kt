package com.agrovision.app.data.llm

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineConversationTest {
    @Test
    fun greetingGetsNaturalReply() {
        val reply = OfflineConversation.replyTo("你好！")
        assertTrue(reply.orEmpty().contains("你好"))
    }

    @Test
    fun capabilityQuestionExplainsBothChatAndAgriculture() {
        val reply = OfflineConversation.replyTo("你能做什么？").orEmpty()
        assertTrue(reply.contains("日常"))
        assertTrue(reply.contains("病虫害"))
    }

    @Test
    fun agriculturalQuestionFallsThroughToKnowledgeEngine() {
        assertNull(OfflineConversation.replyTo("苹果黑星病怎么治？"))
    }
}
