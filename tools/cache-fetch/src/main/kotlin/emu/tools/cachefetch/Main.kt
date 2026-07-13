package emu.tools.cachefetch

import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

// Downloads the newest OpenRS2 live oldschool cache whose build major == 235,
// as the flat-file dump, extracting cache/{archive}/{group}.dat into ./cache-data/.
fun main() {
    val base = "https://archive.openrs2.org"
    val caches = URI("$base/caches.json").toURL().readText()
    // Minimal JSON scan: find the newest id with game oldschool, env live, build major 235.
    // caches.json is a flat array; we parse with kotlinx-serialization-free manual approach is
    // brittle — use a real JSON lib instead.
    val selection = selectBuild235Id(caches)
        ?: error("No live oldschool cache found in caches.json")
    val id = selection.id
    if (selection.build != 235) {
        println(
            "WARNING: no build-235 live oldschool cache found in caches.json; " +
                "falling back to newest live oldschool build <= 235 (build=${selection.build}, id=$id). " +
                "The served revision may not match the client!"
        )
    }
    println("Selected OpenRS2 cache id=$id")
    val outDir = File("cache-data")
    outDir.mkdirs()
    val url = URI("$base/caches/runescape/$id/flat-file.tar.gz").toURL()
    url.openStream().use { raw ->
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(raw))).use { tar ->
            var entry = tar.nextEntry as? TarArchiveEntry
            var count = 0
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".dat")) {
                    val f = File(outDir, entry.name)
                    f.parentFile.mkdirs()
                    f.outputStream().use { tar.copyTo(it) }
                    count++
                }
                entry = tar.nextEntry as? TarArchiveEntry
            }
            println("Extracted $count group files into ${outDir.absolutePath}")
        }
    }
}

private data class Build235Selection(val id: Int, val build: Int)

private fun selectBuild235Id(json: String): Build235Selection? {
    val arr = Json.parseToJsonElement(json).jsonArray
    var best: Int? = null
    var bestTs: String = ""
    var bestBuild: Int? = null

    var fallbackBest: Int? = null
    var fallbackTs: String = ""
    var fallbackBuild: Int? = null

    for (el in arr) {
        val o = el.jsonObject
        if (o["game"]?.jsonPrimitive?.contentOrNull != "oldschool") continue
        if (o["environment"]?.jsonPrimitive?.contentOrNull != "live") continue
        val builds = o["builds"]?.jsonArray ?: continue
        val majors = builds.mapNotNull { it.jsonObject["major"]?.jsonPrimitive?.intOrNull }
        val ts = o["timestamp"]?.jsonPrimitive?.contentOrNull ?: ""
        val id = o["id"]?.jsonPrimitive?.intOrNull ?: continue

        if (majors.any { it == 235 }) {
            if (best == null || ts > bestTs) {
                best = id
                bestTs = ts
                bestBuild = 235
            }
        }

        // Fallback candidate: newest live oldschool cache with build major <= 235.
        val maxMajorLe235 = majors.filter { it <= 235 }.maxOrNull()
        if (maxMajorLe235 != null) {
            if (fallbackBest == null || ts > fallbackTs) {
                fallbackBest = id
                fallbackTs = ts
                fallbackBuild = maxMajorLe235
            }
        }
    }

    if (best != null) return Build235Selection(best, bestBuild!!)
    if (fallbackBest != null) return Build235Selection(fallbackBest, fallbackBuild!!)
    return null
}
