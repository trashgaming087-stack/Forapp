package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.min

data class MipmapIconSet(
    val squareByDensity: Map<String, ByteArray>,
    val roundByDensity: Map<String, ByteArray>
)

object IconRenderer {
    private val densities = mapOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192
    )

    fun renderIconSet(
        context: Context,
        customIconUri: String?,
        badgeLabel: String,
        bgHex: String,
        accentHex: String
    ): MipmapIconSet {
        val masterBitmap = loadMasterBitmap(context, customIconUri)
            ?: renderMonogramBitmap(512, badgeLabel, bgHex, accentHex)

        val squareMap = mutableMapOf<String, ByteArray>()
        val roundMap = mutableMapOf<String, ByteArray>()

        for ((density, px) in densities) {
            val square = Bitmap.createScaledBitmap(masterBitmap, px, px, true)
            val round = maskCircle(square)
            squareMap[density] = toPng(square)
            roundMap[density] = toPng(round)
            if (square !== masterBitmap) square.recycle()
            round.recycle()
        }
        masterBitmap.recycle()

        return MipmapIconSet(squareByDensity = squareMap, roundByDensity = roundMap)
    }

    private fun loadMasterBitmap(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return@use null
                val size = min(raw.width, raw.height)
                val x = (raw.width - size) / 2
                val y = (raw.height - size) / 2
                val cropped = Bitmap.createBitmap(raw, x, y, size, size)
                if (cropped !== raw) raw.recycle()
                val scaled = Bitmap.createScaledBitmap(cropped, 512, 512, true)
                if (scaled !== cropped) cropped.recycle()
                scaled
            }
        }.getOrNull()
    }

    private fun renderMonogramBitmap(
        size: Int,
        label: String,
        bgHex: String,
        accentHex: String
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = runCatching { Color.parseColor(bgHex) }.getOrDefault(0xFF0B0F14.toInt())
        val accentColor = runCatching { Color.parseColor(accentHex) }.getOrDefault(0xFF00E5A3.toInt())

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                bgColor,
                blendWithAccent(bgColor, accentColor, 0.25f),
                Shader.TileMode.CLAMP
            )
        }
        val corner = size * 0.22f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), corner, corner, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
            color = accentColor
            alpha = 210
        }
        val inset = size * 0.08f
        canvas.drawRoundRect(
            RectF(inset, inset, size - inset, size - inset),
            corner * 0.78f,
            corner * 0.78f,
            borderPaint
        )

        val cleanText = label.trim().uppercase().take(2).ifBlank { "AF" }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = size * 0.38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(cleanText, 0, cleanText.length, bounds)
        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(cleanText, size / 2f, y, textPaint)

        return bmp
    }

    private fun maskCircle(source: Bitmap): Bitmap {
        val size = source.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        }
        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun toPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun blendWithAccent(base: Int, accent: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(base) * inv + Color.red(accent) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * inv + Color.green(accent) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * inv + Color.blue(accent) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
