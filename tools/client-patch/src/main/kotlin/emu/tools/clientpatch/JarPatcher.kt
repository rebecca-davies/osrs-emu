package emu.tools.clientpatch

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Byte-patches a copy of a jar: finds an exact ASCII literal inside any zip entry's raw bytes
 * (e.g. a `BigInteger(String, 16)` string constant baked into a `.class` file's constant pool)
 * and replaces it in place with a same-length replacement, so no offsets shift and nothing else
 * in the class file needs to change.
 */
object JarPatcher {

    /**
     * Copies [inputJar] to [outputJar] entry-by-entry, replacing every occurrence of [oldAscii]
     * with [newAscii] (which MUST be the same length) inside each entry's raw bytes.
     *
     * @return the names of the entries that were actually patched (normally exactly one .class).
     */
    fun patchAsciiLiteral(
        inputJar: File,
        outputJar: File,
        oldAscii: String,
        newAscii: String,
    ): List<String> {
        require(oldAscii.length == newAscii.length) {
            "replacement must be the same length as the original " +
                "(${oldAscii.length} != ${newAscii.length}) to avoid shifting byte offsets"
        }
        val oldBytes = oldAscii.toByteArray(Charsets.US_ASCII)
        val newBytes = newAscii.toByteArray(Charsets.US_ASCII)
        val patchedEntries = mutableListOf<String>()

        outputJar.parentFile?.mkdirs()
        ZipFile(inputJar).use { zip ->
            ZipOutputStream(outputJar.outputStream().buffered()).use { zos ->
                for (entry in zip.entries().toList()) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val outBytes = if (!entry.isDirectory) {
                        val replaced = replaceAll(bytes, oldBytes, newBytes)
                        if (replaced != null) patchedEntries += entry.name
                        replaced ?: bytes
                    } else {
                        bytes
                    }
                    // A fresh ZipEntry (name only) lets ZipOutputStream recompute size/CRC/method
                    // rather than reusing the original entry's (now possibly stale) metadata.
                    zos.putNextEntry(ZipEntry(entry.name))
                    zos.write(outBytes)
                    zos.closeEntry()
                }
            }
        }
        return patchedEntries
    }

    /** Replaces every occurrence of [pattern] with [replacement] in [data]; null if not found. */
    private fun replaceAll(data: ByteArray, pattern: ByteArray, replacement: ByteArray): ByteArray? {
        var idx = indexOf(data, pattern, 0)
        if (idx < 0) return null
        while (idx >= 0) {
            replacement.copyInto(data, idx)
            idx = indexOf(data, pattern, idx + pattern.size)
        }
        return data
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        if (pattern.isEmpty() || from < 0) return -1
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
