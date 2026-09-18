package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memory.MemoryEntity
import com.example.memory.MemoryEventEntity
import com.example.memory.model.MemoryCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    memories: List<MemoryEntity>,
    isMemoryEnabled: Boolean,
    isMemoryPaused: Boolean,
    memoryEvents: List<MemoryEventEntity> = emptyList(),
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onToggleMemoryPaused: (Boolean) -> Unit,
    onSaveMemory: (String, String, String, Float) -> Unit,
    onUpdateMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    onClearAllMemories: () -> Unit,
    onClearConversations: () -> Unit,
    onExportJson: ((String) -> Unit) -> Unit,
    onImportJson: (String, (Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Memories, 1: Audit Log
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var memoryToDelete by remember { mutableStateOf<MemoryEntity?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            MemoryCategory.ALL,
            MemoryCategory.PERSONAL,
            MemoryCategory.PREFERENCES,
            MemoryCategory.ROUTINES,
            MemoryCategory.DEVICES,
            MemoryCategory.PROJECTS,
            MemoryCategory.CONVERSATIONS,
            MemoryCategory.GENERAL
        )
    }

    val filteredMemories = remember(memories, searchQuery, selectedCategory) {
        memories.filter { mem ->
            val matchesCategory = selectedCategory == "all" || mem.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    mem.effectiveKey.contains(searchQuery, ignoreCase = true) ||
                    mem.effectiveText.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Memory Core",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Persistent knowledge engine across conversations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Privacy & Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isMemoryPaused && isMemoryEnabled)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                color = if (isMemoryEnabled && !isMemoryPaused) Color(0xFF4CAF50) else Color.Gray,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!isMemoryEnabled) "Memory Disabled"
                                        else if (isMemoryPaused) "Memory Paused"
                                        else "Memory Active (${memories.size} saved)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isMemoryPaused) "Resume" else "Pause",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Switch(
                                        checked = !isMemoryPaused && isMemoryEnabled,
                                        onCheckedChange = { active ->
                                            if (!isMemoryEnabled) onToggleMemoryEnabled(true)
                                            onToggleMemoryPaused(!active)
                                        },
                                        modifier = Modifier.testTag("memory_pause_switch")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "100% Local on-device storage. Passwords, OTPs & cards are never remembered.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Tabs: Memories vs Activity Log
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Memories (${filteredMemories.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Audit Log (${memoryEvents.size})") }
                    )
                }
            }

            if (selectedTab == 0) {
                // Search & Filter controls
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search memories...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_search_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Category Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory.equals(cat.id, ignoreCase = true),
                                    onClick = { selectedCategory = cat.id },
                                    label = { Text(cat.title) }
                                )
                            }
                        }

                        // Toolbar actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onExportJson { json ->
                                        exportedJsonText = json
                                        showExportDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Export", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Import", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showClearAllConfirm = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear All", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (filteredMemories.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No memories match \"$searchQuery\"."
                                    else "No memories saved yet in this category.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Say \"Ravan, remember that my name is Rahul\" or tap '+' below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMemories, key = { it.id.toString() + it.memoryId }) { mem ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_card_${mem.effectiveKey}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = mem.category.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = mem.effectiveKey.uppercase(),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { editingMemory = mem },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Memory",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { memoryToDelete = mem },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Memory",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = mem.effectiveText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Importance: ${(mem.importance * 100).toInt()}% • ${mem.source}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatDate(mem.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Audit Log
                if (memoryEvents.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = "No memory activity recorded yet. As Ravan learns, modifies, or forgets facts, events will appear here.",
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(memoryEvents, key = { it.id }) { event ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "[${event.eventType}] ${event.description}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatDate(event.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_memory_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = Color.White)
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        var key by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf(MemoryCategory.GENERAL.id) }
        var importance by remember { mutableFloatStateOf(0.7f) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Remember New Fact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Identifier / Key") },
                        placeholder = { Text("e.g. user_name or favorite_drink") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Fact Details") },
                        placeholder = { Text("e.g. User prefers iced black coffee") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cat,
                        onValueChange = { cat = it },
                        label = { Text("Category (personal, preferences, routines, etc.)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column {
                        Text(
                            text = "Importance: ${(importance * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = importance,
                            onValueChange = { importance = it },
                            valueRange = 0.1f..1.0f
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (value.isNotBlank()) {
                            onSaveMemory(key.trim().ifBlank { value.take(25) }, value.trim(), cat.trim(), importance)
                            showAddDialog = false
                            Toast.makeText(context, "Memory stored", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Memory Dialog
    editingMemory?.let { mem ->
        var editContent by remember { mutableStateOf(mem.effectiveText) }
        var editCategory by remember { mutableStateOf(mem.category) }
        var editImportance by remember { mutableFloatStateOf(mem.importance) }

        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("Edit Memory: ${mem.effectiveKey}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Memory Content") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column {
                        Text(
                            text = "Importance: ${(editImportance * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = editImportance,
                            onValueChange = { editImportance = it },
                            valueRange = 0.1f..1.0f
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editContent.isNotBlank()) {
                            onUpdateMemory(
                                mem.copy(
                                    content = editContent.trim(),
                                    value = editContent.trim(),
                                    category = editCategory.trim(),
                                    importance = editImportance
                                )
                            )
                            editingMemory = null
                            Toast.makeText(context, "Memory updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation
    memoryToDelete?.let { mem ->
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            title = { Text("Forget Memory?") },
            text = { Text("Are you sure you want Ravan to forget \"${mem.effectiveText}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMemory(mem)
                        memoryToDelete = null
                        Toast.makeText(context, "Memory forgotten", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Forget", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All confirmation
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Memories?") },
            text = { Text("This will permanently delete all stored preferences, facts, and conversation context from Ravan local storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMemories()
                        showClearAllConfirm = false
                        Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exported Memory JSON") },
            text = {
                Column {
                    Text(
                        text = "You can copy this JSON backup of all Ravan memories:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Ravan Memories", exportedJsonText))
                        showExportDialog = false
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Memories") },
            text = {
                Column {
                    Text(
                        text = "Paste a valid JSON memories array below to restore or add facts:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { importInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("{\"memories\": [{\"key\": \"...\", \"content\": \"...\"}]}") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importInput.isNotBlank()) {
                            onImportJson(importInput) { count ->
                                showImportDialog = false
                                Toast.makeText(context, "Imported $count memories", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
