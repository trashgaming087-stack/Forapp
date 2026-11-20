package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SigningKeyEntity
import com.example.ui.components.StatusPill
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeOnEmerald
import com.example.ui.theme.ForgeRed
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeSurfaceVariant
import com.example.ui.theme.ForgeTextSecondary
import kotlin.math.roundToInt

@Composable
fun KeysScreen(
    keys: List<SigningKeyEntity>,
    selectedKeyId: Long,
    onSelectKey: (Long) -> Unit,
    onCreateKey: (alias: String, cn: String, org: String, years: Int, onDone: (Boolean, String?) -> Unit) -> Unit,
    onDeleteKey: (SigningKeyEntity) -> Unit,
    onCopyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keystore Vault", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "RSA-2048 X.509 certificates stored locally as PKCS#12",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForgeTextSecondary
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("new_key_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForgeEmerald,
                        contentColor = ForgeOnEmerald
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Key")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(keys, key = { it.id }) { key ->
                    val isSelected = key.id == selectedKeyId
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = ForgeSurface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ForgeEmerald else ForgeBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = if (isSelected) ForgeEmerald else ForgeCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = key.alias,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPill(
                                        text = if (key.isDefault) "STUDIO DEFAULT" else "CUSTOM RSA-2048",
                                        color = if (key.isDefault) ForgeCyan else ForgeEmerald
                                    )
                                }
                                if (!key.isDefault) {
                                    IconButton(
                                        onClick = { onDeleteKey(key) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete signing key",
                                            tint = ForgeRed
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "CN=${key.commonName} • O=${key.organization} • ${key.validityYears} yrs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForgeTextSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = ForgeSurfaceVariant,
                                border = BorderStroke(1.dp, ForgeBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SHA-256 FINGERPRINT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ForgeTextSecondary
                                        )
                                        Text(
                                            text = key.sha256Fingerprint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(key.sha256Fingerprint))
                                            onCopyMessage("Copied SHA-256 fingerprint")
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = "Copy SHA-256 fingerprint",
                                            tint = ForgeCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            if (isSelected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = ForgeEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Active signing key in Studio",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ForgeEmerald
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSelectKey(key.id) },
                                    modifier = Modifier.height(48.dp),
                                    border = BorderStroke(1.dp, ForgeBorder)
                                ) {
                                    Text("Use in Studio")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateKeyDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { alias, cn, org, years, setError ->
                onCreateKey(alias, cn, org, years) { ok, err ->
                    if (ok) showCreateDialog = false
                    else setError(err ?: "Unable to create key")
                }
            }
        )
    }
}

@Composable
private fun CreateKeyDialog(
    onDismiss: () -> Unit,
    onCreate: (alias: String, cn: String, org: String, years: Int, setError: (String) -> Unit) -> Unit
) {
    var alias by remember { mutableStateOf("release_key") }
    var commonName by remember { mutableStateOf("Mobile Studio") }
    var org by remember { mutableStateOf("Independent Dev") }
    var years by remember { mutableFloatStateOf(25f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!generating) onDismiss() },
        containerColor = ForgeSurface,
        title = { Text("Generate RSA-2048 Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Key Alias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_key_alias_input")
                )
                OutlinedTextField(
                    value = commonName,
                    onValueChange = { commonName = it },
                    label = { Text("Common Name (CN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = org,
                    onValueChange = { org = it },
                    label = { Text("Organization (O)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        text = "Validity: ${years.roundToInt()} years",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = years,
                        onValueChange = { years = it },
                        valueRange = 5f..50f
                    )
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = ForgeRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !generating && alias.isNotBlank(),
                onClick = {
                    generating = true
                    onCreate(alias, commonName, org, years.roundToInt()) { err ->
                        generating = false
                        errorMsg = err
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForgeEmerald,
                    contentColor = ForgeOnEmerald
                ),
                modifier = Modifier.testTag("confirm_create_key_button")
            ) {
                Text(if (generating) "Generating..." else "Generate Key")
            }
        },
        dismissButton = {
            TextButton(enabled = !generating, onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
