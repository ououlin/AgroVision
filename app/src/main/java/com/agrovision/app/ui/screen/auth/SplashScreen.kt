package com.agrovision.app.ui.screen.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import coil.compose.AsyncImage
import com.agrovision.app.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrovision.app.domain.model.AuthState
import com.agrovision.app.ui.theme.Green50
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authState: AuthState,
    onNavigateToHome: () -> Unit,
    onNavigateToWelcome: () -> Unit
) {
    val animDuration = 1500
    val scaleAnim = remember { Animatable(0.3f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        alphaAnim.animateTo(1f, tween(600))
        delay(animDuration.toLong())
        when (authState) {
            is AuthState.LoggedIn -> onNavigateToHome()
            else -> onNavigateToWelcome()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Green50),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = R.drawable.nvtlogo,
                contentDescription = null,
                modifier = Modifier.size(100.dp).clip(CircleShape).scale(scaleAnim.value)
            )
            Spacer(Modifier.height(16.dp))
            AsyncImage(
                model = R.drawable.beilian_logo,
                contentDescription = null,
                modifier = Modifier.size(140.dp).scale(scaleAnim.value)
            )
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.alpha(alphaAnim.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("农智眼", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("AgroVision AI", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("让每一株作物，都被看见。",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}
