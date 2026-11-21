package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.memory.AerodockMemory
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard

/**
 * Settings card for Aerodock's on-device memory (see AerodockMemory):
 * the saved facts, an add box, per-fact delete and clear-all.
 * Self-contained like KairoAccessCard — it talks to the store directly,
 * so the Settings screen only hosts this one card.
 */
@Composable
fun MemoryCard() {
    val context = LocalContext.current
    var facts by remember { mutableStateOf(AerodockMemory.loadFacts(context)) }
    var draft by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    MinimalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = LucideIcons.Sparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Memory",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Facts the assistant remembers about you across chats. " +
                            "Stored only on this device, and included in new chats.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (facts.isEmpty()) {
                Text(
                    text = "Nothing saved yet. Add a fact below, or ask the assistant to remember one in a chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                facts.forEach { fact ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "•  $fact",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                AerodockMemory.removeFact(context, fact)
                                facts = AerodockMemory.loadFacts(context)
                                note = "Deleted."
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("memory_delete_fact")
                        ) {
                            Icon(
                                imageVector = LucideIcons.Trash2,
                                contentDescription = "Delete this fact",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Add a fact") },
                placeholder = { Text("e.g. My exhibition project is a laser security alarm") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_add_fact_input")
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MinimalButton(
                    text = "Add",
                    onClick = {
                        if (AerodockMemory.addFact(context, draft)) {
                            draft = ""
                            facts = AerodockMemory.loadFacts(context)
                            note = "Saved."
                        } else {
                            note = "That fact is blank, already saved, or memory is full."
                        }
                    },
                    enabled = draft.isNotBlank(),
                    filled = true,
                    testTag = "memory_add_fact_button"
                )
                if (facts.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            AerodockMemory.clearFacts(context)
                            facts = emptyList()
                            note = "All facts cleared."
                        },
                        modifier = Modifier.testTag("memory_clear_all_button")
                    ) {
                        Text("Clear all")
                    }
                }
            }

            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
