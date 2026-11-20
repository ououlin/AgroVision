package com.agrovision.app.data.rag

import com.agrovision.app.domain.model.PestInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2.0 农技搜索服务
 *
 * 包装 [KnowledgeBase]，对外提供结构化搜索结果（不同于原 [KnowledgeBase.search] 返回纯文本）。
 *
 * 设计意图：
 * - 原有 [KnowledgeBase.search] 仍被 AI 助手（RAG）使用，输出纯文本 context
 * - 新 [AgriSearchService] 输出 [SearchResultItem] 列表，供农技搜索 UI 渲染
 * - 完全离线可用（RAG only，不调用 LLM），与 AI 助手形成互补
 */
@Singleton
class AgriSearchService @Inject constructor(
    private val knowledgeBase: KnowledgeBase
) {

    /**
     * 结构化搜索：返回带评分的病虫条目
     *
     * @param query 用户输入关键词（支持同义词/拼音首字母扩展）
     * @param topK 返回条数，默认 10
     */
    fun searchStructured(query: String, topK: Int = 10): List<SearchResultItem> {
        if (query.isBlank()) return emptyList()

        val pests = knowledgeBase.run {
            // 复用 KnowledgeBase 内部的方法
            // 但因为 KnowledgeBase.search 返回纯文本，需要重新调用并解析
            search(query, topK)
        }

        // KnowledgeBase.search 返回格式化的 Markdown 文本
        // 这里我们重新构造结构化结果，解析文本中的中文/英文名
        return pests.mapIndexed { idx, doc ->
            SearchResultItem(
                pestInfo = parsePestFromDoc(doc),
                relevanceScore = 1.0 - idx * 0.05,  // 简单按排名递减评分
                docSnippet = doc.take(120) + if (doc.length > 120) "..." else "",
                intent = knowledgeBase.classifyIntent(query)
            )
        }.filter { it.pestInfo != null }
    }

    /**
     * 从 Markdown 文档解析 PestInfo（简化版）
     *
     * 格式：### 中文名 (英文名)
     */
    private fun parsePestFromDoc(doc: String): PestInfo? {
        val firstLine = doc.lines().firstOrNull() ?: return null
        // ### 中文名 (英文名)
        val match = Regex("###\\s+(.+?)\\s*\\((.+?)\\)").find(firstLine) ?: return null
        val (cn, en) = match.destructured
        return PestInfo(enName = en, cnName = cn)
    }

    /**
     * 搜索建议（输入前缀联想）
     *
     * 由 UI 在用户输入时触发，返回匹配的中文/英文名称列表
     */
    fun suggest(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isBlank()) return emptyList()
        val allNames = knowledgeBase.getAllPestNames()
        val lower = prefix.lowercase()
        return allNames
            .filter { it.first.contains(prefix) || it.second.lowercase().contains(lower) }
            .take(limit)
            .map { it.first }
    }

    /**
     * 获取知识库规模（UI 展示用）
     */
    fun knowledgeBaseSize(): Int = knowledgeBase.size()
}

/**
 * 搜索结果项（结构化）
 */
data class SearchResultItem(
    val pestInfo: PestInfo?,
    val relevanceScore: Double,
    val docSnippet: String,
    val intent: KnowledgeBase.QueryIntent
)
