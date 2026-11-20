package com.agrovision.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrovision.app.ui.theme.*

/**
 * 权限申请解释对话框（Rationale Dialog）。
 *
 * 国内应用市场合规要求：任何动态权限申请（相机、定位、存储、通知、麦克风）
 * 必须先弹出「告知为什么要用这个权限」的解释框，用户点击「我知道了」之后
 * 才允许真正调 ActivityResultContracts.RequestPermission。
 *
 * 如果用户此前拒绝过（shouldShowRequestPermissionRationale=true）或系统弹过
 * 永久拒绝弹窗，此对话框还会给出「前往设置页」的操作按钮。
 *
 * @param icon 左侧图标（建议与权限类型对应：相机 → CameraAlt，定位 → LocationOn，通知 → Notifications，相册 → PhotoLibrary）
 * @param title 大标题（例：「相机权限说明」）
 * @param purpose 权限用途（一句话描述我们用这个权限做什么，不能模糊笼统，要具体到业务动作）
 * @param whatHappens 授予后会做什么（例：「拍摄作物叶片 → 本地 YOLO 模型推理病虫害，不会上传原图到云端」）
 * @param whatIfDeny 拒绝的影响（例：「拒绝后仍可使用相册识别，但无法实时拍摄检测」）
 * @param confirmLabel 确认按钮文案，默认「我知道了」
 * @param openSettingsLabel 若用户已永久拒绝则展示此按钮，默认「去设置页开启」
 * @param permanentlyDenied 是否是永久拒绝（shouldShowRationale=false 且此前请求过），true 时会显示去设置页按钮
 * @param onConfirm 点击确认按钮回调（一般是真正 launch permissionRequest）
 * @param onOpenSettings 点击去设置页回调（一般是 startActivity(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)）
 * @param onDismiss 关闭回调（点击背景关闭 / 上滑关闭）
 */
@Composable
fun PermissionRationaleDialog(
    icon: ImageVector,
    title: String,
    purpose: String,
    whatHappens: String,
    whatIfDeny: String,
    confirmLabel: String = "我知道了",
    openSettingsLabel: String = "去设置页开启",
    permanentlyDenied: Boolean = false,
    onConfirm: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (permanentlyDenied) {
                    TextButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.XS.dp))
                        Text(openSettingsLabel, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(Spacing.XS.dp))
                }
                Button(
                    onClick = onConfirm,
                    shape = ShapeMD,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不开启", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        icon = {
            Surface(
                shape = ShapeLG,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    icon, null,
                    modifier = Modifier.padding(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("用途：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp))
                    Text(purpose,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("授权后：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp))
                    Text(whatHappens,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("拒绝：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp))
                    Text(whatIfDeny,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.XS.dp))
                Text(
                    "★ 您可以随时在系统「设置 → 农智眼 → 权限」中修改授权。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
        },
        shape = ShapeMD,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.CARD.dp
    )
}
