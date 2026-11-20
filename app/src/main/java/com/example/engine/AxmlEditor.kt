package com.example.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class ManifestSummary(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Int,
    val minSdkVersion: Int,
    val targetSdkVersion: Int
)

/**
 * Pure-Kotlin parser and patcher for Android Binary XML (AXML) AndroidManifest.xml files.
 * Preserves the resource map and existing string indices while appending updated attribute strings
 * and updating numeric/string attributes directly in RES_XML_START_ELEMENT chunks.
 */
object AxmlEditor {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102

    private const val UTF8_FLAG = 0x00000100
    private const val SORTED_FLAG = 0x00000001

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10

    private const val ATTR_ID_LABEL = 0x01010001
    private const val ATTR_ID_VERSION_CODE = 0x0101021b
    private const val ATTR_ID_VERSION_NAME = 0x0101021c
    private const val ATTR_ID_MIN_SDK = 0x0101020c
    private const val ATTR_ID_TARGET_SDK = 0x01010270

    fun patchManifest(
        axmlBytes: ByteArray,
        newPackageName: String,
        newAppName: String,
        newVersionCode: Int,
        newVersionName: String
    ): ByteArray {
        val buf = ByteBuffer.wrap(axmlBytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        require(fileType == RES_XML_TYPE) { "Invalid AXML header type: 0x${fileType.toString(16)}" }

        val fileHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        var offset = fileHeaderSize

        // 1. Parse String Pool
        val spOffset = offset
        val spType = buf.getShort(spOffset).toInt() and 0xFFFF
        require(spType == RES_STRING_POOL_TYPE) { "Expected string pool chunk at offset $spOffset" }
        val spHeaderSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val styleCount = buf.getInt(spOffset + 12)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val stylesStart = buf.getInt(spOffset + 24)
        val isUtf8 = (flags and UTF8_FLAG) != 0

        val stringOffsets = IntArray(stringCount) { i ->
            buf.getInt(spOffset + spHeaderSize + i * 4)
        }
        val styleOffsets = IntArray(styleCount) { i ->
            buf.getInt(spOffset + spHeaderSize + stringCount * 4 + i * 4)
        }

        val strings = MutableList(stringCount) { i ->
            readPoolString(axmlBytes, spOffset + stringsStart + stringOffsets[i], isUtf8)
        }

        val originalStylesBytes = if (styleCount > 0 && stylesStart > 0) {
            axmlBytes.copyOfRange(spOffset + stylesStart, spOffset + spChunkSize)
        } else {
            ByteArray(0)
        }

        offset += spChunkSize

        // 2. Parse remaining chunks (Resource Map + XML tree nodes)
        var resourceIds = IntArray(0)
        val tailChunksStart = offset
        val tailBytes = axmlBytes.copyOfRange(tailChunksStart, axmlBytes.size)
        val tailBuf = ByteBuffer.wrap(tailBytes).order(ByteOrder.LITTLE_ENDIAN)

        fun appendString(value: String): Int {
            val idx = strings.size
            strings.add(value)
            return idx
        }

        val pkgIdx = appendString(newPackageName)
        val labelIdx = appendString(newAppName)
        val verNameIdx = appendString(newVersionName)

        var pos = 0
        while (pos + 8 <= tailBytes.size) {
            val chunkType = tailBuf.getShort(pos).toInt() and 0xFFFF
            val chunkHeaderSize = tailBuf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = tailBuf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > tailBytes.size) break

            if (chunkType == RES_XML_RESOURCE_MAP_TYPE) {
                val count = (chunkSize - chunkHeaderSize) / 4
                resourceIds = IntArray(count) { i ->
                    tailBuf.getInt(pos + chunkHeaderSize + i * 4)
                }
            } else if (chunkType == RES_XML_START_ELEMENT_TYPE) {
                val elemNameIdx = tailBuf.getInt(pos + 20)
                val elemName = strings.getOrNull(elemNameIdx)
                val attrStart = tailBuf.getShort(pos + 24).toInt() and 0xFFFF
                val attrSize = tailBuf.getShort(pos + 26).toInt() and 0xFFFF
                val attrCount = tailBuf.getShort(pos + 28).toInt() and 0xFFFF

                val attrsBase = pos + chunkHeaderSize + attrStart
                for (a in 0 until attrCount) {
                    val attrOff = attrsBase + a * attrSize
                    if (attrOff + 20 > pos + chunkSize) break

                    val attrNameIdx = tailBuf.getInt(attrOff + 4)
                    val attrName = strings.getOrNull(attrNameIdx)
                    val resId = if (attrNameIdx in resourceIds.indices) resourceIds[attrNameIdx] else 0

                    when {
                        elemName == "manifest" && attrName == "package" -> {
                            tailBuf.putInt(attrOff + 8, pkgIdx)
                            tailBuf.put(attrOff + 15, TYPE_STRING.toByte())
                            tailBuf.putInt(attrOff + 16, pkgIdx)
                        }
                        elemName == "manifest" && (resId == ATTR_ID_VERSION_CODE || attrName == "versionCode") -> {
                            tailBuf.putInt(attrOff + 8, -1)
                            tailBuf.put(attrOff + 15, TYPE_INT_DEC.toByte())
                            tailBuf.putInt(attrOff + 16, newVersionCode)
                        }
                        elemName == "manifest" && (resId == ATTR_ID_VERSION_NAME || attrName == "versionName") -> {
                            tailBuf.putInt(attrOff + 8, verNameIdx)
                            tailBuf.put(attrOff + 15, TYPE_STRING.toByte())
                            tailBuf.putInt(attrOff + 16, verNameIdx)
                        }
                        (elemName == "application" || elemName == "activity") &&
                            (resId == ATTR_ID_LABEL || attrName == "label") -> {
                            tailBuf.putInt(attrOff + 8, labelIdx)
                            tailBuf.put(attrOff + 15, TYPE_STRING.toByte())
                            tailBuf.putInt(attrOff + 16, labelIdx)
                        }
                    }
                }
            }
            pos += chunkSize
        }

        // 3. Rebuild String Pool + AXML file
        val newFlags = flags and SORTED_FLAG.inv()
        val rebuiltStringPool = buildStringPoolChunk(
            strings = strings,
            styleOffsets = styleOffsets,
            stylesBytes = originalStylesBytes,
            flags = newFlags,
            isUtf8 = isUtf8
        )

        val totalSize = fileHeaderSize + rebuiltStringPool.size + tailBytes.size
        val out = ByteArrayOutputStream(totalSize)
        val header = ByteBuffer.allocate(fileHeaderSize).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(RES_XML_TYPE.toShort())
        header.putShort(fileHeaderSize.toShort())
        header.putInt(totalSize)
        out.write(header.array())
        out.write(rebuiltStringPool)
        out.write(tailBytes)
        return out.toByteArray()
    }

    fun parseManifestSummary(axmlBytes: ByteArray): ManifestSummary {
        val buf = ByteBuffer.wrap(axmlBytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        val spOffset = fileHeaderSize
        val spHeaderSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val isUtf8 = (flags and UTF8_FLAG) != 0

        val stringOffsets = IntArray(stringCount) { i ->
            buf.getInt(spOffset + spHeaderSize + i * 4)
        }
        val strings = List(stringCount) { i ->
            readPoolString(axmlBytes, spOffset + stringsStart + stringOffsets[i], isUtf8)
        }

        var packageName = "unknown"
        var appLabel = "unknown"
        var versionName = "1.0"
        var versionCode = 1
        var minSdk = 24
        var targetSdk = 35
        var resourceIds = IntArray(0)

        var pos = spOffset + spChunkSize
        while (pos + 8 <= axmlBytes.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkHeaderSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > axmlBytes.size) break

            if (chunkType == RES_XML_RESOURCE_MAP_TYPE) {
                val count = (chunkSize - chunkHeaderSize) / 4
                resourceIds = IntArray(count) { i ->
                    buf.getInt(pos + chunkHeaderSize + i * 4)
                }
            } else if (chunkType == RES_XML_START_ELEMENT_TYPE) {
                val elemName = strings.getOrNull(buf.getInt(pos + 20))
                val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                val attrsBase = pos + chunkHeaderSize + attrStart

                for (a in 0 until attrCount) {
                    val attrOff = attrsBase + a * attrSize
                    if (attrOff + 20 > pos + chunkSize) break
                    val attrNameIdx = buf.getInt(attrOff + 4)
                    val attrName = strings.getOrNull(attrNameIdx)
                    val resId = if (attrNameIdx in resourceIds.indices) resourceIds[attrNameIdx] else 0
                    val dataType = buf.get(attrOff + 15).toInt() and 0xFF
                    val data = buf.getInt(attrOff + 16)

                    when {
                        elemName == "manifest" && attrName == "package" && dataType == TYPE_STRING -> {
                            packageName = strings.getOrElse(data) { packageName }
                        }
                        elemName == "manifest" && (resId == ATTR_ID_VERSION_CODE || attrName == "versionCode") -> {
                            versionCode = data
                        }
                        elemName == "manifest" && (resId == ATTR_ID_VERSION_NAME || attrName == "versionName") && dataType == TYPE_STRING -> {
                            versionName = strings.getOrElse(data) { versionName }
                        }
                        elemName == "uses-sdk" && (resId == ATTR_ID_MIN_SDK || attrName == "minSdkVersion") -> {
                            minSdk = data
                        }
                        elemName == "uses-sdk" && (resId == ATTR_ID_TARGET_SDK || attrName == "targetSdkVersion") -> {
                            targetSdk = data
                        }
                        elemName == "application" && (resId == ATTR_ID_LABEL || attrName == "label") && dataType == TYPE_STRING -> {
                            appLabel = strings.getOrElse(data) { appLabel }
                        }
                    }
                }
            }
            pos += chunkSize
        }

        return ManifestSummary(
            packageName = packageName,
            appLabel = appLabel,
            versionName = versionName,
            versionCode = versionCode,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk
        )
    }

    private fun readPoolString(bytes: ByteArray, offset: Int, isUtf8: Boolean): String {
        if (isUtf8) {
            var p = offset
            val firstCharLen = bytes[p++].toInt() and 0xFF
            if ((firstCharLen and 0x80) != 0) p++
            var byteLen = bytes[p++].toInt() and 0xFF
            if ((byteLen and 0x80) != 0) {
                val low = bytes[p++].toInt() and 0xFF
                byteLen = ((byteLen and 0x7F) shl 8) or low
            }
            return String(bytes, p, byteLen, StandardCharsets.UTF_8)
        } else {
            var p = offset
            val lo = bytes[p++].toInt() and 0xFF
            val hi = bytes[p++].toInt() and 0xFF
            var charLen = (hi shl 8) or lo
            if ((charLen and 0x8000) != 0) {
                val lo2 = bytes[p++].toInt() and 0xFF
                val hi2 = bytes[p++].toInt() and 0xFF
                charLen = ((charLen and 0x7FFF) shl 16) or ((hi2 shl 8) or lo2)
            }
            return String(bytes, p, charLen * 2, StandardCharsets.UTF_16LE)
        }
    }

    private fun buildStringPoolChunk(
        strings: List<String>,
        styleOffsets: IntArray,
        stylesBytes: ByteArray,
        flags: Int,
        isUtf8: Boolean
    ): ByteArray {
        val stringDataOut = ByteArrayOutputStream()
        val newStringOffsets = IntArray(strings.size)

        for (i in strings.indices) {
            newStringOffsets[i] = stringDataOut.size()
            val s = strings[i]
            if (isUtf8) {
                val utf8 = s.toByteArray(StandardCharsets.UTF_8)
                writeUtf8Length(stringDataOut, s.length)
                writeUtf8Length(stringDataOut, utf8.size)
                stringDataOut.write(utf8)
                stringDataOut.write(0)
            } else {
                val utf16 = s.toByteArray(StandardCharsets.UTF_16LE)
                writeUtf16Length(stringDataOut, s.length)
                stringDataOut.write(utf16)
                stringDataOut.write(0)
                stringDataOut.write(0)
            }
        }

        while (stringDataOut.size() % 4 != 0) {
            stringDataOut.write(0)
        }

        val headerSize = 0x1c
        val offsetsSize = (strings.size + styleOffsets.size) * 4
        val stringsStart = headerSize + offsetsSize
        val stringDataBytes = stringDataOut.toByteArray()
        val stylesStart = if (styleOffsets.isNotEmpty() && stylesBytes.isNotEmpty()) {
            stringsStart + stringDataBytes.size
        } else {
            0
        }

        val rawChunkSize = stringsStart + stringDataBytes.size + stylesBytes.size
        val padding = (4 - (rawChunkSize % 4)) % 4
        val chunkSize = rawChunkSize + padding

        val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_STRING_POOL_TYPE.toShort())
        buf.putShort(headerSize.toShort())
        buf.putInt(chunkSize)
        buf.putInt(strings.size)
        buf.putInt(styleOffsets.size)
        buf.putInt(flags)
        buf.putInt(stringsStart)
        buf.putInt(stylesStart)
        newStringOffsets.forEach { buf.putInt(it) }
        styleOffsets.forEach { buf.putInt(it) }
        buf.put(stringDataBytes)
        buf.put(stylesBytes)
        repeat(padding) { buf.put(0) }
        return buf.array()
    }

    private fun writeUtf8Length(out: ByteArrayOutputStream, len: Int) {
        if (len > 0x7F) {
            out.write(((len ushr 8) or 0x80) and 0xFF)
        }
        out.write(len and 0xFF)
    }

    private fun writeUtf16Length(out: ByteArrayOutputStream, len: Int) {
        if (len > 0x7FFF) {
            val hi = ((len ushr 16) or 0x8000)
            out.write(hi and 0xFF)
            out.write((hi ushr 8) and 0xFF)
        }
        out.write(len and 0xFF)
        out.write((len ushr 8) and 0xFF)
    }
}
