package com.example.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Deflater

data class ApkZipInputEntry(
    val name: String,
    val bytes: ByteArray,
    val storeUncompressed: Boolean
)

/**
 * Pure-Kotlin ZIP writer that enforces 4-byte alignment on STORED entries (such as resources.arsc
 * and uncompressed PNGs) so generated APKs satisfy Android 11+ PackageManager alignment rules.
 */
object ZipAligner {
    private const val LOCAL_HEADER_SIG = 0x04034b50
    private const val CENTRAL_HEADER_SIG = 0x02014b50
    private const val EOCD_SIG = 0x06054b50

    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8

    private data class CentralRecord(
        val nameBytes: ByteArray,
        val method: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long
    )

    fun writeAlignedZip(entries: List<ApkZipInputEntry>, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        val centralRecords = ArrayList<CentralRecord>(entries.size)
        var offset = 0L

        FileOutputStream(outputFile).buffered().use { out ->
            for (entry in entries) {
                val nameBytes = entry.name.toByteArray(StandardCharsets.UTF_8)
                val crc = CRC32().apply { update(entry.bytes) }.value
                val uncompressedSize = entry.bytes.size.toLong()

                val method: Int
                val payload: ByteArray
                val extraBytes: ByteArray

                if (entry.storeUncompressed) {
                    method = METHOD_STORED
                    payload = entry.bytes
                    val rawDataStart = offset + 30L + nameBytes.size
                    val pad = ((4 - (rawDataStart % 4)) % 4).toInt()
                    extraBytes = ByteArray(pad)
                } else {
                    method = METHOD_DEFLATED
                    payload = deflateRaw(entry.bytes)
                    extraBytes = ByteArray(0)
                }

                val compressedSize = payload.size.toLong()
                val localHeader = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(LOCAL_HEADER_SIG)
                    putShort(20) // version needed to extract
                    putShort(0x0800.toShort()) // UTF-8 flag
                    putShort(method.toShort())
                    putShort(0) // mod time
                    putShort(0x4021.toShort()) // fixed deterministic date
                    putInt(crc.toInt())
                    putInt(compressedSize.toInt())
                    putInt(uncompressedSize.toInt())
                    putShort(nameBytes.size.toShort())
                    putShort(extraBytes.size.toShort())
                }.array()

                val localOffset = offset
                out.write(localHeader)
                out.write(nameBytes)
                if (extraBytes.isNotEmpty()) out.write(extraBytes)
                out.write(payload)

                offset += 30L + nameBytes.size + extraBytes.size + compressedSize

                centralRecords.add(
                    CentralRecord(
                        nameBytes = nameBytes,
                        method = method,
                        crc32 = crc,
                        compressedSize = compressedSize,
                        uncompressedSize = uncompressedSize,
                        localHeaderOffset = localOffset
                    )
                )
            }

            val cdStartOffset = offset
            var cdSize = 0L
            for (rec in centralRecords) {
                val cdHeader = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(CENTRAL_HEADER_SIG)
                    putShort(20) // version made by
                    putShort(20) // version needed
                    putShort(0x0800.toShort()) // UTF-8
                    putShort(rec.method.toShort())
                    putShort(0)
                    putShort(0x4021.toShort())
                    putInt(rec.crc32.toInt())
                    putInt(rec.compressedSize.toInt())
                    putInt(rec.uncompressedSize.toInt())
                    putShort(rec.nameBytes.size.toShort())
                    putShort(0) // extra len
                    putShort(0) // comment len
                    putShort(0) // disk number
                    putShort(0) // internal attrs
                    putInt(0) // external attrs
                    putInt(rec.localHeaderOffset.toInt())
                }.array()
                out.write(cdHeader)
                out.write(rec.nameBytes)
                cdSize += 46L + rec.nameBytes.size
            }

            val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(EOCD_SIG)
                putShort(0)
                putShort(0)
                putShort(centralRecords.size.toShort())
                putShort(centralRecords.size.toShort())
                putInt(cdSize.toInt())
                putInt(cdStartOffset.toInt())
                putShort(0)
            }.array()
            out.write(eocd)
            out.flush()
        }
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count > 0) out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }
}
