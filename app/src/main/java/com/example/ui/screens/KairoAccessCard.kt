package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.kairo.KairoRelayPrefs
import com.example.data.kairo.KairoRelayService
import com.example.data.security.TinkSecretVault
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalToggle
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Settings card for Kairo access (see KairoRelayService): the on/off
 * toggle, the relay URL, and the pairing token. Self-contained on purpose
 * — it talks to KairoRelayPrefs, the vault and the service directly, so
 * the main Settings screen only has to host this one card.
 */
@Composable
fun KairoAccessCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { TinkSecretVault(context) }

    var enabled by remember { mutableStateOf(KairoRelayPrefs.isEnabled(context)) }
    var urlDraft by remember { mutableStateOf(KairoRelayPrefs.relayUrl(context)) }
    var tokenDraft by remember { mutableStateOf("") }
    var savedNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        tokenDraft = vault.getApiKey(KairoRelayPrefs.TOKEN_RECORD_ID)
    }

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
                            imageVector = LucideIcons.Bot,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kairo access",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Let Kairo, your Muse agent, look inside this app: read the " +
                            "workspace and talk to the model here. Read-only — Kairo cannot " +
                            "change or send anything.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MinimalToggle(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        KairoRelayPrefs.setEnabled(context, on)
                        if (on) {
                            KairoRelayService.start(context)
                        } else {
                            KairoRelayService.stop()
                        }
                    },
                    testTag = "kairo_access_toggle"
                )
            }

            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text("Relay URL") },
                placeholder = { Text("https://kairo-relay.<you>.workers.dev") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("Pairing token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        KairoRelayPrefs.setRelayUrl(context, urlDraft)
                        scope.launch {
                            vault.saveApiKey(
                                KairoRelayPrefs.TOKEN_RECORD_ID,
                                tokenDraft.trim()
                            )
                            if (enabled) {
                                KairoRelayService.stop()
                                KairoRelayService.start(context)
                            }
                            savedNote = "Saved. Use the same token as the relay's RELAY_TOKEN secret."
                        }
                    }
                ) {
                    Text("Save")
                }
                TextButton(
                    onClick = {
                        tokenDraft = UUID.randomUUID().toString().replace("-", "") +
                            UUID.randomUUID().toString().replace("-", "")
                        savedNote = "New token created — tap Save, then set it as the " +
                            "relay's RELAY_TOKEN secret in Cloudflare."
                    }
                ) {
                    Text("Generate new token")
                }
            }

            Text(
                text = savedNote ?: ("Status: " + KairoRelayService.statusText),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
