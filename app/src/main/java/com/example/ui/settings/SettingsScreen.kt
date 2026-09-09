package com.example.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AIProviderType
import com.example.ai.AISettings
import com.example.permissions.PermissionManager
import com.example.services.JarvisAccessibilityService
import com.example.services.JarvisNotificationListenerService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    speechRate: Float,
    pitch: Float,
    preferredLanguage: String,
    aiSettings: AISettings = AISettings(),
    onSpeechRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSelectProvider: (AIProviderType) -> Unit = {},
    onUpdateApiKey: (String) -> Unit,
    onUpdateOpenRouterConfig: (apiKey: String, model: String) -> Unit = { _, _ -> },
    onTestOpenRouterConnection: (suspend (apiKey: String, model: String) -> Pair<Boolean, String>)? = null,
    onClearHistory: () -> Unit,
    onClearMemories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var geminiKeyInput by remember(aiSettings.geminiCustomApiKey) { mutableStateOf(aiSettings.geminiCustomApiKey) }
    var openRouterKeyInput by remember(aiSettings.openRouterApiKey) { mutableStateOf(aiSettings.openRouterApiKey) }
    var openRouterModelInput by remember(aiSettings.openRouterModel) { mutableStateOf(aiSettings.openRouterModel) }

    var isApiKeyVisible by remember { mutableStateOf(false) }
    var showGeminiSavedNotice by remember { mutableStateOf(false) }
    var showOpenRouterSavedNotice by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val popularOpenRouterModels = remember {
        listOf(
            "google/gemini-2.5-flash",
            "deepseek/deepseek-chat",
            "meta-llama/llama-3.3-70b-instruct",
            "anthropic/claude-3.5-sonnet",
            "openai/gpt-4o-mini"
        )
    }

    var confirmCallState by remember { mutableStateOf(true) }
    var confirmSmsState by remember { mutableStateOf(true) }
    var confirmWhatsAppState by remember { mutableStateOf(true) }

    // Check live permission states
    var micGranted by remember { mutableStateOf(PermissionManager.hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var contactsGranted by remember { mutableStateOf(PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) }
    var callGranted by remember { mutableStateOf(PermissionManager.hasPermission(context, Manifest.permission.CALL_PHONE)) }
    var smsGranted by remember { mutableStateOf(PermissionManager.hasPermission(context, Manifest.permission.SEND_SMS)) }
    var notifAccessGranted by remember { mutableStateOf(JarvisNotificationListenerService.isNotificationAccessGranted(context)) }

    val genericPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        micGranted = results[Manifest.permission.RECORD_AUDIO] ?: micGranted
        contactsGranted = results[Manifest.permission.READ_CONTACTS] ?: contactsGranted
        callGranted = results[Manifest.permission.CALL_PHONE] ?: callGranted
        smsGranted = results[Manifest.permission.SEND_SMS] ?: smsGranted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisDarkBackground)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "SYSTEM CONFIGURATION",
            color = JarvisCyanPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        // 1. GENERAL / SPEECH SYNTHESIS
        SettingsSectionCard(title = "Voice & Speech Synthesis", icon = Icons.Default.RecordVoiceOver) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Language selection
                Text("Assistant Language", color = JarvisTextSecondary, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageChip(
                        label = "English (US)",
                        selected = preferredLanguage == "en",
                        onClick = { onLanguageChange("en") }
                    )
                    LanguageChip(
                        label = "English (India)",
                        selected = preferredLanguage == "en-in",
                        onClick = { onLanguageChange("en-in") }
                    )
                    LanguageChip(
                        label = "Hindi (हिन्दी)",
                        selected = preferredLanguage == "hi",
                        onClick = { onLanguageChange("hi") }
                    )
                }

                // Speech Speed Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speech Rate", color = JarvisTextSecondary, fontSize = 13.sp)
                        Text(String.format("%.1fx", speechRate), color = JarvisCyanPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = JarvisCyanBright,
                            activeTrackColor = JarvisCyanPrimary,
                            inactiveTrackColor = JarvisCardBorder
                        ),
                        modifier = Modifier.testTag("speech_rate_slider")
                    )
                }

                // Pitch Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pitch Modulation", color = JarvisTextSecondary, fontSize = 13.sp)
                        Text(String.format("%.1fx", pitch), color = JarvisCyanPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.6f..1.6f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = JarvisCyanBright,
                            activeTrackColor = JarvisCyanPrimary,
                            inactiveTrackColor = JarvisCardBorder
                        ),
                        modifier = Modifier.testTag("pitch_slider")
                    )
                }
            }
        }

        // 2. AI MODEL & CONFIGURATION
        SettingsSectionCard(title = "AI Intelligence Engine", icon = Icons.Default.Psychology) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Configure your AI provider. Gemini AI is active by default with zero input needed. You can switch to OpenRouter to use any custom model with your own key.",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Provider Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProviderSelectorCard(
                        title = "Gemini AI",
                        badge = "Default • Ready",
                        icon = Icons.Default.AutoAwesome,
                        isSelected = aiSettings.providerType == AIProviderType.GEMINI,
                        onClick = {
                            onSelectProvider(AIProviderType.GEMINI)
                            showOpenRouterSavedNotice = false
                            connectionTestResult = null
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ProviderSelectorCard(
                        title = "OpenRouter",
                        badge = "Custom Key & Model",
                        icon = Icons.Default.Hub,
                        isSelected = aiSettings.providerType == AIProviderType.OPENROUTER,
                        onClick = {
                            onSelectProvider(AIProviderType.OPENROUTER)
                            showGeminiSavedNotice = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (aiSettings.providerType == AIProviderType.GEMINI) {
                    // GEMINI DEFAULT MODE
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, JarvisCyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        color = JarvisCyanDark.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = JarvisSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Built-in Gemini Active",
                                    color = JarvisCyanBright,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ready out-of-the-box. No API key input is required.",
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Optional Custom Gemini Key Override
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Custom Gemini API Key (Optional override)",
                            color = JarvisTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = {
                                geminiKeyInput = it
                                showGeminiSavedNotice = false
                            },
                            placeholder = { Text("Leave empty to use built-in key...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_api_key_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyanPrimary,
                                unfocusedBorderColor = JarvisCardBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showGeminiSavedNotice) {
                                Text("Gemini key updated.", color = JarvisSuccess, fontSize = 12.sp)
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Button(
                                onClick = {
                                    onUpdateApiKey(geminiKeyInput.trim())
                                    showGeminiSavedNotice = true
                                },
                                modifier = Modifier.testTag("save_api_key_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // OPENROUTER CUSTOM MODE
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Connect your OpenRouter account to use DeepSeek, Llama, Claude, GPT, or any OpenRouter model. Your credentials remain private on device.",
                            color = JarvisTextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        // OpenRouter API Key
                        OutlinedTextField(
                            value = openRouterKeyInput,
                            onValueChange = {
                                openRouterKeyInput = it
                                showOpenRouterSavedNotice = false
                                connectionTestResult = null
                            },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("sk-or-v1-...") },
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isApiKeyVisible) "Hide Key" else "Show Key",
                                        tint = JarvisTextSecondary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openrouter_api_key_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyanPrimary,
                                unfocusedBorderColor = JarvisCardBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            )
                        )

                        // OpenRouter Model Name
                        OutlinedTextField(
                            value = openRouterModelInput,
                            onValueChange = {
                                openRouterModelInput = it
                                showOpenRouterSavedNotice = false
                                connectionTestResult = null
                            },
                            label = { Text("OpenRouter Model Name") },
                            placeholder = { Text("e.g. google/gemini-2.5-flash") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openrouter_model_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyanPrimary,
                                unfocusedBorderColor = JarvisCardBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            )
                        )

                        // Quick Model Suggestion Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Popular Models:", color = JarvisTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                popularOpenRouterModels.take(2).forEach { model ->
                                    QuickModelChip(
                                        model = model,
                                        selected = openRouterModelInput.trim() == model,
                                        onClick = {
                                            openRouterModelInput = model
                                            connectionTestResult = null
                                        }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                popularOpenRouterModels.drop(2).take(2).forEach { model ->
                                    QuickModelChip(
                                        model = model,
                                        selected = openRouterModelInput.trim() == model,
                                        onClick = {
                                            openRouterModelInput = model
                                            connectionTestResult = null
                                        }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                popularOpenRouterModels.drop(4).forEach { model ->
                                    QuickModelChip(
                                        model = model,
                                        selected = openRouterModelInput.trim() == model,
                                        onClick = {
                                            openRouterModelInput = model
                                            connectionTestResult = null
                                        }
                                    )
                                }
                            }
                        }

                        // Connection Test Feedback
                        connectionTestResult?.let { (success, message) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (success) JarvisSuccess.copy(alpha = 0.5f) else JarvisError.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                color = if (success) JarvisSuccess.copy(alpha = 0.1f) else JarvisError.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (success) JarvisSuccess else JarvisError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = message,
                                        color = if (success) JarvisSuccess else JarvisError,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (showOpenRouterSavedNotice) {
                            Text("OpenRouter saved! Engine is active.", color = JarvisSuccess, fontSize = 12.sp)
                        }

                        // Buttons: Test Connection & Save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (onTestOpenRouterConnection != null) {
                                        coroutineScope.launch {
                                            isTestingConnection = true
                                            connectionTestResult = onTestOpenRouterConnection(
                                                openRouterKeyInput.trim(),
                                                openRouterModelInput.trim()
                                            )
                                            isTestingConnection = false
                                        }
                                    }
                                },
                                enabled = !isTestingConnection && openRouterKeyInput.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_openrouter_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = JarvisCyanBright
                                    )
                                } else {
                                    Text("Test Connection", fontSize = 12.sp, color = JarvisCyanBright)
                                }
                            }

                            Button(
                                onClick = {
                                    onUpdateOpenRouterConfig(
                                        openRouterKeyInput.trim(),
                                        openRouterModelInput.trim()
                                    )
                                    onSelectProvider(AIProviderType.OPENROUTER)
                                    showOpenRouterSavedNotice = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_openrouter_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save & Activate", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Text(
                            text = "Note: If the key is left empty or OpenRouter fails, JARVIS will automatically use default Gemini AI.",
                            color = JarvisTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 3. ANDROID PERMISSIONS AUDIT
        SettingsSectionCard(title = "Android Permissions & Safety", icon = Icons.Default.Security) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionStatusRow(
                    name = "Microphone",
                    description = "Required for natural speech recognition",
                    isGranted = micGranted,
                    onRequest = {
                        genericPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                )

                PermissionStatusRow(
                    name = "Contacts",
                    description = "Required to find contact names when calling or texting",
                    isGranted = contactsGranted,
                    onRequest = {
                        genericPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    }
                )

                PermissionStatusRow(
                    name = "Phone Calls",
                    description = "Required to place hands-free voice calls",
                    isGranted = callGranted,
                    onRequest = {
                        genericPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                    }
                )

                PermissionStatusRow(
                    name = "SMS",
                    description = "Required to compose and send SMS messages",
                    isGranted = smsGranted,
                    onRequest = {
                        genericPermissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                    }
                )

                PermissionStatusRow(
                    name = "Notification Access",
                    description = "Required to read incoming alerts (e.g. WhatsApp)",
                    isGranted = notifAccessGranted,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                PermissionStatusRow(
                    name = "Accessibility Assistant",
                    description = "Required for safe UI automation (e.g. clicking buttons on requested apps)",
                    isGranted = JarvisAccessibilityService.isRunning(),
                    onRequest = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open System App Settings", color = JarvisCyanBright)
                }
            }
        }

        // 4. CONFIRMATION & RISK POLICIES
        SettingsSectionCard(title = "Action Risk & Confirmation", icon = Icons.Default.VerifiedUser) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm Phone Calls", color = JarvisTextPrimary, fontSize = 13.sp)
                        Text("Prompt with dialog before dialling", color = JarvisTextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = confirmCallState,
                        onCheckedChange = { confirmCallState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisCyanBright,
                            checkedTrackColor = JarvisCyanDark
                        )
                    )
                }

                Divider(color = JarvisCardBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm SMS Messages", color = JarvisTextPrimary, fontSize = 13.sp)
                        Text("Display text preview and Send button before sending", color = JarvisTextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = confirmSmsState,
                        onCheckedChange = { confirmSmsState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisCyanBright,
                            checkedTrackColor = JarvisCyanDark
                        )
                    )
                }

                Divider(color = JarvisCardBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm WhatsApp Messages", color = JarvisTextPrimary, fontSize = 13.sp)
                        Text("Verify recipient and message draft before sending", color = JarvisTextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = confirmWhatsAppState,
                        onCheckedChange = { confirmWhatsAppState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisCyanBright,
                            checkedTrackColor = JarvisCyanDark
                        )
                    )
                }
            }
        }

        // 5. PRIVACY & LOCAL DATA VAULT
        SettingsSectionCard(title = "Privacy & Local Data Controls", icon = Icons.Default.Lock) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "All memories and conversation logs reside exclusively in your device's local Room database. Never uploaded without consent.",
                    color = JarvisTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisError),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear History", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onClearMemories,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisError),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear Memory", fontSize = 12.sp)
                    }
                }
            }
        }

        // 6. ABOUT
        SettingsSectionCard(title = "About JARVIS AI", icon = Icons.Default.Info) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Version 1.0.0 (Production Release)", color = JarvisCyanBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Architecture: Clean Architecture + MVVM + Jetpack Compose", color = JarvisTextSecondary, fontSize = 12.sp)
                Text("Tools: Official Android APIs (AlarmClock, ContactsContract, CameraManager, Telephony, Open-Meteo)", color = JarvisTextMuted, fontSize = 11.sp)
                Text("Safety: Strict confirmation policy for medium & high risk actions.", color = JarvisTextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.creator_attribution),
                    color = JarvisCyanBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("settings_creator_attribution")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.creator_attribution),
            color = JarvisTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("settings_footer_attribution")
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp)),
        color = JarvisDarkSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = JarvisCyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = JarvisTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (selected) JarvisCyanPrimary else JarvisCardBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        color = if (selected) JarvisCyanPrimary.copy(alpha = 0.2f) else JarvisCardSurface
    ) {
        Text(
            text = label,
            color = if (selected) JarvisCyanBright else JarvisTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PermissionStatusRow(
    name: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) JarvisSuccess else JarvisWarning)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(name, color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(description, color = JarvisTextMuted, fontSize = 11.sp)
        }

        if (isGranted) {
            Text("GRANTED", color = JarvisSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } else {
            Button(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanPrimary, contentColor = Color.Black)
            ) {
                Text("GRANT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProviderSelectorCard(
    title: String,
    badge: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) JarvisCyanPrimary else JarvisCardBorder
    val bgColor = if (isSelected) JarvisCyanDark.copy(alpha = 0.35f) else JarvisCardSurface

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) JarvisCyanBright else JarvisTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(JarvisCyanBright)
                    )
                }
            }

            Text(
                text = title,
                color = if (isSelected) JarvisCyanBright else JarvisTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = badge,
                color = if (isSelected) JarvisCyanPrimary else JarvisTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun QuickModelChip(
    model: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = model.substringAfter("/")
    val borderColor = if (selected) JarvisCyanPrimary else JarvisCardBorder
    val bgColor = if (selected) JarvisCyanPrimary.copy(alpha = 0.2f) else JarvisCardSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) JarvisCyanBright else JarvisTextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

