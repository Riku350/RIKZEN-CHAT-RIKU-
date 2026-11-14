package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import com.example.ui.theme.SilverMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private data class WelcomeParticle(val x: Float, val y: Float, val radius: Float)

@Composable
fun WelcomeAnimationScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Frame-synced Animatable read only inside draw/graphicsLayer phases -> zero per-frame recompositions
    val progress = remember { Animatable(if (reducedMotion) 0.9f else 0f) }
    var brandTextVisible by remember { mutableStateOf(reducedMotion) }
    var welcomeVisible by remember { mutableStateOf(reducedMotion) }
    var showSkip by remember { mutableStateOf(true) }

    val particles = remember {
        List(26) { idx ->
            WelcomeParticle(
                x = (idx * 37 % 100) / 100f,
                y = (idx * 53 % 100) / 100f,
                radius = 1.6f + (idx % 4)
            )
        }
    }

    val ringBrush = remember {
        Brush.sweepGradient(listOf(ChampagneGold, BrightGold, RikzenEmerald, BrightGold))
    }
    val emblemBorderBrush = remember {
        Brush.linearGradient(listOf(BrightGold, ChampagneGold.copy(alpha = 0.25f), BrightGold))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            delay(1400L)
            onFinished()
            return@LaunchedEffect
        }
        launch {
            delay(2900L)
            brandTextVisible = true
            delay(1050L)
            welcomeVisible = true
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .graphicsLayer {
                val ms = progress.value * 5000f
                alpha = if (ms > 4650f) (1f - (ms - 4650f) / 350f).coerceIn(0f, 1f) else 1f
            }
    ) {
        // 0-1s: Subtle golden ambient radial glow + drifting golden particles + 2-4s golden lighting sweep
        Canvas(modifier = Modifier.fillMaxSize()) {
            val total = progress.value
            val elapsedMs = total * 5000f
            val glowProgress = (elapsedMs / 1000f).coerceIn(0f, 1f)
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val lightingPulse = ((elapsedMs - 1600f) / 1400f).coerceIn(0f, 1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrightGold.copy(alpha = 0.16f * lightingPulse),
                        ChampagneGold.copy(alpha = 0.28f * glowProgress),
                        ChampagneGold.copy(alpha = 0.08f * glowProgress),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * (0.35f + 0.40f * glowProgress)
                ),
                center = center,
                radius = size.minDimension * 0.74f
            )

            // Diagonal champagne gold light sweep between 1.8s and 3.8s
            val sweepT = ((elapsedMs - 1800f) / 2000f).coerceIn(0f, 1f)
            if (!reducedMotion && sweepT > 0f && sweepT < 1f) {
                val sweepX = size.width * (-0.3f + 1.6f * sweepT)
                val sweepAlpha = sin(sweepT * Math.PI).toFloat() * 0.16f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, BrightGold.copy(alpha = sweepAlpha), Color.Transparent),
                        start = Offset(sweepX - 180f, 0f),
                        end = Offset(sweepX + 180f, size.height)
                    )
                )
            }

            if (!reducedMotion) {
                particles.forEachIndexed { idx, p ->
                    val driftX = ((p.x + total * (0.15f + (idx % 3) * 0.05f)) % 1f) * size.width
                    val driftY = ((p.y - total * (0.18f + (idx % 4) * 0.04f) + 1f) % 1f) * size.height
                    val twinkle = 0.3f + 0.7f * sin((total * 10f + idx).toDouble()).toFloat().coerceAtLeast(0f)
                    drawCircle(
                        color = BrightGold.copy(alpha = 0.55f * glowProgress * twinkle),
                        radius = p.radius,
                        center = Offset(driftX, driftY)
                    )
                }
            }
        }

        // 1-2s: Elegant spiderweb forming (driven in draw phase)
        SpiderWebPattern(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            alpha = 0.34f,
            progressProvider = { ((progress.value * 5000f - 1000f) / 1000f).coerceIn(0f, 1f) }
        )

        // Skip button
        if (showSkip) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        showSkip = false
                        onFinished()
                    }
                    .testTag("skip_welcome_button"),
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Skip",
                    color = SilverMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Centerpiece: Spider Emblem + 5s Golden Orbital Ring + RIKZEN Logo + Tagline + Welcome
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1-3s: Golden Spider Emblem cinematic reveal with 5s orbital sweep
            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val total = progress.value
                    val glowProgress = ((total * 5000f) / 1000f).coerceIn(0f, 1f)
                    val strokeW = 3.dp.toPx()
                    drawCircle(
                        color = ChampagneGold.copy(alpha = 0.14f * glowProgress),
                        style = Stroke(width = strokeW)
                    )
                    drawArc(
                        brush = ringBrush,
                        startAngle = -90f,
                        sweepAngle = 360f * total,
                        useCenter = false,
                        style = Stroke(
                            width = strokeW,
                            cap = StrokeCap.Round
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .graphicsLayer {
                            val webP = if (reducedMotion) 1f else ((progress.value * 5000f - 1000f) / 1000f).coerceIn(0f, 1f)
                            val s = 0.65f + 0.35f * webP
                            scaleX = s
                            scaleY = s
                            alpha = webP
                        }
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            brush = emblemBorderBrush,
                            shape = CircleShape
                        )
                        .background(ObsidianBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rikzen_spider_icon_1794648913961),
                        contentDescription = "RIKZEN Spider Emblem",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val logoReveal = ((progress.value * 5000f - 2000f) / 900f).coerceIn(0f, 1f)
                                alpha = 0.35f + 0.65f * logoReveal
                            }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 2-3s: RIKZEN Wordmark
            Text(
                text = "RIKZEN",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = 8.sp,
                color = BrightGold,
                modifier = Modifier.graphicsLayer {
                    alpha = ((progress.value * 5000f - 2000f) / 900f).coerceIn(0f, 1f)
                }
            )

            Spacer(Modifier.height(10.dp))

            // 3-4s: RIKZEN CHAT / CONNECT • CHAT • SHARE
            AnimatedVisibility(
                visible = brandTextVisible,
                enter = fadeIn(tween(450, easing = FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RIKZEN CHAT",
                        style = MaterialTheme.typography.titleMedium,
                        color = IvoryWhite,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "CONNECT  •  CHAT  •  SHARE",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneGold,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 4-5s: WELCOME TO RIKZEN CHAT
            AnimatedVisibility(
                visible = welcomeVisible,
                enter = fadeIn(tween(400, easing = LinearEasing)),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = RikzenEmerald.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, RikzenEmerald.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "WELCOME TO RIKZEN CHAT",
                        style = MaterialTheme.typography.labelLarge,
                        color = IvoryWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
