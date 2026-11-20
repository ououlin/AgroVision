package com.agrovision.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.agrovision.app.ui.theme.brandGreen

/**
 * 统一的异步内容容器：封装 loading / error / empty / success 四态渲染。
 *
 * 用于消除各页面重复的三态 when 分支，提供一致的加载/错误/空态体验。
 *
 * @param isLoading 是否加载中
 * @param data 数据；为 null 或空集合时进入 empty 态
 * @param error 错误信息；非空时进入 error 态
 * @param emptyIcon 空状态图标（默认空态文案上方）
 * @param emptyText 空状态文案
 * @param loadingContent 自定义加载态内容
 * @param errorContent 自定义错误态内容
 * @param emptyContent 自定义空态内容
 * @param content success 态内容
 */
@Composable
fun <T> AsyncContent(
    isLoading: Boolean,
    data: T?,
    error: String?,
    modifier: Modifier = Modifier,
    emptyIcon: ImageVector? = null,
    emptyText: String = "暂无数据",
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (String) -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> loadingContent?.invoke() ?: CircularProgressIndicator(color = brandGreen())
            error != null -> errorContent?.invoke(error) ?: DefaultErrorContent(error)
            data == null || (data is Collection<*> && data.isEmpty()) ->
                emptyContent?.invoke() ?: DefaultEmptyContent(emptyIcon, emptyText)
            else -> content(data)
        }
    }
}

@Composable
private fun DefaultErrorContent(error: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DefaultEmptyContent(icon: ImageVector?, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
    }
}
