package com.example.ui.home

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.permissions.AudioPermissionBanner
import com.example.permissions.AudioPermissionDialogs
import com.example.permissions.AudioPermissionStatusBadge
import com.example.permissions.PermissionManager
import com.example.permissions.rememberStructuredAudioPermissionController
import com.example.ui.AssistantState
import com.example.ui.JarvisUiState
import com.example.ui.components.JarvisOrb
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    uiState: JarvisUiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onSpeakResponse: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    // Accompanist structured permissions controller for RECORD_AUDIO
    val audioPermissionController = rememberStructuredAudioPermissionController(
        onPermissionGranted = onStartListening
    )

    // System Voice Input Activity Fallback (100% reliable native Google speech prompt)
    val speechActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                onSubmitCommand(spoken)
            }
        }
    }

    val launchSystemSpeechIntent: () -> Unit = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command for JARVIS...")
            }
            speechActivityLauncher.launch(intent)
        } catch (e: Exception) {
            audioPermissionController.onVoiceActionRequested()
        }
    }

    // Render structured permission dialogs (rationale / app settings)
    AudioPermissionDialogs(controller = audioPermissionController)

    val samplePrompts = listOf(
        "Toggle Wi-Fi",
        "Turn on Bluetooth",
        "What's my battery?",
        "Turn on flashlight",
        "Open YouTube",
        "Set an alarm for 7 AM",
        "What time is it?",
        "Weather in Delhi",
        "Open camera",
        "Rahul ko call karo",
        "Turn off flashlight"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisDarkBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Status Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (uiState.assistantState == AssistantState.ERROR) JarvisError else JarvisSuccess)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "JARVIS SYSTEM",
                    color = JarvisCyanPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AudioPermissionStatusBadge(isGranted = audioPermissionController.isGranted)
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(JarvisCardSurface)
                        .testTag("mute_toggle_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isVoiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (uiState.isVoiceMuted) "Unmute JARVIS" else "Mute JARVIS",
                        tint = if (uiState.isVoiceMuted) JarvisTextMuted else JarvisCyanBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Accompanist Structured Permission Banner
        AudioPermissionBanner(controller = audioPermissionController)

        Spacer(modifier = Modifier.height(16.dp))

        // Large Animated Orb
        JarvisOrb(
            state = uiState.assistantState,
            rmsLevel = uiState.audioRmsLevel,
            size = 220.dp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // State & Waveform Status
        val statusHeadline = when (uiState.assistantState) {
            AssistantState.IDLE -> "How can I help you, Sir?"
            AssistantState.LISTENING -> "Listening to command..."
            AssistantState.THINKING -> "Processing Request..."
            AssistantState.EXECUTING -> "Executing ${uiState.activeToolName ?: "Action"}..."
            AssistantState.SPEAKING -> "Speaking Response..."
            AssistantState.ERROR -> "Execution Notice"
        }

        Text(
            text = statusHeadline,
            style = MaterialTheme.typography.titleMedium,
            color = when (uiState.assistantState) {
                AssistantState.ERROR -> JarvisError
                AssistantState.LISTENING, AssistantState.SPEAKING -> JarvisCyanBright
                else -> JarvisTextPrimary
            },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        WaveformVisualizer(
            isAnimating = uiState.assistantState == AssistantState.LISTENING ||
                    uiState.assistantState == AssistantState.SPEAKING
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Recognized Command Preview (if present)
        AnimatedVisibility(
            visible = uiState.currentCommand.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, JarvisCyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .testTag("user_command_card"),
                color = JarvisCardSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User",
                        tint = JarvisCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "\"${uiState.currentCommand}\"",
                        color = JarvisTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Assistant Response Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
                .animateContentSize()
                .testTag("assistant_response_card"),
            color = JarvisDarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "JARVIS",
                            tint = JarvisCyanBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "JARVIS",
                            color = JarvisCyanBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    if (uiState.activeToolName != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(JarvisCyanPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = uiState.activeToolName,
                                color = JarvisCyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = uiState.assistantResponse,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary,
                    lineHeight = 22.sp
                )

                if (uiState.activeToolResult != null && !uiState.activeToolResult.success) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: ${uiState.activeToolResult.errorCode ?: "Action failed"}",
                        color = JarvisError,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onSpeakResponse,
                        modifier = Modifier.testTag("replay_voice_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = JarvisCyanPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Replay",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Replay Speech", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Tap-to-Talk Microphone Action Button
        val isListening = uiState.assistantState == AssistantState.LISTENING

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isListening) {
                            listOf(JarvisCyanBright, JarvisCyanPrimary, JarvisCyanDark)
                        } else {
                            listOf(JarvisCyanPrimary, JarvisBlueAccent, JarvisCardSurface)
                        }
                    )
                )
                .clickable {
                    if (isListening) {
                        onStopListening()
                    } else {
                        audioPermissionController.onVoiceActionRequested()
                    }
                }
                .testTag("microphone_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isListening) "TAP TO STOP" else "TAP TO TALK",
            color = JarvisCyanPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        if (!isListening) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = launchSystemSpeechIntent,
                modifier = Modifier.testTag("system_voice_dialog_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = JarvisCyanBright,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "System Voice Dialog",
                    color = JarvisCyanBright,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Command Suggestions Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            samplePrompts.forEach { prompt ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, JarvisCardBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            textInput = prompt
                            onSubmitCommand(prompt)
                        }
                        .testTag("quick_command_${prompt.take(6)}"),
                    color = JarvisCardSurface
                ) {
                    Text(
                        text = prompt,
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text input bar for typing commands
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, JarvisCardBorder, RoundedCornerShape(14.dp)),
            color = JarvisCardSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input"),
                    placeholder = {
                        Text("Or type a command for JARVIS...", color = JarvisTextMuted, fontSize = 13.sp)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        cursorColor = JarvisCyanPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            onSubmitCommand(textInput)
                            keyboardController?.hide()
                            textInput = ""
                        }
                    })
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSubmitCommand(textInput)
                            keyboardController?.hide()
                            textInput = ""
                        } else {
                            launchSystemSpeechIntent()
                        }
                    },
                    modifier = Modifier.testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = if (textInput.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                        contentDescription = if (textInput.isNotBlank()) "Send" else "Voice Input",
                        tint = JarvisCyanPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Creator Attribution Footer
        Text(
            text = stringResource(R.string.creator_attribution),
            color = JarvisTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .testTag("creator_attribution")
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
