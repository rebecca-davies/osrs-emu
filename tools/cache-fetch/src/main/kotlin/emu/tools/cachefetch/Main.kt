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

    fetchMasterIndex(base, id, outDir)
    fetchMapKeys(base, caches, outDir)
}

/**
 * Map-region XTEA keys for loc (`l`) groups. Live build-$TARGET_BUILD OSRS caches ship **zero**
 * map keys (the community corpus lags a few builds), so scenery loc groups in the served cache stay
 * encrypted. This fetches `keys.json` from the newest live oldschool cache with build `<=
 * TARGET_BUILD` that actually has keys (build 236, id 2499 at time of writing — keys are
 * revision-stable for old content like Lumbridge; see
 * docs/superpowers/research/2026-07-14-map-xtea-keys.md), plus the Lumbridge `l50_50` container so
 * the key can be validated by trial-decrypt ([emu.cache.container.MapXteaKeys]). These are **not**
 * injected into any rev-239 game packet — the decompiled client's REBUILD_NORMAL carries no keys;
 * they exist for server-side loc validation/decode.
 */
private fun fetchMapKeys(base: String, cachesJson: String, outDir: File) {
    val candidates = keyedCacheCandidates(cachesJson)
    for (candidateId in candidates) {
        val keysUrl = URI("$base/caches/runescape/$candidateId/keys.json").toURL()
        val keysText = try {
            keysUrl.openStream().use { it.readBytes() }.decodeToString()
        } catch (e: Exception) {
            logger.warn { "failed to fetch keys.json for cache id=$candidateId: ${e.message}" }
            continue
        }
        val entries = Json.parseToJsonElement(keysText).jsonArray
        if (entries.isEmpty()) continue // this cache has no map keys; try the next-oldest

        val xteaDir = File(outDir, "xtea").apply { mkdirs() }
        File(xteaDir, "keys.json").writeText(keysText)
        logger.info { "fetched map XTEA keys.json (${entries.size} entries) from cache id=$candidateId" }

        // Vendor the Lumbridge l50_50 (mapsquare 12850) loc container for trial-decrypt validation.
        val lumbridge = entries.map { it.jsonObject }.firstOrNull {
            it["mapsquare"]?.jsonPrimitive?.intOrNull == LUMBRIDGE_MAPSQUARE
        }
        val group = lumbridge?.get("group")?.jsonPrimitive?.intOrNull
        if (group != null) {
            val locUrl = URI("$base/caches/runescape/$candidateId/archives/5/groups/$group.dat").toURL()
            try {
                val bytes = locUrl.openStream().use { it.readBytes() }
                File(xteaDir, "l50_50.dat").writeBytes(bytes)
                logger.info { "fetched Lumbridge l50_50 loc container (group $group): ${bytes.size} bytes" }
            } catch (e: Exception) {
                logger.warn { "failed to fetch l50_50 container (group $group): ${e.message}" }
            }
        }
        return
    }
    logger.warn { "no live oldschool cache <= build $TARGET_BUILD had non-empty map keys; scenery will stay encrypted" }
}

/** Lumbridge mapsquare id = `(50 shl 8) or 50` — the milestone spawn region. */
private const val LUMBRIDGE_MAPSQUARE = 12850

/**
 * Ids of live oldschool caches with build major `<= TARGET_BUILD`, newest timestamp first — the
 * search order for a cache that actually carries map keys (§3 of the map-xtea research).
 */
private fun keyedCacheCandidates(cachesJson: String): List<Int> {
    val arr = Json.parseToJsonElement(cachesJson).jsonArray
    return arr.mapNotNull { el ->
        val o = el.jsonObject
        if (o["game"]?.jsonPrimitive?.contentOrNull != "oldschool") return@mapNotNull null
        if (o["environment"]?.jsonPrimitive?.contentOrNull != "live") return@mapNotNull null
        val majors = o["builds"]?.jsonArray?.mapNotNull { it.jsonObject["major"]?.jsonPrimitive?.intOrNull } ?: return@mapNotNull null
        if (majors.none { it <= TARGET_BUILD }) return@mapNotNull null
        val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val ts = o["timestamp"]?.jsonPrimitive?.contentOrNull ?: ""
        Triple(id, ts, majors.filter { it <= TARGET_BUILD }.max())
    }.sortedByDescending { it.second }.map { it.first }
}

/**
 * The flat-file dump does not contain the synthesized master index group (255,255) — the JS5
 * client's very first request at bootstrap. Fetches it directly from the per-group endpoint and
 * writes it into the same on-disk layout so the dump is complete for JS5 serving.
 */
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
