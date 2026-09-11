package com.example.ui.updates

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.UpdateHistoryEntity
import com.example.ui.JarvisViewModel
import com.example.ui.theme.*
import com.example.update.InstallResult
import com.example.update.UpdateConfig
import com.example.update.UpdateManifest
import com.example.update.UpdateSecurityValidator
import com.example.update.UpdateStatus
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdatesScreen(
    viewModel: JarvisViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val settings by viewModel.updateSettings.collectAsStateWithLifecycle()
    val remoteConfig by viewModel.remoteConfig.collectAsStateWithLifecycle()
    val history by viewModel.updateHistory.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    var customUrlInput by remember(settings.customServerUrl) {
        mutableStateOf(settings.customServerUrl)
    }
    var showUrlError by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Updates, 1: History, 2: Developer Lab

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "APP UPDATES",
                            color = JarvisCyanBright,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "JARVIS Autonomous Update Architecture",
                            color = JarvisTextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("updates_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisCyanBright
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisDarkSurface
                ),
                modifier = Modifier.border(0.5.dp, JarvisCardBorder, androidx.compose.ui.graphics.RectangleShape)
            )
        },
        containerColor = JarvisDarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Maintenance mode banner if enabled
            if (remoteConfig.maintenance.isEnabled) {
                Surface(
                    color = JarvisWarning.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JarvisWarning.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = JarvisWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CLOUD MAINTENANCE IN PROGRESS",
                                color = JarvisWarning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = remoteConfig.maintenance.message,
                                color = JarvisTextPrimary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Local device automation & phone control continue to function offline.",
                                color = JarvisTextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Sub tabs: Updates, History, Developer Lab
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = JarvisDarkSurface,
                contentColor = JarvisCyanBright,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = JarvisCyanBright
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Updates", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History (${history.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Release Lab", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // 1. STATUS HERO CARD
                        UpdateStatusHeroCard(
                            currentVersionName = viewModel.updateManager.currentVersionName,
                            currentVersionCode = viewModel.updateManager.currentVersionCode,
                            status = updateStatus,
                            lastCheckTimestamp = settings.lastUpdateCheckTime,
                            onCheckNow = { viewModel.checkForUpdates(isUserInitiated = true) }
                        )

                        // 2. ACTIVE UPDATE / DOWNLOAD PROGRESS / INSTALL CARD
                        AnimatedVisibility(
                            visible = updateStatus is UpdateStatus.Available ||
                                    updateStatus is UpdateStatus.Downloading ||
                                    updateStatus is UpdateStatus.Downloaded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            ActiveUpdateActionCard(
                                status = updateStatus,
                                onStartDownload = { manifest -> viewModel.startDownloadUpdate(manifest) },
                                onCancelDownload = { viewModel.cancelUpdateDownload() },
                                onInstall = {
                                    viewModel.installUpdate { res ->
                                        if (res is InstallResult.PermissionRequired) {
                                            context.startActivity(res.settingsIntent)
                                        }
                                    }
                                },
                                onDismiss = { code -> viewModel.dismissUpdate(code) }
                            )
                        }

                        // 3. SETTINGS & CHANNEL CONTROLS
                        UpdateSettingsCard(
                            settings = settings,
                            onAutoUpdateChange = { viewModel.setAutoUpdate(it) },
                            onWifiOnlyChange = { viewModel.setWifiOnly(it) },
                            onAutoDownloadChange = { viewModel.setAutoDownload(it) },
                            onMobileDataChange = { viewModel.setUpdateOverMobileData(it) },
                            onIncludeBetaChange = { viewModel.setIncludeBetaUpdates(it) },
                            onChannelChange = { viewModel.setUpdateChannel(it) },
                            customUrl = customUrlInput,
                            onCustomUrlChange = {
                                customUrlInput = it
                                showUrlError = false
                            },
                            onSaveCustomUrl = {
                                val trimmed = customUrlInput.trim()
                                if (trimmed.isBlank() || UpdateSecurityValidator.isHttpsUrl(trimmed)) {
                                    viewModel.setCustomServerUrl(trimmed)
                                    showUrlError = false
                                } else {
                                    showUrlError = true
                                }
                            },
                            showUrlError = showUrlError
                        )
                    }

                    1 -> {
                        // UPDATE HISTORY TAB
                        UpdateHistoryTab(
                            history = history,
                            onClearHistory = { viewModel.clearUpdateHistory() }
                        )
                    }

                    2 -> {
                        // DEVELOPER RELEASE SWITCHER & TESTING LAB
                        DeveloperReleaseLab(
                            settings = settings,
                            remoteConfig = remoteConfig,
                            onToggleSimulation = { viewModel.setSimulationMode(it) },
                            onVersionChange = { viewModel.setSimulatedTargetVersion(it) },
                            onToggleMandatory = { viewModel.setSimulatedMandatory(it) },
                            onToggleMaintenance = { viewModel.setSimulatedMaintenance(it) },
                            onTestUpdate = {
                                viewModel.setSimulationMode(true)
                                viewModel.checkForUpdates(isUserInitiated = true)
                            },
                            onClearCache = { viewModel.clearUpdateCache() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun UpdateStatusHeroCard(
    currentVersionName: String,
    currentVersionCode: Int,
    status: UpdateStatus,
    lastCheckTimestamp: Long,
    onCheckNow: () -> Unit
) {
    val isChecking = status is UpdateStatus.Checking

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, JarvisCyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        color = JarvisDarkSurface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(JarvisCyanPrimary.copy(alpha = 0.15f))
                            .border(1.dp, JarvisCyanBright, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = JarvisCyanBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Installed Version",
                            color = JarvisTextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "v$currentVersionName (Build $currentVersionCode)",
                            color = JarvisTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                StatusBadge(status = status)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Last check timestamp
            val lastCheckFormatted = if (lastCheckTimestamp > 0) {
                DateFormat.format("MMM dd, yyyy • hh:mm a", Date(lastCheckTimestamp)).toString()
            } else {
                "Never checked"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Last Checked", color = JarvisTextMuted, fontSize = 11.sp)
                    Text(lastCheckFormatted, color = JarvisTextSecondary, fontSize = 12.sp)
                }

                Button(
                    onClick = onCheckNow,
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyanPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("check_for_updates_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Error notice if check failed
            if (status is UpdateStatus.Error) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = JarvisError.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = JarvisError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.message,
                            color = JarvisError,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: UpdateStatus) {
    val (label, bg, fg) = when (status) {
        is UpdateStatus.Idle -> Triple("IDLE", JarvisCardSurface, JarvisTextSecondary)
        is UpdateStatus.Checking -> Triple("CHECKING...", JarvisCyanPrimary.copy(alpha = 0.2f), JarvisCyanBright)
        is UpdateStatus.Available -> if (status.isCritical) {
            Triple("CRITICAL UPDATE", JarvisError.copy(alpha = 0.2f), JarvisError)
        } else {
            Triple("NEW UPDATE", JarvisSuccess.copy(alpha = 0.2f), JarvisSuccess)
        }
        is UpdateStatus.Downloading -> Triple("DOWNLOADING", JarvisCyanPrimary.copy(alpha = 0.2f), JarvisCyanBright)
        is UpdateStatus.Downloaded -> Triple("READY TO INSTALL", JarvisSuccess.copy(alpha = 0.2f), JarvisSuccess)
        is UpdateStatus.Installing -> Triple("INSTALLING", JarvisCyanPrimary.copy(alpha = 0.2f), JarvisCyanBright)
        is UpdateStatus.UpToDate -> Triple("UP TO DATE", JarvisSuccess.copy(alpha = 0.15f), JarvisSuccess)
        is UpdateStatus.Error -> Triple("ERROR", JarvisError.copy(alpha = 0.2f), JarvisError)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ActiveUpdateActionCard(
    status: UpdateStatus,
    onStartDownload: (UpdateManifest) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: (Int) -> Unit
) {
    val manifest = when (status) {
        is UpdateStatus.Available -> status.manifest
        is UpdateStatus.Downloaded -> status.manifest
        else -> null
    }

    val isMandatory = when (status) {
        is UpdateStatus.Available -> status.isCritical
        else -> false
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.5.dp,
                if (isMandatory) JarvisError else JarvisCyanBright,
                RoundedCornerShape(16.dp)
            ),
        color = JarvisDarkSurface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMandatory) Icons.Default.Dangerous else Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = if (isMandatory) JarvisError else JarvisCyanBright,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isMandatory) "CRITICAL UPDATE REQUIRED" else "WHAT'S NEW",
                            color = if (isMandatory) JarvisError else JarvisCyanBright,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        manifest?.let {
                            Text(
                                text = "Version ${it.latestVersionName} • ${(it.fileSize / (1024 * 1024f)).formatMb()} MB",
                                color = JarvisTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                manifest?.channel?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(JarvisCardSurface)
                            .border(0.5.dp, JarvisCardBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = it.uppercase(),
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mandatory notice
            if (isMandatory) {
                Text(
                    text = "This version is no longer supported. Please update JARVIS to continue.",
                    color = JarvisError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Release Notes list
            manifest?.releaseNotes?.let { notes ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    notes.forEach { note ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("•", color = JarvisCyanPrimary, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                            Text(note, color = JarvisTextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Different views based on status
            when (status) {
                is UpdateStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Downloading update package...",
                                color = JarvisCyanBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${status.progressPercent}%",
                                color = JarvisCyanBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = JarvisCyanBright,
                            trackColor = JarvisCardSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(status.bytesDownloaded / (1024 * 1024f)).formatMb()} MB of ${(status.totalBytes / (1024 * 1024f)).formatMb()} MB",
                                color = JarvisTextMuted,
                                fontSize = 11.sp
                            )

                            TextButton(onClick = onCancelDownload) {
                                Text("Cancel", color = JarvisError, fontSize = 12.sp)
                            }
                        }
                    }
                }

                is UpdateStatus.Downloaded -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = JarvisSuccess.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = JarvisSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "SHA-256 Checksum Verified",
                                        color = JarvisSuccess,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Package signature confirmed. Ready for official Android installation.",
                                        color = JarvisTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onInstall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("install_update_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyanPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("INSTALL UPDATE NOW", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is UpdateStatus.Available -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onStartDownload(status.manifest) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("download_update_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMandatory) JarvisError else JarvisCyanPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMandatory) "CRITICAL UPDATE" else "UPDATE NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (!isMandatory) {
                            OutlinedButton(
                                onClick = { onDismiss(status.manifest.latestVersionCode) },
                                modifier = Modifier
                                    .weight(0.5f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                            ) {
                                Text("Later", fontSize = 12.sp)
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    settings: com.example.update.UpdateSettings,
    onAutoUpdateChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAutoDownloadChange: (Boolean) -> Unit,
    onMobileDataChange: (Boolean) -> Unit,
    onIncludeBetaChange: (Boolean) -> Unit,
    onChannelChange: (String) -> Unit,
    customUrl: String,
    onCustomUrlChange: (String) -> Unit,
    onSaveCustomUrl: () -> Unit,
    showUrlError: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp)),
        color = JarvisDarkSurface
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = JarvisCyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Update Preferences & Channel", color = JarvisTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)

            // Auto Update
            SettingToggleRow(
                title = "Auto Update",
                subtitle = "Periodically check for official JARVIS releases",
                checked = settings.autoUpdateEnabled,
                onCheckedChange = onAutoUpdateChange,
                testTag = "toggle_auto_update"
            )

            // Wi-Fi Only
            SettingToggleRow(
                title = "Wi-Fi Only",
                subtitle = "Only download updates when connected to unmetered Wi-Fi",
                checked = settings.wifiOnly,
                onCheckedChange = onWifiOnlyChange,
                testTag = "toggle_wifi_only"
            )

            // Auto Download
            SettingToggleRow(
                title = "Auto Download",
                subtitle = "Automatically download updates in background when found",
                checked = settings.autoDownload,
                onCheckedChange = onAutoDownloadChange,
                testTag = "toggle_auto_download"
            )

            // Update over Mobile Data
            SettingToggleRow(
                title = "Update over Mobile Data",
                subtitle = "Allow downloading updates using cellular network",
                checked = settings.updateOverMobileData,
                onCheckedChange = onMobileDataChange,
                testTag = "toggle_mobile_data"
            )

            // Include Beta Updates
            SettingToggleRow(
                title = "Include Beta Updates",
                subtitle = "Receive early-access preview builds and test features",
                checked = settings.includeBetaUpdates,
                onCheckedChange = onIncludeBetaChange,
                testTag = "toggle_beta_updates"
            )

            HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)

            // Release Channel Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Release Channel", color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UpdateConfig.Channel.values().forEach { ch ->
                        val isSelected = settings.updateChannel == ch.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) JarvisCyanPrimary.copy(alpha = 0.2f) else JarvisCardSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) JarvisCyanBright else JarvisCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onChannelChange(ch.id) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ch.displayName.substringBefore(" "),
                                color = if (isSelected) JarvisCyanBright else JarvisTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)

            // Custom Update Server URL
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Update Server URL (HTTPS Only)", color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Default: ${UpdateConfig.DEFAULT_UPDATE_SERVER_URL}",
                    color = JarvisTextMuted,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = onCustomUrlChange,
                    placeholder = { Text("Leave empty for default server", fontSize = 12.sp) },
                    isError = showUrlError,
                    supportingText = {
                        if (showUrlError) {
                            Text("Only secure HTTPS URLs are permitted.", color = JarvisError, fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_server_url_field"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyanBright,
                        unfocusedBorderColor = JarvisCardBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    )
                )

                Button(
                    onClick = onSaveCustomUrl,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyanPrimary.copy(alpha = 0.3f),
                        contentColor = JarvisCyanBright
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Server URL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = JarvisTextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisCyanBright,
                checkedTrackColor = JarvisCyanDark
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun UpdateHistoryTab(
    history: List<UpdateHistoryEntity>,
    onClearHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recorded Update History",
                color = JarvisTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text("Clear All", color = JarvisError, fontSize = 12.sp)
                }
            }
        }

        if (history.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(12.dp)),
                color = JarvisDarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = JarvisTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No Update History Yet",
                        color = JarvisTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Past version installations will be archived here.",
                        color = JarvisTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            history.forEach { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, JarvisCardBorder, RoundedCornerShape(12.dp)),
                    color = JarvisDarkSurface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${item.versionName} (${item.status})",
                                color = JarvisCyanBright,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = DateFormat.format("MMM dd, yyyy", Date(item.installedAt)).toString(),
                                color = JarvisTextMuted,
                                fontSize = 11.sp
                            )
                        }

                        if (item.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.releaseNotes,
                                color = JarvisTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Channel: ${item.channel.uppercase()}",
                            color = JarvisTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperReleaseLab(
    settings: com.example.update.UpdateSettings,
    remoteConfig: com.example.update.RemoteConfig,
    onToggleSimulation: (Boolean) -> Unit,
    onVersionChange: (String) -> Unit,
    onToggleMandatory: (Boolean) -> Unit,
    onToggleMaintenance: (Boolean) -> Unit,
    onTestUpdate: () -> Unit,
    onClearCache: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, JarvisWarning.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        color = JarvisDarkSurface
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = JarvisWarning, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Developer Release Management & Sandbox", color = JarvisWarning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "Simulate real remote updates, mandatory versions, and maintenance state for testing without requiring a live cloud server.",
                color = JarvisTextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)

            // Toggle Simulation Mode
            SettingToggleRow(
                title = "Simulation Mode",
                subtitle = "Override network check with local test manifest",
                checked = settings.simulationMode,
                onCheckedChange = onToggleSimulation,
                testTag = "toggle_sim_mode"
            )

            // Simulated Target Version
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Simulated Target Version", color = JarvisTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2.0.0", "2.5.0", "3.0.0-dev").forEach { ver ->
                        val isSelected = settings.simulatedTargetVersion == ver
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) JarvisCyanPrimary.copy(alpha = 0.25f) else JarvisCardSurface)
                                .border(1.dp, if (isSelected) JarvisCyanBright else JarvisCardBorder, RoundedCornerShape(8.dp))
                                .clickable { onVersionChange(ver) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "v$ver",
                                color = if (isSelected) JarvisCyanBright else JarvisTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Simulate Critical/Mandatory Update
            SettingToggleRow(
                title = "Simulate Mandatory Update",
                subtitle = "Flags update as critical & unskippable",
                checked = settings.simulatedIsMandatory,
                onCheckedChange = onToggleMandatory,
                testTag = "toggle_sim_mandatory"
            )

            // Simulate Cloud Maintenance
            SettingToggleRow(
                title = "Simulate Cloud Maintenance",
                subtitle = "Enables maintenance banner (offline features stay active)",
                checked = remoteConfig.maintenance.isEnabled,
                onCheckedChange = onToggleMaintenance,
                testTag = "toggle_sim_maintenance"
            )

            HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTestUpdate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisWarning, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Trigger Simulation", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                ) {
                    Text("Clear APK Cache", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun Float.formatMb(): String {
    return "%.1f".format(Locale.US, this)
}
