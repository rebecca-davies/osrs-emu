package emu.cache.def

import emu.cache.container.Container
import emu.cache.def.codec.ItemDefCodec
import emu.cache.def.codec.NpcDefCodec
import emu.cache.def.codec.ObjectDefCodec
import emu.cache.group.Group
import emu.cache.index.Js5IndexDecoder
import java.io.File
import kotlin.test.Test

class RealCacheProbe {
    private val base = File("../cache-data/cache")

    private fun files(group: Int): Map<Int, ByteArray>? {
        val ref = File(base, "255/2.dat")
        val grp = File(base, "2/$group.dat")
        if (!ref.isFile || !grp.isFile) return null
        val index = Js5IndexDecoder.decode(Container.decode(ref.readBytes()).data)
        val entry = index.groups.first { it.id == group }
        val payload = Container.decode(grp.readBytes()).data
        val byPos = Group.unpack(payload, entry.files.size)
        return entry.files.mapIndexed { pos, f -> f.id to byPos.getValue(pos) }.toMap()
    }

    private fun probe(name: String, group: Int, encode: (Int, ByteArray) -> ByteArray) {
        val fs = files(group) ?: run { println("[$name] cache-data absent"); return }
        var ok = 0
        var bad = 0
        val samples = mutableListOf<String>()
        for ((id, bytes) in fs) {
            val re = try { encode(id, bytes) } catch (e: Throwable) {
                if (samples.size < 8) samples.add("id=$id EXC ${e.message}")
                bad++; continue
            }
            if (re.contentEquals(bytes)) ok++ else {
                bad++
                if (samples.size < 8) samples.add("id=$id lenOrig=${bytes.size} lenRe=${re.size} diff@${firstDiff(bytes, re)}")
            }
        }
        println("[$name] total=${fs.size} identical=$ok mismatch=$bad")
        samples.forEach { println("   $it") }
    }

    private fun firstDiff(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) if (a[i] != b[i]) return i
        return n
    }

    @Test fun probe() {
        probe("OBJECT", 6) { id, b -> ObjectDefCodec.encode(ObjectDefCodec.decode(id, b)) }
        probe("NPC", 9) { id, b -> NpcDefCodec.encode(NpcDefCodec.decode(id, b)) }
        probe("ITEM", 10) { id, b -> ItemDefCodec.encode(ItemDefCodec.decode(id, b)) }
    }
}
