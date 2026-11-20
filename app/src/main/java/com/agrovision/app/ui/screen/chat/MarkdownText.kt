package com.agrovision.app.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染组件 — 基于 Compose 原生 [AnnotatedString]
 *
 * 支持的 Markdown 子集：
 * - 标题：`#`、`##`、`###`、`####`（对应不同字号/字重）
 * - 加粗：`**text**`
 * - 斜体：`*text*`
 * - 行内代码：`` `code` ``
 * - 无序列表：`-` 或 `•` 开头
 * - 有序列表：`1.` 开头
 * - 分隔线：`---`
 * - 段落：空行分隔
 *
 * 不引入第三方库，满足 AI 助手回复的常见排版需求。
 *
 * @param markdown 原始 Markdown 文本
 * @param style 基础文本样式（默认取 bodyMedium）
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            3 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.labelLarge
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = renderInline(block.text),
                        style = style,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                is MdBlock.ListItem -> {
                    val prefix = if (block.ordered) "${block.index}. " else "• "
                    Text(
                        text = renderInline(prefix + block.text),
                        style = style,
                        modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp)
                    )
                }
                is MdBlock.Divider -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MdBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .padding(8.dp)
                    )
                }
                is MdBlock.Blank -> Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ===== Block 解析 =====

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int = 0) : MdBlock()
    data object Divider : MdBlock()
    data class CodeBlock(val code: String) : MdBlock()
    data object Blank : MdBlock()
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()
    var i = 0
    var orderedIndex = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            // 空行
            trimmed.isEmpty() -> {
                blocks.add(MdBlock.Blank)
                orderedIndex = 0
            }
            // 分隔线
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks.add(MdBlock.Divider)
                orderedIndex = 0
            }
            // 标题
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                blocks.add(MdBlock.Heading(level.coerceAtMost(4), text))
                orderedIndex = 0
            }
            // 代码块
            trimmed.startsWith("```") -> {
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.appendLine(lines[i])
                    i++
                }
                blocks.add(MdBlock.CodeBlock(codeBuilder.toString().trimEnd()))
                orderedIndex = 0
            }
            // 有序列表
            Regex("^\\d+\\.\\s+.+").matches(trimmed) -> {
                orderedIndex++
                val text = trimmed.substringAfter(". ").trim()
                blocks.add(MdBlock.ListItem(text, ordered = true, index = orderedIndex))
            }
            // 无序列表
            trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ") -> {
                val text = trimmed.drop(2).trim()
                blocks.add(MdBlock.ListItem(text, ordered = false))
                orderedIndex = 0
            }
            // 普通段落
            else -> {
                blocks.add(MdBlock.Paragraph(trimmed))
                orderedIndex = 0
            }
        }
        i++
    }

    return blocks
}

// ===== Inline 解析（加粗、斜体、行内代码） =====

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // 加粗 **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            // 行内代码 `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.15f)
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            // 斜体 *text*
            text[i] == '*' && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}
