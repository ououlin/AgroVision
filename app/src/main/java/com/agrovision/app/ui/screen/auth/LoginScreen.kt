package com.agrovision.app.ui.screen.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agrovision.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAnim by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(200); showAnim = true }
    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            delay(800)
            onLoginSuccess()
        }
    }

    val alpha = animateFloatAsState(if (showAnim) 1f else 0f, tween(500))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = {
            uiState.error?.let { error ->
                Snackbar(modifier = Modifier.padding(16.dp)) {
                    Text(error)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.XXL.dp)
                .alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.XXL.dp))

            // 顶部Logo
            val logoBg = brandGreen()
            Surface(Modifier.size(64.dp), shape = ShapeLG, color = logoBg) {
                Icon(Icons.Filled.Yard, null, Modifier.padding(12.dp), tint = Color.White)
            }

            Spacer(Modifier.height(Spacing.XL.dp))

            Text("欢迎回来",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("请输入手机号登录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(Spacing.XXL3.dp))

            // 手机号
            OutlinedTextField(
                value = uiState.phone,
                onValueChange = viewModel::onPhoneChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("手机号") },
                leadingIcon = { Icon(Icons.Outlined.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = ShapeMD
            )

            Spacer(Modifier.height(Spacing.MD.dp + Spacing.SM.dp))

            // 验证码
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.code,
                    onValueChange = viewModel::onCodeChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("验证码") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = ShapeMD
                )
                Spacer(Modifier.width(Spacing.MD.dp - Spacing.SM.dp))
                val codeBtnBg = brandGreen()
                Button(
                    onClick = viewModel::sendCode,
                    modifier = Modifier.height(56.dp),
                    shape = ShapeMD,
                    enabled = uiState.phone.length == 11 && uiState.countdown == 0 && !uiState.isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = codeBtnBg)
                ) {
                    if (uiState.isSending) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else if (uiState.countdown > 0) {
                        Text("${uiState.countdown}s", fontWeight = FontWeight.Bold)
                    } else {
                        Text("发送验证码", maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.XXL2.dp))

            // 登录按钮
            val btnBg = brandGreen()
            Button(
                onClick = viewModel::login,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = ShapeMD,
                colors = ButtonDefaults.buttonColors(containerColor = btnBg),
                enabled = !uiState.isLoggingIn
            ) {
                if (uiState.isLoggingIn) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.SM.dp))
                    Text("登录中...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(Spacing.XXL2.dp))

            // 分隔线
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(" 其他登录方式 ", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                HorizontalDivider(Modifier.weight(1f))
            }

            Spacer(Modifier.height(Spacing.LG.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SocialButton(Icons.Filled.Forum, "微信", enabled = false)
                SocialButton(Icons.Filled.Chat, "QQ", enabled = false)
                SocialButton(Icons.Filled.Email, "邮箱", enabled = false)
                SocialButton(Icons.Filled.Phone, "Apple", enabled = false)
            }

            Spacer(Modifier.height(Spacing.SM.dp))

            Text(
                "第三方登录即将上线",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(Spacing.XXL2.dp))

            TextButton(onClick = {
                viewModel.loginAsGuest()
            }) {
                Text("游客模式登录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SocialButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(52.dp)
        ) {
            Surface(
                Modifier.size(48.dp), shape = ShapeMD,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.5f else 0.3f)
            ) {
                Icon(
                    icon, null, Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.4f
                    )
                )
            }
        }
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.5f else 0.3f)
        )
    }
}
