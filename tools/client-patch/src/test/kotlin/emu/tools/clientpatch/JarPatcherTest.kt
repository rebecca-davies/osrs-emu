package emu.tools.clientpatch

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JarPatcherTest {
    private val oldLiteral = "c4cc48b4f69a6215" // stand-in fixed-length ascii literal
    private val newLiteral = "1111111111111111" // same length replacement

    @Test fun `replaces a same-length ascii literal inside a class entry and leaves other entries untouched`() {
        val input = File.createTempFile("fixture", ".jar")
        val output = File.createTempFile("patched", ".jar")
        try {
            ZipOutputStream(input.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("bg.class"))
                zos.write("prefix-$oldLiteral-suffix".toByteArray(Charsets.US_ASCII))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("Other.class"))
                zos.write("unrelated bytes".toByteArray(Charsets.US_ASCII))
                zos.closeEntry()
            }

            val patched = JarPatcher.patchAsciiLiteral(input, output, oldLiteral, newLiteral)

            assertEquals(listOf("bg.class"), patched)

            ZipFile(output).use { zip ->
                val bgBytes = zip.getInputStream(zip.getEntry("bg.class")).readBytes()
                val bgText = String(bgBytes, Charsets.US_ASCII)
                assertTrue(bgText.contains(newLiteral))
                assertFalse(bgText.contains(oldLiteral))

                val otherBytes = zip.getInputStream(zip.getEntry("Other.class")).readBytes()
                assertEquals("unrelated bytes", String(otherBytes, Charsets.US_ASCII))
            }
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test fun `rejects a replacement of a different length`() {
        val input = File.createTempFile("fixture", ".jar")
        val output = File.createTempFile("patched", ".jar")
        try {
            ZipOutputStream(input.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("bg.class"))
                zos.write(oldLiteral.toByteArray(Charsets.US_ASCII))
                zos.closeEntry()
            }

            var threw = false
            try {
                JarPatcher.patchAsciiLiteral(input, output, oldLiteral, "short")
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            input.delete()
            output.delete()
        }
    }
}
