package com.example.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devices.*
import com.example.ui.JarvisViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.deviceManager.discovery.isScanning.collectAsState()
    val discoveredDevices by viewModel.deviceManager.discovery.discoveredDevices.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var deviceToRename by remember { mutableStateOf<Device?>(null) }
    var deviceToAuthorize by remember { mutableStateOf<Device?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val filterCategories = listOf("ALL", "PHONE", "LIGHTS", "TV", "SPEAKERS", "PC", "OTHER")

    val filteredDevices = remember(devices, selectedFilter) {
        when (selectedFilter) {
            "PHONE" -> devices.filter { it.type == DeviceType.PHONE }
            "LIGHTS" -> devices.filter { it.type == DeviceType.SMART_LIGHT || it.type == DeviceType.SMART_PLUG }
            "TV" -> devices.filter { it.type == DeviceType.SMART_TV }
            "SPEAKERS" -> devices.filter { it.type == DeviceType.SPEAKER }
            "PC" -> devices.filter { it.type == DeviceType.PC }
            "OTHER" -> devices.filter { it.type in listOf(DeviceType.THERMOSTAT, DeviceType.SMART_FAN, DeviceType.OTHER) }
            else -> devices
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CONNECTED DEVICES",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "${devices.size} Configured • ${devices.count { it.status == DeviceStatus.ONLINE || it.status == DeviceStatus.CONNECTED }} Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.deviceManager.discovery.startScan()
                            }
                        },
                        modifier = Modifier.testTag("scan_devices_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CyanAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = "Scan Devices", tint = CyanAccent)
                        }
                    }
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_device_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Device", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(filterCategories) { cat ->
                        FilterChip(
                            selected = selectedFilter == cat,
                            onClick = { selectedFilter = cat },
                            label = { Text(cat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                selectedLabelColor = CyanAccent,
                                containerColor = SurfaceNavy,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == cat,
                                borderColor = if (selectedFilter == cat) CyanAccent else BorderNavy
                            )
                        )
                    }
                }
            }

            // Discovered devices banner if scan found new items
            if (discoveredDevices.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArcBlue.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ArcBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "DISCOVERED NEARBY (${discoveredDevices.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ArcBlue
                                )
                                TextButton(onClick = { viewModel.deviceManager.discovery.stopScan() }) {
                                    Text("Dismiss", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            discoveredDevices.forEach { dev ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column {
                                        Text(dev.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("${dev.type.displayName} • ${dev.manufacturer}", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.deviceManager.registry.registerDevice(dev.copy(isAuthorized = true))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Pair & Authorize", color = DeepNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Devices list
            if (filteredDevices.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                    ) {
                        Text(
                            text = "No devices registered in this category.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(filteredDevices, key = { it.deviceId }) { device ->
                    DeviceCard(
                        device = device,
                        onTogglePower = {
                            viewModel.triggerDeviceAction(device.deviceId, "toggle_power")
                        },
                        onTestConnection = {
                            viewModel.triggerDeviceAction(device.deviceId, "test_connection")
                        },
                        onRename = { deviceToRename = device },
                        onRemove = { viewModel.removeDevice(device.deviceId) },
                        onAuthorize = { deviceToAuthorize = device }
                    )
                }
            }
        }
    }

    // Add manual device dialog
    if (showAddDialog) {
        AddDeviceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, type, manufacturer, conn, room ->
                viewModel.addManualDevice(name, type, manufacturer, conn, room)
                showAddDialog = false
            }
        )
    }

    // Rename dialog
    deviceToRename?.let { dev ->
        var newName by remember { mutableStateOf(dev.name) }
        AlertDialog(
            onDismissRequest = { deviceToRename = null },
            title = { Text("Rename Device", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Device Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderNavy
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renameDevice(dev.deviceId, newName.trim())
                    }
                    deviceToRename = null
                }) {
                    Text("Save", color = CyanAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceNavy
        )
    }

    // Authorize dialog
    deviceToAuthorize?.let { dev ->
        var token by remember { mutableStateOf("JARVIS_KEY_2026") }
        AlertDialog(
            onDismissRequest = { deviceToAuthorize = null },
            title = { Text("Authorize ${dev.name}", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the pairing PIN or security key from your device to grant control permissions to JARVIS.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Pairing PIN / Auth Token") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderNavy
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.authorizeDevice(dev.deviceId, token)
                        deviceToAuthorize = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Grant Permission", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToAuthorize = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceNavy
        )
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onTogglePower: () -> Unit,
    onTestConnection: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPoweredOn = device.state["power"] as? Boolean ?: false

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPoweredOn) CyanAccent.copy(alpha = 0.4f) else BorderNavy
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isPoweredOn) CyanAccent.copy(alpha = 0.2f) else BorderNavy.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = getDeviceIcon(device.type),
                            contentDescription = null,
                            tint = if (isPoweredOn) CyanAccent else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "${device.roomLocation} • ${device.manufacturer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                StatusBadge(device.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info row: Connection & Capabilities
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Link: ${device.connectionType.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                if (device.capabilities.contains(Capability.POWER)) {
                    Text(
                        text = if (isPoweredOn) "STATE: ON" else "STATE: OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPoweredOn) HologramGreen else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!device.isAuthorized) {
                    Button(
                        onClick = onAuthorize,
                        colors = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Authorize", color = DeepNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (device.capabilities.contains(Capability.POWER)) {
                    Button(
                        onClick = onTogglePower,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPoweredOn) CrimsonRed.copy(alpha = 0.8f) else CyanAccent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isPoweredOn) "Turn Off" else "Turn On",
                            color = if (isPoweredOn) Color.White else DeepNavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderNavy),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Link", fontSize = 12.sp)
                }

                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = CrimsonRed.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: DeviceStatus) {
    val bgColor: Color
    val textColor: Color
    when (status) {
        DeviceStatus.ONLINE, DeviceStatus.CONNECTED -> {
            bgColor = HologramGreen.copy(alpha = 0.2f)
            textColor = HologramGreen
        }
        DeviceStatus.OFFLINE -> {
            bgColor = CrimsonRed.copy(alpha = 0.2f)
            textColor = CrimsonRed
        }
        DeviceStatus.REQUIRES_PERMISSION -> {
            bgColor = WarningYellow.copy(alpha = 0.2f)
            textColor = WarningYellow
        }
    }

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.displayName.uppercase(),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

fun getDeviceIcon(type: DeviceType): ImageVector {
    return when (type) {
        DeviceType.PHONE -> Icons.Default.PhoneAndroid
        DeviceType.SMART_LIGHT -> Icons.Default.Lightbulb
        DeviceType.SMART_PLUG -> Icons.Default.Power
        DeviceType.SMART_TV -> Icons.Default.Tv
        DeviceType.SPEAKER -> Icons.Default.Speaker
        DeviceType.CHROMECAST -> Icons.Default.Cast
        DeviceType.PC -> Icons.Default.Computer
        DeviceType.THERMOSTAT -> Icons.Default.Thermostat
        DeviceType.SMART_FAN -> Icons.Default.Air
        DeviceType.WEARABLE -> Icons.Default.Watch
        else -> Icons.Default.Sensors
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, type: DeviceType, manufacturer: String, conn: ConnectionType, room: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("Living Room") }
    var manufacturer by remember { mutableStateOf("Generic") }
    var selectedType by remember { mutableStateOf(DeviceType.SMART_LIGHT) }
    var selectedConn by remember { mutableStateOf(ConnectionType.WIFI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Manual Device", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name (e.g. Study Light)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderNavy
                    )
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room Location") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderNavy
                    )
                )

                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Brand / Manufacturer") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderNavy
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(name.trim(), selectedType, manufacturer.trim(), selectedConn, room.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("Add", color = DeepNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceNavy
    )
}
