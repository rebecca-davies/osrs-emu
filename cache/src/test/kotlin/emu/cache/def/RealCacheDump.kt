package emu.cache.def

import emu.cache.container.Container
import emu.cache.def.codec.ItemDefCodec
import emu.cache.def.codec.ObjectDefCodec
import emu.cache.group.Group
import emu.cache.index.Js5IndexDecoder
import java.io.File
import kotlin.test.Test

class RealCacheDump {
    private val base = File("../cache-data/cache")
    private fun files(group: Int): Map<Int, ByteArray>? {
        val ref = File(base, "255/2.dat"); val grp = File(base, "2/$group.dat")
        if (!ref.isFile || !grp.isFile) return null
        val index = Js5IndexDecoder.decode(Container.decode(ref.readBytes()).data)
        val entry = index.groups.first { it.id == group }
        val byPos = Group.unpack(Container.decode(grp.readBytes()).data, entry.files.size)
        return entry.files.mapIndexed { pos, f -> f.id to byPos.getValue(pos) }.toMap()
    }
    private fun hex(b: ByteArray, n: Int = 40) = b.take(n).joinToString(" ") { "%02x".format(it) }
    @Test fun dump() {
        files(10)?.get(0)?.let { o ->
            val re = ItemDefCodec.encode(ItemDefCodec.decode(0, o))
            println("ITEM0 orig ${o.size}: ${hex(o)}")
            println("ITEM0 re   ${re.size}: ${hex(re)}")
        }
        files(6)?.get(1)?.let { o ->
            val re = ObjectDefCodec.encode(ObjectDefCodec.decode(1, o))
            println("OBJ1 orig ${o.size}: ${hex(o)}")
            println("OBJ1 re   ${re.size}: ${hex(re)}")
        }
    }
}
