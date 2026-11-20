package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ApkInspectionReport
import com.example.ui.components.ApkExportActionsRow
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeRed
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeSurfaceVariant
import com.example.ui.theme.ForgeTextSecondary
import com.example.ui.viewmodel.InspectorUiState

@Composable
fun InspectorScreen(
    state: InspectorUiState,
    onBack: () -> Unit,
    onSaveApk: (apkPath: String, fileName: String) -> Unit,
    onShareApk: (apkPath: String) -> Unit,
    onCopyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inspector_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("APK Binary Inspector", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Verified with Android apksig & AXML reader",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForgeTextSecondary
                    )
                }
            }

            when (state) {
                InspectorUiState.Idle, InspectorUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForgeEmerald)
                    }
                }

                is InspectorUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.message, color = ForgeRed, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                is InspectorUiState.Loaded -> {
                    InspectorContent(
                        report = state.report,
                        apkPath = state.apkPath,
                        onSave = { onSaveApk(state.apkPath, state.report.fileName) },
                        onShare = { onShareApk(state.apkPath) },
                        onCopyMessage = onCopyMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorContent(
    report: ApkInspectionReport,
    apkPath: String,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCopyMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val verifiedColor = if (report.isSignatureVerified) ForgeEmerald else ForgeRed

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = ForgeSurface,
                border = BorderStroke(1.5.dp, verifiedColor.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (report.isSignatureVerified) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = verifiedColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (report.isSignatureVerified) "Cryptographic Signature Verified" else "Verification Warning",
                                style = MaterialTheme.typography.titleMedium,
                                color = verifiedColor
                            )
                            Text(
                                text = "${report.fileName} • ${formatFileSize(report.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatusPill("v1 JAR", if (report.v1Verified) ForgeEmerald else ForgeAmber)
                        StatusPill("v2 APK", if (report.v2Verified) ForgeEmerald else ForgeAmber)
                        StatusPill("v3 KEY", if (report.v3Verified) ForgeEmerald else ForgeAmber)
                        StatusPill(
                            text = if (report.resourcesAligned4Byte) "4B ALIGNED" else "UNALIGNED",
                            color = if (report.resourcesAligned4Byte) ForgeCyan else ForgeRed
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    ApkExportActionsRow(
                        onSave = onSave,
                        onShare = onShare,
                        onInspect = {
                            clipboard.setText(AnnotatedString(apkPath))
                            onCopyMessage("Copied local APK path")
                        }
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Parsed Binary Manifest",
                icon = Icons.Filled.Description,
                trailingBadge = "AXML"
            ) {
                MetaRow("App Label", report.appLabel)
                MetaRow("Package ID", report.packageName)
                MetaRow("Version", "${report.versionName} (code ${report.versionCode})")
                MetaRow("SDK Targets", "minSdk ${report.minSdkVersion} • targetSdk ${report.targetSdkVersion}")
            }
        }

        item {
            SectionCard(
                title = "Signing Certificate",
                icon = Icons.Filled.Security,
                trailingBadge = "X.509"
            ) {
                Text(
                    text = report.signerSubject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                FingerprintCopyBox(
                    label = "SHA-256",
                    value = report.certSha256,
                    onCopy = {
                        clipboard.setText(AnnotatedString(report.certSha256))
                        onCopyMessage("Copied SHA-256")
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                FingerprintCopyBox(
                    label = "SHA-1",
                    value = report.certSha1,
                    onCopy = {
                        clipboard.setText(AnnotatedString(report.certSha1))
                        onCopyMessage("Copied SHA-1")
                    }
                )
            }
        }

        item {
            val totalUncompressed = report.entryGroups.sumOf { it.totalBytes }.coerceAtLeast(1L)
            SectionCard(
                title = "Archive Payload Breakdown",
                icon = Icons.Filled.FolderZip,
                trailingBadge = "${report.entryGroups.sumOf { it.entryCount }} files"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.entryGroups.forEach { group ->
                        val fraction = (group.totalBytes.toFloat() / totalUncompressed.toFloat()).coerceIn(0.04f, 1f)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${group.category} (${group.entryCount})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatFileSize(group.totalBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ForgeCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ForgeSurfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ForgeEmerald)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeTextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FingerprintCopyBox(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = ForgeSurfaceVariant,
        border = BorderStroke(1.dp, ForgeBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ForgeTextSecondary)
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = ForgeCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
