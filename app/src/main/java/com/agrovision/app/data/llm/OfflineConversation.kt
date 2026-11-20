package com.agrovision.app.data.llm

/** 无大模型配置时的轻量日常对话，农业专业回答仍交给本地知识库。 */
object OfflineConversation {
    fun replyTo(query: String): String? {
        val normalized = query.trim().lowercase()
            .filterNot { it.isWhitespace() || it in "，。！？,.!?~～" }
        if (normalized.isEmpty()) return null

        return when {
            normalized in GREETINGS -> "你好，我在。你可以和我聊日常问题，也可以问果园管理、病虫害、水肥和用药。"
            normalized in THANKS || normalized.startsWith("谢谢") || normalized.startsWith("感谢") ->
                "不客气。还有什么想聊的，直接说就行。"
            normalized in FAREWELLS -> "好的，下次见。需要时随时来找我。"
            listOf("你是谁", "你叫什么", "介绍一下你自己").any { normalized.contains(it) } ->
                "我是农智眼 AI 助手。我可以进行日常交流，也擅长农业知识、果园管理和病虫害分析。当前没有连接大模型，所以复杂的开放式问题会受限。"
            listOf("你能做什么", "你会什么", "能帮我什么", "有什么功能").any { normalized.contains(it) } ->
                "我能进行日常交流，查询病虫害，提供施肥、灌溉、修剪和采收建议，并结合本机农场档案回答管理问题。配置云端 API Key 或 Ollama 后，还能进行更自然的多轮对话、写作、总结和一般知识问答。"
            normalized in setOf("在吗", "还在吗", "有人吗") -> "在。你说，我听着。"
            normalized in setOf("早上好", "早安") -> "早上好。今天有什么想安排或了解的？"
            normalized == "下午好" -> "下午好。有什么我可以帮你的？"
            normalized in setOf("晚上好", "晚安") ->
                if (normalized == "晚安") "晚安，早点休息。" else "晚上好。今天过得怎么样？"
            else -> null
        }
    }

    private val GREETINGS = setOf("你好", "您好", "嗨", "哈喽", "hello", "hi", "hey")
    private val THANKS = setOf("谢谢", "多谢", "感谢", "谢了", "thankyou", "thanks")
    private val FAREWELLS = setOf("再见", "拜拜", "回头见", "下次见", "bye", "goodbye")
}
