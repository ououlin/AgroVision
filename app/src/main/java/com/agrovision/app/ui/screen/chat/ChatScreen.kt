package com.agrovision.app.ui.screen.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.ui.theme.*

/**
 * 问农技 — 以农户任务为中心的日常对话与农业问答
 *
 * 布局：
 * - 顶栏：头像 + 标题 + 在线状态 + 模型名 + 设置入口
 * - 消息流：自适应宽度气泡 + 打字指示器
 * - 底部：错误 Banner（条件显示）+ 快捷问题横滑（常驻）+ 多行输入栏
 *
 * V2.0 UI Review 整改：
 * - 魔法数字统一改 DesignTokens（Spacing/Radius/TouchTarget）
 * - 硬编码 Green50 改语义色 brandGreen() / Blue50→brandBlue()
 * - 硬编码 Color.White 改 onPrimary/onSecondaryContainer（动态主题安全）
 * - LaunchedEffect 仅监听 messages.size（去掉 isAiTyping 抖动）
 * - 所有 clickable 触控区 ≥ TouchTarget.MIN=48dp
 *
 * 农户界面不展示模型、Provider 或 API 配置，技术配置统一留在设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialQuestion: String = "",
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // V2.0: 只监听 messages.size 变化，isAiTyping 变化不再强制滚动，避免用户看历史被拉回
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(initialQuestion, uiState.messages.isNotEmpty()) {
        if (initialQuestion.isNotBlank() && uiState.messages.isNotEmpty()) {
            viewModel.startContextQuestion(initialQuestion)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            onNavigateBack = onNavigateBack
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 离线模式引导 Banner
        if (!uiState.isModelReady) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.LG.dp, vertical = Spacing.SM.dp)
                    .clip(ShapeMD),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.LG.dp, vertical = Spacing.MD.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 触控 48dp 容器内放 20dp icon，符合 TouchTarget
                    Box(
                        modifier = Modifier
                            .size(TouchTarget.MIN.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            null,
                            modifier = Modifier.size(TouchTarget.ICON.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.MD.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "当前使用本地助手",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.XS.dp))
                        Text(
                            "可以日常交流，也能查询本地农业知识。复杂用药问题请结合当地农技指导。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = Spacing.LG.dp)
        ) {
            items(uiState.messages) { msg ->
                ChatBubble(message = msg)
            }
            if (uiState.isAiTyping) {
                item { TypingDot() }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 错误 Banner
        AnimatedVisibility(
            visible = uiState.lastError != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            uiState.lastError?.let { error ->
                ErrorRetryBar(
                    error = error,
                    onRetry = { viewModel.retryLastMessage() },
                    onDismiss = { viewModel.clearError() }
                )
            }
        }

        // 快捷问题横滑
        if (!uiState.isAiTyping) {
            QuickChipsRow(
                questions = uiState.suggestedQuestions,
                onSelect = { viewModel.sendQuickQuestion(it) }
            )
        }

        ChatInput(
            text = uiState.inputText,
            onTextChange = viewModel::onInputChanged,
            onSend = viewModel::sendMessage,
            isAiTyping = uiState.isAiTyping
        )
    }
}

// ---- Top Bar ----
@Composable
private fun ChatTopBar(
    onNavigateBack: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.LG.dp, vertical = Spacing.MD.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, "返回")
            }
            // AI 头像：48dp 触控，内部 brandGreen() 语义色 + onPrimary 图标色
            Surface(
                modifier = Modifier.size(TouchTarget.MIN.dp),
                shape = CircleShape,
                color = brandGreen(),
                tonalElevation = Elevation.CARD.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Psychology, null,
                        modifier = Modifier.size(TouchTarget.ICON_LG.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.MD.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "问农技",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(Spacing.XS.dp))
                Text(
                    "农业问题和日常交流",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- Quick Chips ----
@Composable
private fun QuickChipsRow(questions: List<String>, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.LG.dp, vertical = Spacing.SM.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM.dp)
    ) {
        questions.forEach { q ->
            val (icon, accent) = quickQuestionVisual(q)
            Surface(
                modifier = Modifier
                    .height(TouchTarget.MIN_ALT.dp)
                    .clickable { onSelect(q) },
                shape = ShapePill,
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(
                    1.dp,
                    accent.copy(alpha = 0.28f)
                ),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.LG.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(TouchTarget.ICON.dp),
                        tint = accent
                    )
                    Spacer(modifier = Modifier.width(Spacing.XS.dp))
                    Text(q,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun quickQuestionVisual(question: String): Pair<ImageVector, Color> = when {
    listOf("农药", "药剂", "打药", "用药", "安全间隔", "抗药性", "助剂")
        .any(question::contains) -> Icons.Outlined.Medication to accentOrange()
    listOf("施肥", "肥料", "缺素", "浇水", "灌溉", "土壤")
        .any(question::contains) -> Icons.Outlined.Eco to brandGreen()
    listOf("天气", "下雨", "高温", "低温", "大风", "霜冻")
        .any(question::contains) -> Icons.Outlined.Cloud to brandBlue()
    listOf("病", "虫", "黄叶", "发黄", "腐烂", "枯萎")
        .any(question::contains) -> Icons.Outlined.BugReport to brandRed()
    else -> Icons.Outlined.Lightbulb to MaterialTheme.colorScheme.tertiary
}

// ---- Chat Bubble ----
@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.LG.dp, vertical = Spacing.SM.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                AvatarAI()
                Spacer(modifier = Modifier.width(Spacing.SM.dp))
            }

            // 气泡：保持视觉风格性圆角（4dp/18dp 是艺术风格，不属于通用令牌间距）
            Surface(
                modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.88f).dp),
                shape = if (isUser)
                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                else
                    RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                color = if (isUser) brandGreen()
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                border = if (isUser) null else BorderStroke(
                    1.dp,
                    brandGreen().copy(alpha = 0.14f)
                ),
                tonalElevation = if (isUser) 0.dp else Elevation.CARD.dp,
                shadowElevation = if (isUser) 0.dp else 0.5.dp
            ) {
                if (!isUser && message.isMarkdown) {
                    MarkdownText(
                        markdown = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        modifier = Modifier.padding(Spacing.LG.dp)
                    )
                } else {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        // V2.0: 用户气泡的文字用 onPrimary（随 brandGreen 语义色自动适配动态主题）
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Spacing.LG.dp),
                        lineHeight = 20.sp
                    )
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(Spacing.SM.dp))
                AvatarUser()
            }
        }
    }
}

/** AI 头像组件：统一 48dp 触控 + brandGreen 语义色 + onPrimary 图标色 */
@Composable
private fun AvatarAI() {
    Surface(
        modifier = Modifier.size(TouchTarget.MIN.dp),
        shape = CircleShape,
        color = brandGreen()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Psychology, null,
                modifier = Modifier.size(TouchTarget.ICON_LG.dp),
                tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** 用户头像组件：统一 48dp 触控 + brandBlue 语义色 + onPrimary */
@Composable
private fun AvatarUser() {
    Surface(
        modifier = Modifier.size(TouchTarget.MIN.dp),
        shape = CircleShape,
        color = brandBlue()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, null,
                modifier = Modifier.size(TouchTarget.ICON_LG.dp),
                tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// ---- Typing Dot ----
@Composable
private fun TypingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val scales = listOf(0, 1, 2).map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = delay * 200, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot$delay"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.LG.dp, vertical = Spacing.SM.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        AvatarAI()
        Spacer(modifier = Modifier.width(Spacing.SM.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            tonalElevation = 0.5.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.LG.dp, vertical = Spacing.LG.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                scales.forEach { scale ->
                    Box(
                        modifier = Modifier
                            .size(Spacing.SM.dp)
                            .scale(scale.value)
                            // V2.0: Green50 → brandGreen() 语义色
                            .background(brandGreen().copy(alpha = 0.85f), CircleShape)
                    )
                }
            }
        }
    }
}

// ---- Error Retry Bar ----
@Composable
private fun ErrorRetryBar(
    error: ChatError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val (icon, message) = when (error) {
        is ChatError.Network -> Icons.Filled.WifiOff to error.message
        is ChatError.AuthKey -> Icons.Filled.Key to error.message
        is ChatError.Server -> Icons.Filled.CloudOff to error.message
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL.dp, vertical = Spacing.SM.dp),
        shape = ShapeMD,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.LG.dp, vertical = Spacing.MD.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null,
                modifier = Modifier.size(TouchTarget.ICON.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(Spacing.SM.dp))
            Text(message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f))
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = Spacing.SM.dp, vertical = 0.dp)
            ) {
                Text("重试", style = MaterialTheme.typography.labelMedium)
            }
            // V2.0: 移除 size(28.dp)，IconButton 默认 48dp 触控达标
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "关闭",
                    modifier = Modifier.size(TouchTarget.ICON.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Input Bar ----
@Composable
private fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isAiTyping: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.padding(horizontal = Spacing.MD.dp, vertical = Spacing.SM.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    enabled = !isAiTyping,
                    placeholder = {
                        Text(
                            if (isAiTyping) "正在整理回答..." else "说说作物哪里不对，或聊点别的",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    },
                    shape = ShapePill,
                    colors = OutlinedTextFieldDefaults.colors(
                        // V2.0: 全部 Green50 → brandGreen() 语义色
                        focusedBorderColor = brandGreen().copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        cursorColor = brandGreen()
                    ),
                    maxLines = 4,
                    minLines = 1
                )
                Spacer(modifier = Modifier.width(Spacing.XS.dp))
                if (text.isNotBlank() && !isAiTyping) {
                    // 发送按钮：去掉外层 size(40dp)，保留 IconButton 默认 48dp
                    IconButton(onClick = onSend) {
                        Surface(
                            shape = CircleShape,
                            color = brandGreen(),
                            tonalElevation = Elevation.CARD.dp,
                            modifier = Modifier.size(TouchTarget.MIN.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Send, "发送",
                                    modifier = Modifier.size(TouchTarget.ICON.dp),
                                    // V2.0: Color.White → onPrimary
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
