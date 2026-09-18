package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AssistantState
import com.example.ui.JarvisUiState
import com.example.ui.components.AudioPulseWaveformVisualizer
import com.example.ui.components.MicAudioPulseHalo
import com.example.ui.components.normalizeAudioRms

@Composable
fun HomeScreen(
    uiState: JarvisUiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onToggleMute: () -> Unit,
    onSpeakResponse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    var showWaveformVisualizer by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    val quickCommands = remember {
        listOf(
            "Turn on flashlight",
            "What's the weather today?",
            "Set a 5 minute timer",
            "Turn on Wi-Fi",
            "Open YouTube",
            "What time is it?",
            "Search for space news"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RAVAN A.I.",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "System Online • Mk-VII",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showWaveformVisualizer = !showWaveformVisualizer },
                    modifier = Modifier.testTag("toggle_visualizer_mode_button")
                ) {
                    Icon(
                        imageVector = if (showWaveformVisualizer) Icons.Default.GraphicEq else Icons.Default.Waves,
                        contentDescription = "Toggle Visualizer View",
                        tint = if (showWaveformVisualizer) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.testTag("toggle_voice_mute_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isVoiceMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Voice Mute",
                        tint = if (uiState.isVoiceMuted) Color.Gray else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Animated Visualizer: Real-time Audio Waveform & Pulse or Arc Reactor
        AnimatedContent(
            targetState = (uiState.assistantState == AssistantState.LISTENING) || showWaveformVisualizer,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "visualizer_display"
        ) { displayWaveform ->
            if (displayWaveform) {
                AudioPulseWaveformVisualizer(
                    assistantState = uiState.assistantState,
                    rmsLevel = uiState.audioRmsLevel,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ArcReactorOrb(
                    state = uiState.assistantState,
                    rmsLevel = uiState.audioRmsLevel,
                    modifier = Modifier.size(190.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // State Indicator Badge
        StatusBadge(state = uiState.assistantState)

        Spacer(modifier = Modifier.height(20.dp))

        // Response or Active Command Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .shadow(6.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (uiState.currentCommand.isNotBlank()) {
                    Text(
                        text = "YOU",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "\"${uiState.currentCommand}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RAVAN",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onSpeakResponse,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Read aloud",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = uiState.assistantResponse,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (uiState.activeToolName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Tool Executed: ${uiState.activeToolName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Suggestions
        Text(
            text = "Suggested Directives",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Start)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickCommands) { cmd ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable {
                        textInput = cmd
                        onSubmitCommand(cmd)
                    }
                ) {
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mic Button with Real-time Acoustic Pulse Halo
        val isListening = uiState.assistantState == AssistantState.LISTENING
        MicAudioPulseHalo(
            isListening = isListening,
            rmsLevel = uiState.audioRmsLevel,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                val pulseTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = if (isListening) 1.25f else 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(if (isListening) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                )

                IconButton(
                    onClick = {
                        if (isListening) onStopListening() else onStartListening()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isListening) listOf(Color(0xFFFF3366), Color(0xFFFF6699))
                                else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                        .testTag("main_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Command Text Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Ask Ravan anything...", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSubmitCommand(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Command",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StatusBadge(state: AssistantState) {
    val (label, color) = when (state) {
        AssistantState.IDLE -> "READY FOR DIRECTIVE" to Color(0xFF00E5FF)
        AssistantState.LISTENING -> "LISTENING..." to Color(0xFFFF5252)
        AssistantState.THINKING -> "PROCESSING INTENT..." to Color(0xFFFFD700)
        AssistantState.EXECUTING -> "EXECUTING TOOL..." to Color(0xFF76FF03)
        AssistantState.SPEAKING -> "TRANSMITTING..." to Color(0xFF00E5FF)
        AssistantState.ERROR -> "SYSTEM ALERT" to Color(0xFFFF1744)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(color, color))),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = color
            )
        }
    }
}

@Composable
fun ArcReactorOrb(
    state: AssistantState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (state) {
                    AssistantState.THINKING, AssistantState.EXECUTING -> 3000
                    AssistantState.LISTENING -> 1500
                    else -> 10000
                },
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val color = when (state) {
        AssistantState.LISTENING -> Color(0xFFFF3366)
        AssistantState.THINKING -> Color(0xFFFFD700)
        AssistantState.EXECUTING -> Color(0xFF00E676)
        AssistantState.ERROR -> Color(0xFFFF1744)
        else -> Color(0xFF00D2FF)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) * 0.9f

            // Outer ring
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Inner dashed / glow ring
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = radius * 0.75f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Core glow
            val dynamicCoreRadius = radius * 0.45f * if (state == AssistantState.LISTENING) {
                val normAudio = normalizeAudioRms(rmsLevel, true)
                (1f + normAudio * 0.65f)
            } else {
                corePulse
            }

            // Outer acoustic pulse shockwaves when listening
            if (state == AssistantState.LISTENING) {
                val normAudio = normalizeAudioRms(rmsLevel, true)
                val shockwaveRadius = radius * (0.95f + normAudio * 0.3f)
                drawCircle(
                    color = color.copy(alpha = (0.25f + normAudio * 0.55f).coerceIn(0f, 1f)),
                    radius = shockwaveRadius,
                    center = center,
                    style = Stroke(width = (2f + (normAudio * 3f)).dp.toPx())
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = dynamicCoreRadius
                ),
                radius = dynamicCoreRadius,
                center = center
            )
        }

        if (state == AssistantState.THINKING || state == AssistantState.EXECUTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(110.dp),
                color = color,
                strokeWidth = 3.dp
            )
        }
    }
}
