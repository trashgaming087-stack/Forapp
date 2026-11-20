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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BuildRecordEntity
import com.example.ui.components.ApkExportActionsRow
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeOnEmerald
import com.example.ui.theme.ForgeRed
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeTextSecondary
import java.io.File

@Composable
fun BuildsScreen(
    builds: List<BuildRecordEntity>,
    onLoadProject: (Long) -> Unit,
    onSaveApk: (apkPath: String, fileName: String) -> Unit,
    onShareApk: (apkPath: String) -> Unit,
    onInspectApk: (apkPath: String) -> Unit,
    onDeleteBuild: (BuildRecordEntity) -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(builds, query) {
        if (query.isBlank()) builds
        else builds.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

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
                Column {
                    Text("Forged Builds", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${builds.size} local APK artifact(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForgeTextSecondary
                    )
                }
                StatusPill(text = "LOCAL VAULT", color = ForgeCyan)
            }

            if (builds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filter by app name or package ID...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("builds_search_input")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                EmptyBuildsState(
                    isFiltered = builds.isNotEmpty(),
                    onGoToStudio = onGoToStudio,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        BuildHistoryCard(
                            build = item,
                            onEdit = { onLoadProject(item.projectId) },
                            onSave = {
                                val name = File(item.apkFilePath).name
                                onSaveApk(item.apkFilePath, name)
                            },
                            onShare = { onShareApk(item.apkFilePath) },
                            onInspect = { onInspectApk(item.apkFilePath) },
                            onDelete = { onDeleteBuild(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildHistoryCard(
    build: BuildRecordEntity,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val fileExists = remember(build.apkFilePath) { File(build.apkFilePath).exists() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("build_card_${build.id}"),
        shape = MaterialTheme.shapes.large,
        color = ForgeSurface,
        border = BorderStroke(1.dp, ForgeBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = build.appName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(
                            text = "v${build.versionName} (${build.versionCode})",
                            color = ForgeCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = build.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = ForgeEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit project in Studio",
                            tint = ForgeCyan
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete build record",
                            tint = ForgeRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (build.isVerified) "SIGNED (${build.signingAlias})" else "UNVERIFIED",
                    color = if (build.isVerified) ForgeEmerald else ForgeAmber
                )
                Text(
                    text = "${formatFileSize(build.fileSizeBytes)} • ${build.durationMs}ms • ${formatTimestamp(build.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForgeTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (fileExists) {
                ApkExportActionsRow(
                    onSave = onSave,
                    onShare = onShare,
                    onInspect = onInspect
                )
            } else {
                Text(
                    text = "APK file was removed from local cache.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForgeAmber
                )
            }
        }
    }
}

@Composable
private fun EmptyBuildsState(
    isFiltered: Boolean,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Inventory2,
            contentDescription = null,
            tint = ForgeCyan.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isFiltered) "No matching builds" else "No forged APKs yet",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isFiltered) "Try another app name or package ID."
            else "Configure a website URL, HTML bundle, or starter template in Studio to compile and sign your first APK.",
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeTextSecondary,
            textAlign = TextAlign.Center
        )
        if (!isFiltered) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onGoToStudio,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForgeEmerald,
                    contentColor = ForgeOnEmerald
                )
            ) {
                Icon(Icons.Filled.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Studio")
            }
        }
    }
}
