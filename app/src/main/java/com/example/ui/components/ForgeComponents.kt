package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeSurfaceVariant
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    trailingBadge: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ForgeSurface,
        border = BorderStroke(1.dp, ForgeBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ForgeCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (trailingBadge != null) {
                    StatusPill(text = trailingBadge, color = ForgeCyan)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color = ForgeEmerald,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppIconBadgePreview(
    customIconUri: String?,
    badgeLabel: String,
    bgHex: String,
    accentHex: String,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(initialValue = null, customIconUri) {
        value = if (customIconUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(customIconUri))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
        }
    }

    val bgColor = parseSafeColor(bgHex, Color(0xFF0B0F14))
    val accentColor = parseSafeColor(accentHex, ForgeEmerald)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(bgColor, bgColor.copy(alpha = 0.7f).compositeOver(accentColor, 0.25f))
                )
            )
            .border(1.5.dp, accentColor.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmapState.value
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Selected app launcher icon",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = badgeLabel.trim().uppercase().take(2).ifBlank { "AF" },
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp,
                color = accentColor
            )
        }
    }
}

@Composable
fun ApkExportActionsRow(
    onSave: () -> Unit,
    onShare: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("save_apk_button"),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = ForgeEmerald.copy(alpha = 0.16f),
                contentColor = ForgeEmerald
            )
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save", maxLines = 1)
        }

        OutlinedButton(
            onClick = onShare,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("share_apk_button"),
            border = BorderStroke(1.dp, ForgeBorder)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share", maxLines = 1)
        }

        OutlinedButton(
            onClick = onInspect,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("inspect_apk_button"),
            border = BorderStroke(1.dp, ForgeCyan.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ForgeCyan)
        ) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Inspect", maxLines = 1)
        }
    }
}

fun parseSafeColor(hex: String, fallback: Color): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(fallback)
}

private fun Color.compositeOver(accent: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = red * inv + accent.red * ratio,
        green = green * inv + accent.green * ratio,
        blue = blue * inv + accent.blue * ratio,
        alpha = 1f
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

fun formatTimestamp(ms: Long): String {
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}
