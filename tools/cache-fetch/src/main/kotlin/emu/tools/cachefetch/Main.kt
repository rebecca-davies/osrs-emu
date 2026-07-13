package emu.tools.cachefetch

import io.github.oshai.kotlinlogging.KotlinLogging
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

private val logger = KotlinLogging.logger {}

/**
 * Target OSRS revision — must match the client. The freshly-cloned RuneLite injected-client
 * is rev 239 (verified from its JS5 handshake). Change this one constant to move revisions.
 */
private const val TARGET_BUILD = 239

/**
 * Downloads the newest OpenRS2 live oldschool cache whose build major matches [TARGET_BUILD] as
 * the flat-file dump, extracting `cache/{archive}/{group}.dat` into `./cache-data/`. Falls back to
 * the newest live oldschool build `<= TARGET_BUILD` (with a warning) if no exact match exists.
 */
fun main() {
    val base = "https://archive.openrs2.org"
    val caches = URI("$base/caches.json").toURL().readText()
    val selection = selectCacheId(caches)
        ?: error("No live oldschool cache found in caches.json")
    val id = selection.id
    if (selection.build != TARGET_BUILD) {
        logger.warn {
            "no build-$TARGET_BUILD live oldschool cache found in caches.json; falling back to " +
                "newest live oldschool build <= $TARGET_BUILD (build=${selection.build}, id=$id). " +
                "The served revision may not match the client!"
        }
    }
    logger.info { "selected OpenRS2 cache id=$id" }
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
            logger.info { "extracted $count group files into ${outDir.absolutePath}" }
        }
    }

    // The flat-file dump does not contain the synthesized master index group (255,255) — the
    // JS5 client's very first request at bootstrap. Fetch it directly from the per-group endpoint
    // and write it into the same on-disk layout so the dump is complete for JS5 serving.
    fetchMasterIndex(base, id, outDir)
}

private fun fetchMasterIndex(base: String, id: Int, outDir: File) {
    val masterUrl = URI("$base/caches/runescape/$id/archives/255/groups/255.dat").toURL()
    try {
        val bytes = masterUrl.openStream().use { it.readBytes() }
        val f = File(outDir, "cache/255/255.dat")
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
        logger.info { "fetched master index (255,255): ${bytes.size} bytes" }
    } catch (e: Exception) {
        logger.warn {
            "failed to fetch master index (255,255) from $masterUrl: ${e.message}. The JS5 " +
                "client's first bootstrap request will fail until this group is present."
        }
    }
}

private data class CacheSelection(val id: Int, val build: Int)

private fun selectCacheId(json: String): CacheSelection? {
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

        if (majors.any { it == TARGET_BUILD }) {
            if (best == null || ts > bestTs) {
                best = id
                bestTs = ts
                bestBuild = TARGET_BUILD
            }
        }

        // Fallback candidate: newest live oldschool cache with build major <= TARGET_BUILD.
        val maxMajorLe = majors.filter { it <= TARGET_BUILD }.maxOrNull()
        if (maxMajorLe != null) {
            if (fallbackBest == null || ts > fallbackTs) {
                fallbackBest = id
                fallbackTs = ts
                fallbackBuild = maxMajorLe
            }
        }
    }

    if (best != null) return CacheSelection(best, bestBuild!!)
    if (fallbackBest != null) return CacheSelection(fallbackBest, fallbackBuild!!)
    return null
}
