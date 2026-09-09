package com.example.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memory.MemoryEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MemoryScreen(
    memories: List<MemoryEntity>,
    onSaveMemory: (key: String, value: String) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    onClearAllMemories: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val timeFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Memory Item", color = JarvisTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Topic / Key (e.g. Favorite Color)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_key_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text("Value / Fact (e.g. Blue)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_value_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                            onSaveMemory(keyInput.trim(), valueInput.trim())
                            keyInput = ""
                            valueInput = ""
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_memory_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanPrimary, contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisCardSurface
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Memories", color = JarvisTextPrimary) },
            text = { Text("Delete all personal memories saved in JARVIS local storage?", color = JarvisTextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMemories()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_memories")
                ) {
                    Text("Clear All", color = JarvisError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisCardSurface
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisDarkBackground)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEURAL MEMORY VAULT",
                color = JarvisCyanPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Row {
                if (memories.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear all memories",
                            tint = JarvisTextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_memory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add memory",
                        tint = JarvisCyanBright
                    )
                }
            }
        }

        Text(
            text = "JARVIS only recalls facts and preferences you explicitly ask it to remember.",
            color = JarvisTextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No memories saved yet.\nSay \"Remember that my favorite color is blue\" or tap + above.",
                    color = JarvisTextMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(memories, key = { it.id }) { memory ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, JarvisCardBorder, RoundedCornerShape(14.dp))
                            .testTag("memory_item_${memory.id}"),
                        color = JarvisDarkSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = memory.key.replaceFirstChar { it.uppercase() },
                                    color = JarvisCyanBright,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = memory.value,
                                    color = JarvisTextPrimary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Saved ${timeFormatter.format(Date(memory.updatedAt))}",
                                    color = JarvisTextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            IconButton(
                                onClick = { onDeleteMemory(memory) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete memory",
                                    tint = JarvisTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
