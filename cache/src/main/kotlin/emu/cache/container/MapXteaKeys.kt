package emu.cache.container

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One entry of an OpenRS2 `keys.json` dump: a map-region loc group (`l{x}_{y}`, archive 5) and the
 * 4-word XTEA key that decrypts it. Field names mirror the OpenRS2 schema; unknown fields are
 * ignored on parse.
 */
@Serializable
private data class MapXteaEntry(
    val mapsquare: Int,
    val key: List<Int>,
    val name: String = "",
    val name_hash: Int = 0,
    val group: Int = -1,
)

/**
 * Per-mapsquare XTEA keys for map-region (index 5) loc (`l`) groups, loaded from an OpenRS2
 * `keys.json` dump.
 *
 * Rev-239 REBUILD_NORMAL contains no XTEA keys. These keys are used only by server-side [Container]
 * decoding for encrypted loc groups; a zero key denotes plaintext.
 *
 * A mapsquare id is `(regionX shl 8) or regionY` where `regionX = worldX shr 6`; e.g. Lumbridge
 * `l50_50` = mapsquare `12850`.
 */
class MapXteaKeys(private val byMapsquare: Map<Int, XteaKey>) {
    /** Number of mapsquares with a key. */
    val size: Int get() = byMapsquare.size

    /** The key for [mapsquare] = `(regionX shl 8) or regionY`, or null if this dump has none. */
    operator fun get(mapsquare: Int): XteaKey? = byMapsquare[mapsquare]

    /** The key for the map region containing absolute world tile ([x], [y]), or null if absent. */
    fun forTile(x: Int, y: Int): XteaKey? = get(mapsquareOf(x, y))

    companion object {
        /** The mapsquare id for absolute world tile ([x], [y]): `(x shr 6 shl 8) or (y shr 6)`. */
        fun mapsquareOf(x: Int, y: Int): Int = ((x shr 6) shl 8) or (y shr 6)

        /**
         * The djb-style name hash index-5 named groups use for `l{x}_{y}`/`m{x}_{y}` group names:
         * `h = h * 31 + c` over the ASCII characters, as a signed 32-bit int. Matches the client's
         * own group-name hashing, so it maps a name to its [emu.cache.index.GroupEntry.nameHash].
         */
        fun nameHash(name: String): Int = name.fold(0) { h, c -> h * 31 + c.code }

        /** Parses the OpenRS2 `keys.json` array form into a mapsquare -> [XteaKey] map. */
        fun parse(json: String): MapXteaKeys {
            val entries = Json { ignoreUnknownKeys = true }.decodeFromString<List<MapXteaEntry>>(json)
            val map = HashMap<Int, XteaKey>(entries.size)
            for (e in entries) {
                require(e.key.size == 4) {
                    "map XTEA key for mapsquare ${e.mapsquare} must have 4 words, got ${e.key.size}"
                }
                map[e.mapsquare] = XteaKey(e.key[0], e.key[1], e.key[2], e.key[3])
            }
            return MapXteaKeys(map)
        }

        /** Loads and parses a vendored OpenRS2 `keys.json` file (e.g. `cache-data/xtea/keys.json`). */
        fun load(file: File): MapXteaKeys = parse(file.readText())
    }
}
