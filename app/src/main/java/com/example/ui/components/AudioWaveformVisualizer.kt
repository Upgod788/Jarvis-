package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Visualizer presentation style options for listening mode.
 */
enum class WaveformVisualizerStyle(val displayName: String) {
    COMBINED("Arc & Wave"),
    FLUID_WAVE("Fluid Wave"),
    RADIAL_PULSE("Pulse Rings"),
    SPECTRUM("Spectrum EQ")
}

/**
 * Normalizes raw microphone audio RMS level (dB) into a continuous [0.0 .. 1.0] scale.
 * Android SpeechRecognizer onRmsChanged generally delivers values from -2.0 dB (silence)
 * up to 10.0-14.0 dB for loud speech, or 0..100 on specific OEM drivers.
 */
fun normalizeAudioRms(rawRms: Float, isListening: Boolean): Float {
    if (!isListening) return 0f
    return when {
        rawRms <= -2f -> 0.06f // Gentle baseline hum during active listening
        rawRms in -2f..14f -> {
            // Map [-2 dB .. 10 dB] into [0.06 .. 1.0]
            val normalized = ((rawRms + 2f) / 12f).coerceIn(0f, 1f)
            0.06f + (normalized * 0.94f)
        }
        rawRms in 14f..100f -> {
            // OEM 0-100 percentage scale
            (rawRms / 100f).coerceIn(0.06f, 1f)
        }
        else -> 0.08f
    }
}

/**
 * Primary visualizer container that responds in real-time to microphone audio levels.
 * Renders animated visual pulses, fluid multi-harmonic waveforms, or digital spectrum bars.
 */
@Composable
fun AudioPulseWaveformVisualizer(
    assistantState: AssistantState,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    initialStyle: WaveformVisualizerStyle = WaveformVisualizerStyle.COMBINED,
    showControls: Boolean = true
) {
    val isListening = assistantState == AssistantState.LISTENING
    var selectedStyle by remember { mutableStateOf(initialStyle) }

    // Target audio amplitude normalized to [0..1]
    val targetAudioLevel = remember(rmsLevel, isListening) {
        normalizeAudioRms(rmsLevel, isListening)
    }

    // Dynamic smoothing filter using spring physics for responsive and jitter-free animations
    val animatedLevel by animateFloatAsState(
        targetValue = targetAudioLevel,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animatedAudioLevel"
    )

    // Infinite continuous phase timer for harmonic wave movement
    val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 1600 else 3600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 1200 else 2400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val primaryColor = when (assistantState) {
        AssistantState.LISTENING -> Color(0xFF00E5FF) // Neon Cyan
        AssistantState.THINKING -> Color(0xFFFFD700)  // Cyber Gold
        AssistantState.EXECUTING -> Color(0xFF00E676) // Hologram Green
        AssistantState.SPEAKING -> Color(0xFF7C4DFF)  // Electric Violet
        AssistantState.ERROR -> Color(0xFFFF1744)     // Warning Crimson
        else -> Color(0xFF00B0FF)                     // Standby Sky Blue
    }

    val secondaryColor = when (assistantState) {
        AssistantState.LISTENING -> Color(0xFFFF3366) // Neon Pink / Rose
        AssistantState.THINKING -> Color(0xFFFF9100)  // Amber Orange
        AssistantState.EXECUTING -> Color(0xFF00B0FF) // Cyan-Blue
        AssistantState.SPEAKING -> Color(0xFF00E5FF)  // Cyan
        else -> Color(0xFF2979FF)                     // Royal Blue
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_pulse_waveform_visualizer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Visualizer Display Surface
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        primaryColor.copy(alpha = if (isListening) 0.6f else 0.2f),
                        secondaryColor.copy(alpha = if (isListening) 0.6f else 0.2f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background subtle sci-fi HUD grid
                HudGridBackground(
                    gridColor = primaryColor.copy(alpha = if (isListening) 0.08f else 0.03f)
                )

                // Visualizer Core Rendering based on selected style
                when (selectedStyle) {
                    WaveformVisualizerStyle.FLUID_WAVE -> {
                        FluidAudioWaveformCanvas(
                            audioLevel = animatedLevel,
                            phase = phase,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            isListening = isListening,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    WaveformVisualizerStyle.RADIAL_PULSE -> {
                        RadialPulseShockwavesCanvas(
                            audioLevel = animatedLevel,
                            pulsePhase = pulsePhase,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            isListening = isListening,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    WaveformVisualizerStyle.SPECTRUM -> {
                        DigitalSpectrumBarsCanvas(
                            audioLevel = animatedLevel,
                            phase = phase,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            isListening = isListening,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    WaveformVisualizerStyle.COMBINED -> {
                        // Multi-layer combination: radial acoustic shockwaves behind fluid flowing wave
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RadialPulseShockwavesCanvas(
                                audioLevel = animatedLevel,
                                pulsePhase = pulsePhase,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                isListening = isListening,
                                modifier = Modifier.fillMaxSize()
                            )
                            FluidAudioWaveformCanvas(
                                audioLevel = animatedLevel,
                                phase = phase,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                isListening = isListening,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Top HUD overlay badge: Live Audio dB Meter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // State / Mode indicator pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isListening) primaryColor else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isListening) "MIC ACTIVE" else assistantState.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = primaryColor
                        )
                    }

                    // Live VU Meter & Decibel HUD Readout
                    LiveVuAudioMeter(
                        audioLevel = animatedLevel,
                        rawRms = rmsLevel,
                        isListening = isListening,
                        activeColor = primaryColor
                    )
                }
            }
        }

        // Style Switcher Chips
        if (showControls) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WaveformVisualizerStyle.values().forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) primaryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) primaryColor else Color.Transparent
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedStyle = style }
                            .testTag("visualizer_style_${style.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (style) {
                                    WaveformVisualizerStyle.FLUID_WAVE -> Icons.Default.Waves
                                    WaveformVisualizerStyle.RADIAL_PULSE -> Icons.Default.Radio
                                    WaveformVisualizerStyle.SPECTRUM -> Icons.Default.GraphicEq
                                    WaveformVisualizerStyle.COMBINED -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = style.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders multiple flowing sinusoidal waves with a Hann window envelope,
 * glowing crests, and dynamic gradient fills that react in real-time to microphone amplitude.
 */
@Composable
fun FluidAudioWaveformCanvas(
    audioLevel: Float,
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        if (width <= 0 || height <= 0) return@Canvas

        // Amplitude multipliers
        val baseAmp = if (isListening) height * 0.12f else height * 0.04f
        val maxSpeechAmp = height * 0.42f
        val currentAmp = baseAmp + (audioLevel * maxSpeechAmp)

        val steps = 100
        val dx = width / steps

        // --- Wave 1: Primary Cyan Wave (Foreground with glowing fill) ---
        val path1 = Path()
        val fillPath1 = Path()
        fillPath1.moveTo(0f, centerY)

        for (i in 0..steps) {
            val x = i * dx
            val u = (x / width).coerceIn(0f, 1f)
            // Hann window envelope: 0 at edges, 1 at center
            val envelope = (sin(u * PI).toFloat()) * (sin(u * PI).toFloat())

            // Primary carrier wave with 2 harmonic sub-frequencies
            val harmonic1 = sin(u * 2.5f * (2 * PI).toFloat() + phase).toFloat()
            val harmonic2 = 0.35f * sin(u * 5f * (2 * PI).toFloat() - (phase * 1.5f)).toFloat()
            val y = centerY + (harmonic1 + harmonic2) * currentAmp * envelope

            if (i == 0) {
                path1.moveTo(x, y)
                fillPath1.lineTo(x, y)
            } else {
                path1.lineTo(x, y)
                fillPath1.lineTo(x, y)
            }
        }
        fillPath1.lineTo(width, centerY)
        fillPath1.close()

        // Draw translucent gradient fill under primary wave
        drawPath(
            path = fillPath1,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.35f * (0.4f + audioLevel * 0.6f)),
                    primaryColor.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = centerY - currentAmp,
                endY = centerY + currentAmp
            )
        )

        // Draw crisp glowing stroke for primary wave
        drawPath(
            path = path1,
            color = primaryColor.copy(alpha = 0.9f),
            style = Stroke(
                width = (2.5f + (audioLevel * 3.5f)).dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // --- Wave 2: Secondary Harmonic Wave (Neon Pink/Magenta) ---
        val path2 = Path()
        val fillPath2 = Path()
        fillPath2.moveTo(0f, centerY)

        val amp2 = currentAmp * 0.75f
        for (i in 0..steps) {
            val x = i * dx
            val u = (x / width).coerceIn(0f, 1f)
            val envelope = (sin(u * PI).toFloat()) * (sin(u * PI).toFloat())

            // Counter-phase harmonic
            val wave = sin(u * 3.2f * (2 * PI).toFloat() - (phase * 1.2f)).toFloat()
            val y = centerY + wave * amp2 * envelope

            if (i == 0) {
                path2.moveTo(x, y)
                fillPath2.lineTo(x, y)
            } else {
                path2.lineTo(x, y)
                fillPath2.lineTo(x, y)
            }
        }
        fillPath2.lineTo(width, centerY)
        fillPath2.close()

        drawPath(
            path = fillPath2,
            brush = Brush.verticalGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.25f * (0.3f + audioLevel * 0.7f)),
                    Color.Transparent
                ),
                startY = centerY - amp2,
                endY = centerY + amp2
            )
        )

        drawPath(
            path = path2,
            color = secondaryColor.copy(alpha = 0.85f),
            style = Stroke(
                width = (1.8f + (audioLevel * 2f)).dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // --- Sparkle/Energy nodes on wave crests when audio is energetic ---
        if (audioLevel > 0.25f) {
            val numParticles = 5
            for (p in 1..numParticles) {
                val u = p / (numParticles + 1f)
                val x = u * width
                val envelope = (sin(u * PI).toFloat()) * (sin(u * PI).toFloat())
                val y = centerY + sin(u * 2.5f * (2 * PI).toFloat() + phase).toFloat() * currentAmp * envelope

                drawCircle(
                    color = Color.White,
                    radius = (2f + (audioLevel * 3.5f)).dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.5f),
                    radius = (4f + (audioLevel * 6f)).dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        // Center baseline line
        drawLine(
            color = primaryColor.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Renders concentric acoustic shockwave rings that ripple outward from the center
 * in proportion to microphone volume spikes.
 */
@Composable
fun RadialPulseShockwavesCanvas(
    audioLevel: Float,
    pulsePhase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (min(size.width, size.height) / 2f) * 0.95f
        val minRadius = maxRadius * 0.25f

        // Center pulsing orb
        val coreRadius = minRadius * (0.8f + (audioLevel * 0.5f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.9f),
                    secondaryColor.copy(alpha = 0.5f),
                    Color.Transparent
                ),
                center = center,
                radius = coreRadius * 1.5f
            ),
            radius = coreRadius * 1.5f,
            center = center
        )

        // 4 Concentric expanding acoustic pulse rings
        val ringCount = 4
        for (i in 0 until ringCount) {
            val ringOffset = (pulsePhase + (i.toFloat() / ringCount)) % 1f
            val currentRadius = minRadius + (maxRadius - minRadius) * ringOffset

            // Alpha fades as ring travels outward, scaled up by audio level
            val alphaBase = (1f - ringOffset).coerceIn(0f, 1f)
            val dynamicAlpha = alphaBase * (0.2f + (audioLevel * 0.8f))
            val strokeWidth = ((1.5f + (1f - ringOffset) * 2.5f) * (1f + audioLevel)).dp.toPx()

            val ringColor = if (i % 2 == 0) primaryColor else secondaryColor

            drawCircle(
                color = ringColor.copy(alpha = dynamicAlpha),
                radius = currentRadius,
                center = center,
                style = Stroke(
                    width = strokeWidth
                )
            )
        }

        // Surrounding orbital HUD tick marks
        val tickCount = 24
        val tickRadius = maxRadius * 0.88f
        for (t in 0 until tickCount) {
            val angle = (t * (360f / tickCount) + (pulsePhase * 360f * 0.2f)) * (PI / 180f)
            val tickLen = (4f + (audioLevel * 6f)).dp.toPx()

            val startX = center.x + cos(angle).toFloat() * (tickRadius - tickLen)
            val startY = center.y + sin(angle).toFloat() * (tickRadius - tickLen)
            val endX = center.x + cos(angle).toFloat() * tickRadius
            val endY = center.y + sin(angle).toFloat() * tickRadius

            drawLine(
                color = primaryColor.copy(alpha = if (t % 4 == 0) 0.6f else 0.25f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (t % 4 == 0) 2.dp.toPx() else 1.dp.toPx()
            )
        }
    }
}

/**
 * Renders futuristic mirrored digital equalizer bars with peak caps
 * reacting dynamically to microphone audio energy.
 */
@Composable
fun DigitalSpectrumBarsCanvas(
    audioLevel: Float,
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val barCount = 28
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(2f)

        val maxBarHalfHeight = (height / 2f) * 0.82f
        val minBarHalfHeight = 4.dp.toPx()

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing)
            val centerOffsetRatio = (1f - (kotlin.math.abs(i - (barCount / 2f)) / (barCount / 2f))).coerceIn(0.1f, 1f)

            // Speech frequency simulation: speech peaks around low-mid frequencies
            val freqWeight = sin(centerOffsetRatio * (PI / 2f)).toFloat()
            val harmonicVariance = (sin(i * 0.8f + phase * 2f).toFloat() * 0.35f + 0.65f)

            val dynamicHeight = if (isListening) {
                minBarHalfHeight + (maxBarHalfHeight * audioLevel * freqWeight * harmonicVariance)
            } else {
                minBarHalfHeight + (maxBarHalfHeight * 0.1f * harmonicVariance)
            }

            // Draw top and bottom mirrored bar
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        secondaryColor,
                        primaryColor
                    ),
                    startY = centerY - dynamicHeight,
                    endY = centerY + dynamicHeight
                ),
                topLeft = Offset(x, centerY - dynamicHeight),
                size = Size(barWidth, dynamicHeight * 2),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            // Floating peak marker dot above active bars
            if (audioLevel > 0.15f) {
                val peakGap = 3.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = (barWidth / 3f).coerceAtLeast(1.5f),
                    center = Offset(x + (barWidth / 2f), centerY - dynamicHeight - peakGap)
                )
            }
        }
    }
}

/**
 * Sci-Fi HUD background grid pattern.
 */
@Composable
private fun HudGridBackground(gridColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 20.dp.toPx()
        var currentX = 0f
        while (currentX < size.width) {
            drawLine(
                color = gridColor,
                start = Offset(currentX, 0f),
                end = Offset(currentX, size.height),
                strokeWidth = 0.5f
            )
            currentX += step
        }

        var currentY = 0f
        while (currentY < size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, currentY),
                end = Offset(size.width, currentY),
                strokeWidth = 0.5f
            )
            currentY += step
        }
    }
}

/**
 * Live audio decibel readout badge with animated level bars.
 */
@Composable
fun LiveVuAudioMeter(
    audioLevel: Float,
    rawRms: Float,
    isListening: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 5-bar VU meter segments
        val barCount = 5
        for (b in 1..barCount) {
            val threshold = b / barCount.toFloat()
            val isFilled = isListening && (audioLevel >= threshold * 0.7f)
            val barColor = if (isFilled) {
                if (b == barCount) Color(0xFFFF3366) // Overload red
                else activeColor
            } else {
                Color.Gray.copy(alpha = 0.3f)
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((6 + b * 2).dp)
                    .background(barColor, RoundedCornerShape(1.dp))
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // dB Readout Text
        val dbDisplay = if (isListening) {
            if (rawRms > 0f) "+${"%.1f".format(rawRms)} dB"
            else "${"%.1f".format(rawRms)} dB"
        } else {
            "-- dB"
        }

        Text(
            text = dbDisplay,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            color = if (isListening) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Microphone acoustic pulse halo that wraps the mic FAB button.
 * Expands multiple glowing ripple rings in real-time response to audio volume.
 */
@Composable
fun MicAudioPulseHalo(
    isListening: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val audioLevel = remember(rmsLevel, isListening) {
        normalizeAudioRms(rmsLevel, isListening)
    }

    val animatedAudio by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        ),
        label = "mic_audio_level"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 900 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_pulse_progress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (isListening) {
            // Live Acoustic Shockwave Canvas behind the button
            Canvas(modifier = Modifier.size(140.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 36.dp.toPx()
                val maxExpansion = 28.dp.toPx() * (1f + animatedAudio * 1.5f)

                // 3 layered shockwaves
                for (i in 0..2) {
                    val ringPhase = (pulseProgress + (i * 0.33f)) % 1f
                    val currentRadius = baseRadius + (maxExpansion * ringPhase)
                    val alpha = (1f - ringPhase) * (0.35f + animatedAudio * 0.65f)

                    drawCircle(
                        color = Color(0xFFFF3366).copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = currentRadius,
                        center = center,
                        style = Stroke(width = (2f + (1f - ringPhase) * 2f).dp.toPx())
                    )
                }

                // Ambient glow burst
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF3366).copy(alpha = 0.4f * (0.5f + animatedAudio * 0.5f)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.6f
                    ),
                    radius = baseRadius * 1.6f,
                    center = center
                )
            }
        }

        content()
    }
}
