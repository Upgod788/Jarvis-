package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.AssistantState
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisOrb(
    state: AssistantState,
    rmsLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Rotation angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.THINKING -> 2000
                    AssistantState.EXECUTING -> 1500
                    AssistantState.SPEAKING -> 4000
                    AssistantState.LISTENING -> 5000
                    else -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Counter-rotation angle for inner ring
    val counterRotationAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    // Breathing pulse scale
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 600
                    AssistantState.SPEAKING -> 800
                    AssistantState.THINKING -> 400
                    else -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Dynamic scale influenced by audio RMS level if listening
    val dynamicRmsFactor = if (state == AssistantState.LISTENING && rmsLevel > 0f) {
        (rmsLevel / 10f).coerceIn(0f, 0.25f)
    } else 0f

    val effectiveScale = breathingPulse + dynamicRmsFactor

    val (primaryColor, glowColor) = when (state) {
        AssistantState.LISTENING -> Pair(JarvisCyanBright, JarvisCyanPrimary.copy(alpha = 0.5f))
        AssistantState.THINKING -> Pair(JarvisBlueAccent, JarvisCyanBright.copy(alpha = 0.6f))
        AssistantState.EXECUTING -> Pair(JarvisWarning, JarvisWarning.copy(alpha = 0.4f))
        AssistantState.SPEAKING -> Pair(JarvisCyanPrimary, JarvisCyanBright.copy(alpha = 0.7f))
        AssistantState.ERROR -> Pair(JarvisError, JarvisError.copy(alpha = 0.4f))
        AssistantState.IDLE -> Pair(JarvisCyanPrimary.copy(alpha = 0.85f), JarvisCyanDark.copy(alpha = 0.35f))
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("jarvis_animated_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension / 2f) * 0.85f

            // 1. Outer Glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = radius * effectiveScale * 1.25f
                ),
                radius = radius * effectiveScale * 1.25f,
                center = center
            )

            // 2. Outer segmented HUD Ring
            val outerRingRadius = radius * 0.95f
            val segments = 12
            val dashLength = (2 * Math.PI * outerRingRadius / segments).toFloat() * 0.6f
            val spaceLength = (2 * Math.PI * outerRingRadius / segments).toFloat() * 0.4f

            drawCircle(
                color = primaryColor.copy(alpha = 0.4f),
                radius = outerRingRadius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, spaceLength), rotationAngle)
                )
            )

            // 3. Middle Counter-Rotating Ring with Tick Nodes
            val midRadius = radius * 0.78f
            drawCircle(
                color = primaryColor.copy(alpha = 0.6f),
                radius = midRadius,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 24f), counterRotationAngle)
                )
            )

            // Orbiting nodes on the middle ring
            val nodeCount = if (state == AssistantState.THINKING) 6 else 4
            for (i in 0 until nodeCount) {
                val angle = Math.toRadians((rotationAngle + (360f / nodeCount) * i).toDouble())
                val nodeX = center.x + (midRadius * cos(angle)).toFloat()
                val nodeY = center.y + (midRadius * sin(angle)).toFloat()
                drawCircle(
                    color = primaryColor,
                    radius = if (state == AssistantState.THINKING) 4.5.dp.toPx() else 3.5.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }

            // 4. Glowing Core Orb
            val coreRadius = radius * 0.52f * effectiveScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryColor,
                        glowColor,
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // 5. Central Reactor Ring
            drawCircle(
                color = primaryColor,
                radius = coreRadius * 0.7f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = Color.White,
                radius = coreRadius * 0.35f,
                center = center
            )
        }
    }
}
