package emu.cache.container

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Headerless-bzip2 and deterministic-gzip codecs for Jagex container payloads. */
internal object Js5Compressor {
    /** The 4-byte bzip2 stream magic (`BZh1`, 100 KB block size) that Jagex's payload omits. */
    private val BZIP2_MAGIC = byteArrayOf('B'.code.toByte(), 'Z'.code.toByte(), 'h'.code.toByte(), '1'.code.toByte())

    /** Compresses [data] with bzip2 (block size 1) and strips the leading `BZh1` magic. */
    fun bzip2(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BZip2CompressorOutputStream(out, 1).use { it.write(data) }
        val compressed = out.toByteArray()
        check(compressed.copyOfRange(0, 4).contentEquals(BZIP2_MAGIC)) { "Unexpected bzip2 header" }
        return compressed.copyOfRange(4, compressed.size)
    }

    /** Re-prepends the `BZh1` magic Jagex strips, then inflates. */
    fun bunzip2(headerlessPayload: ByteArray): ByteArray {
        val withHeader = BZIP2_MAGIC + headerlessPayload
        val out = ByteArrayOutputStream()
        BZip2CompressorInputStream(ByteArrayInputStream(withHeader)).use { it.copyTo(out) }
        return out.toByteArray()
    }

    /** Compresses [data] with gzip using a fixed (zeroed) OS byte and mtime for determinism. */
    fun gzip(data: ByteArray): ByteArray {
        val params = GzipParameters().apply {
            operatingSystem = 0
            modificationTime = 0
        }
        val out = ByteArrayOutputStream()
        GzipCompressorOutputStream(out, params).use { it.write(data) }
        return out.toByteArray()
    }

    fun gunzip(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GzipCompressorInputStream(ByteArrayInputStream(payload)).use { it.copyTo(out) }
        return out.toByteArray()
    }
}
