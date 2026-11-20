package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.SigningKeyEntity
import com.example.data.model.BuildPipelineState
import com.example.data.model.ProjectDraft
import com.example.data.model.SourceType
import com.example.data.model.StarterTemplates
import com.example.ui.components.ApkExportActionsRow
import com.example.ui.components.AppIconBadgePreview
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.parseSafeColor
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBackground
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeOnEmerald
import com.example.ui.theme.ForgeRed
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeSurfaceVariant
import com.example.ui.theme.ForgeTextSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.StudioViewModel

private val themeSwatches = listOf("#0B0F14", "#0F172A", "#14121F", "#0D1B1E", "#1A1311")
private val accentSwatches = listOf("#00E5A3", "#38BDF8", "#F59E0B", "#A78BFA", "#FB7185")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    draft: ProjectDraft,
    pipelineState: BuildPipelineState,
    signingKeys: List<SigningKeyEntity>,
    selectedTemplateId: String,
    viewModel: StudioViewModel,
    onSaveApk: (apkPath: String, fileName: String) -> Unit,
    onShareApk: (apkPath: String) -> Unit,
    onInspectApk: (apkPath: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPreviewSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.updateCustomIconUri(uri.toString())
        }
    }

    val packageValid = viewModel.isPackageValid(draft.packageName)
    val urlValid = draft.sourceType != SourceType.URL || viewModel.isUrlValid(draft.targetUrl)
    val canBuild = viewModel.canBuild(draft) && pipelineState !is BuildPipelineState.Running

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudioHeader(
                    onPreviewClick = { showPreviewSheet = true },
                    canPreview = urlValid
                )
            }

            // Pipeline Status / Console Card when active or completed
            if (pipelineState !is BuildPipelineState.Idle) {
                item {
                    BuildPipelineCard(
                        state = pipelineState,
                        onSave = { path, name -> onSaveApk(path, name) },
                        onShare = { path -> onShareApk(path) },
                        onInspect = { path -> onInspectApk(path) },
                        onDismiss = { viewModel.resetPipeline() }
                    )
                }
            }

            // 1. Source Payload Section
            item {
                SectionCard(
                    title = "1. App Payload",
                    icon = Icons.Filled.Code,
                    trailingBadge = draft.sourceType.label
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SourceType.entries.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = draft.sourceType == type,
                                onClick = { viewModel.updateSourceType(type) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SourceType.entries.size
                                ),
                                modifier = Modifier.testTag("source_tab_${type.name.lowercase()}")
                            ) {
                                Text(
                                    text = type.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (draft.sourceType) {
                        SourceType.URL -> {
                            OutlinedTextField(
                                value = draft.targetUrl,
                                onValueChange = viewModel::updateTargetUrl,
                                label = { Text("Website URL") },
                                placeholder = { Text("https://example.com") },
                                leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                                isError = !urlValid,
                                supportingText = {
                                    if (!urlValid) {
                                        Text("Include http:// or https://")
                                    } else {
                                        Text("Wrapped in a native WebView shell with offline error fallback")
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("url_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Android Docs" to "https://developer.android.com",
                                    "Wikipedia" to "https://en.m.wikipedia.org",
                                    "GitHub" to "https://github.com"
                                ).forEach { (label, presetUrl) ->
                                    FilterChip(
                                        selected = draft.targetUrl == presetUrl,
                                        onClick = { viewModel.updateTargetUrl(presetUrl) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        SourceType.TEMPLATE -> {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(StarterTemplates.all, key = { it.id }) { tpl ->
                                    val isSelected = tpl.id == selectedTemplateId
                                    Surface(
                                        modifier = Modifier
                                            .width(210.dp)
                                            .clickable { viewModel.applyTemplate(tpl) }
                                            .testTag("template_card_${tpl.id}"),
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (isSelected) ForgeEmerald.copy(alpha = 0.12f) else ForgeSurfaceVariant,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) ForgeEmerald else ForgeBorder
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                StatusPill(
                                                    text = tpl.category,
                                                    color = if (isSelected) ForgeEmerald else ForgeCyan
                                                )
                                                Icon(
                                                    imageVector = Icons.Filled.Widgets,
                                                    contentDescription = null,
                                                    tint = if (isSelected) ForgeEmerald else ForgeTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = tpl.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = tpl.subtitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = ForgeTextSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SourceType.HTML_BUNDLE -> {
                            OutlinedTextField(
                                value = draft.htmlContent,
                                onValueChange = viewModel::updateHtmlContent,
                                label = { Text("index.html (HTML + CSS + JS)") },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                minLines = 6,
                                maxLines = 10,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("html_code_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${draft.htmlContent.length} chars • Offline asset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeTextSecondary
                                )
                                TextButton(onClick = { showPreviewSheet = true }) {
                                    Text("Test in WebView")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Identity & Icon Section
            item {
                SectionCard(
                    title = "2. App Identity & Icon",
                    icon = Icons.Filled.Image,
                    trailingBadge = "AXML"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AppIconBadgePreview(
                            customIconUri = draft.customIconUri,
                            badgeLabel = draft.badgeLabel,
                            bgHex = draft.themeColorHex,
                            accentHex = draft.accentColorHex,
                            size = 68.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("pick_icon_button"),
                                    border = BorderStroke(1.dp, ForgeBorder)
                                ) {
                                    Text("Pick Image")
                                }
                                if (draft.customIconUri != null) {
                                    TextButton(
                                        onClick = { viewModel.updateCustomIconUri(null) },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Use Badge")
                                    }
                                }
                            }
                            Text(
                                text = "Exports 5 PNG mipmaps (mdpi–xxxhdpi + round)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForgeTextSecondary
                            )
                        }
                    }

                    if (draft.customIconUri == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = draft.badgeLabel,
                                onValueChange = viewModel::updateBadgeLabel,
                                label = { Text("Monogram") },
                                singleLine = true,
                                modifier = Modifier.width(104.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Badge Accent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    accentSwatches.forEach { hex ->
                                        val selected = draft.accentColorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(parseSafeColor(hex, ForgeEmerald))
                                                .border(
                                                    width = if (selected) 2.5.dp else 1.dp,
                                                    color = if (selected) Color.White else ForgeBorder,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.updateAccentColorHex(hex) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = draft.appName,
                        onValueChange = viewModel::updateAppName,
                        label = { Text("App Name (Launcher Label)") },
                        singleLine = true,
                        isError = draft.appName.trim().isEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_name_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = draft.packageName,
                        onValueChange = viewModel::updatePackageName,
                        label = { Text("Package ID (applicationId)") },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        isError = !packageValid,
                        supportingText = {
                            Text(
                                text = if (packageValid) "Valid Android package identifier"
                                else "Use reverse-domain syntax (e.g. com.studio.myapp)"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("package_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = draft.versionName,
                            onValueChange = viewModel::updateVersionName,
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = ForgeSurfaceVariant,
                            border = BorderStroke(1.dp, ForgeBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.incrementVersionCode(-1) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease version code")
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Code", style = MaterialTheme.typography.labelSmall, color = ForgeTextSecondary)
                                    Text(
                                        text = "${draft.versionCode}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.incrementVersionCode(1) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase version code")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Runtime & Signing Configuration
            item {
                SectionCard(
                    title = "3. Runtime & Signing Key",
                    icon = Icons.Filled.Settings,
                    trailingBadge = "v1+v2+v3"
                ) {
                    Text(
                        text = "Status Bar & Window Canvas",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        themeSwatches.forEach { hex ->
                            val selected = draft.themeColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parseSafeColor(hex, ForgeBackground))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) ForgeEmerald else ForgeBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.updateThemeColorHex(hex) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    RuntimeSwitchRow(
                        title = "JavaScript Engine",
                        subtitle = "Required for interactive web apps & templates",
                        checked = draft.enableJavaScript,
                        onCheckedChange = viewModel::updateEnableJavaScript
                    )
                    RuntimeSwitchRow(
                        title = "DOM LocalStorage",
                        subtitle = "Persist client-side app data across launches",
                        checked = draft.domStorageEnabled,
                        onCheckedChange = viewModel::updateDomStorage
                    )
                    RuntimeSwitchRow(
                        title = "Reload Quick Action",
                        subtitle = "Floating reload button inside packaged WebView",
                        checked = draft.pullToRefresh,
                        onCheckedChange = viewModel::updatePullToRefresh
                    )
                    RuntimeSwitchRow(
                        title = "Immersive Fullscreen",
                        subtitle = "Hide Android status bar while running",
                        checked = draft.fullscreen,
                        onCheckedChange = viewModel::updateFullscreen
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Signing Certificate (RSA-2048 PKCS#12)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(signingKeys, key = { it.id }) { key ->
                            val selected = key.id == draft.selectedKeyId
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectSigningKey(key.id) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(key.alias) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForgeEmerald.copy(alpha = 0.18f),
                                    selectedLabelColor = ForgeEmerald,
                                    selectedLeadingIconColor = ForgeEmerald
                                )
                            )
                        }
                    }
                }
            }

            // Primary Forge Action Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPreviewSheet = true },
                        enabled = urlValid,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("preview_app_button"),
                        border = BorderStroke(1.dp, ForgeBorder)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preview")
                    }

                    Button(
                        onClick = { viewModel.forgeApk() },
                        enabled = canBuild,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("forge_apk_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForgeEmerald,
                            contentColor = ForgeOnEmerald
                        )
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pipelineState is BuildPipelineState.Running) "Forging APK..." else "Forge Signed APK",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }

    if (showPreviewSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPreviewSheet = false },
            sheetState = sheetState,
            containerColor = ForgeSurface
        ) {
            LiveWebPreviewSheet(
                draft = draft,
                onClose = { showPreviewSheet = false }
            )
        }
    }
}

@Composable
private fun StudioHeader(
    onPreviewClick: () -> Unit,
    canPreview: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "APKForge",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(text = "OFFLINE ENGINE", color = ForgeEmerald)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Compile binary manifest, assets, icons & v1–v3 signatures on-device",
                style = MaterialTheme.typography.bodyMedium,
                color = ForgeTextSecondary
            )
        }
        IconButton(
            onClick = onPreviewClick,
            enabled = canPreview,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Preview app in WebView",
                tint = if (canPreview) ForgeCyan else ForgeTextSecondary
            )
        }
    }
}

@Composable
private fun RuntimeSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = ForgeTextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BuildPipelineCard(
    state: BuildPipelineState,
    onSave: (String, String) -> Unit,
    onShare: (String) -> Unit,
    onInspect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = when (state) {
        is BuildPipelineState.Running -> ForgeCyan
        is BuildPipelineState.Succeeded -> ForgeEmerald
        is BuildPipelineState.Failed -> ForgeRed
        BuildPipelineState.Idle -> ForgeBorder
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("build_pipeline_card"),
        shape = MaterialTheme.shapes.large,
        color = ForgeSurface,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (state) {
                            is BuildPipelineState.Running -> Icons.Filled.Terminal
                            is BuildPipelineState.Succeeded -> Icons.Filled.CheckCircle
                            is BuildPipelineState.Failed -> Icons.Filled.ErrorOutline
                            BuildPipelineState.Idle -> Icons.Filled.Terminal
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (state) {
                            is BuildPipelineState.Running -> "Stage ${state.stepIndex}/${state.totalSteps}: ${state.currentStage}"
                            is BuildPipelineState.Succeeded -> "APK Forged & Verified (${state.durationMs}ms)"
                            is BuildPipelineState.Failed -> "Build Failed at ${state.failedStage}"
                            BuildPipelineState.Idle -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )
                }
                if (state !is BuildPipelineState.Running) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss build output")
                    }
                }
            }

            if (state is BuildPipelineState.Running) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.stepIndex.toFloat() / state.totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                    color = ForgeCyan,
                    trackColor = ForgeSurfaceVariant
                )
            }

            val logs = when (state) {
                is BuildPipelineState.Running -> state.logs
                is BuildPipelineState.Succeeded -> state.logs
                is BuildPipelineState.Failed -> state.logs
                BuildPipelineState.Idle -> emptyList()
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = ForgeBackground,
                border = BorderStroke(1.dp, ForgeBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logs.takeLast(7).forEach { entry ->
                        val color = when {
                            entry.isError -> ForgeRed
                            entry.isSuccess -> ForgeEmerald
                            else -> ForgeTextSecondary
                        }
                        Text(
                            text = "[${entry.stage}] ${entry.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state is BuildPipelineState.Succeeded) {
                if (state is BuildPipelineState.Succeeded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.fileName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(
                                text = formatFileSize(state.fileSizeBytes),
                                color = if (state.isVerified) ForgeEmerald else ForgeAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ApkExportActionsRow(
                            onSave = { onSave(state.apkPath, state.fileName) },
                            onShare = { onShare(state.apkPath) },
                            onInspect = { onInspect(state.apkPath) }
                        )
                    }
                }
            }

            if (state is BuildPipelineState.Failed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForgeRed
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveWebPreviewSheet(
    draft: ProjectDraft,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${draft.appName.ifBlank { "App" }} — Live Runtime Preview",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = draft.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = ForgeCyan
                )
            }
            TextButton(onClick = onClose) {
                Text("Done")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 500.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, ForgeBorder),
            color = parseSafeColor(draft.themeColorHex, ForgeBackground)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = draft.enableJavaScript
                        settings.domStorageEnabled = draft.domStorageEnabled
                        webViewClient = WebViewClient()
                        if (draft.sourceType == SourceType.URL) {
                            loadUrl(draft.targetUrl)
                        } else {
                            loadDataWithBaseURL(
                                "https://apkforge.local/",
                                draft.htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                },
                update = { webView ->
                    webView.settings.javaScriptEnabled = draft.enableJavaScript
                    webView.settings.domStorageEnabled = draft.domStorageEnabled
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
