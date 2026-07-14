package emu.cache.index

import emu.buffer.JagexBuffer

/**
 * The peek-based variable-width integer encoding used by protocol-7 JS5 index counts and
 * delta-encoded ids (recon doc §3, `io/InputStream.java#readBigSmart` / `io/OutputStream.java#writeBigSmart`).
 * Values `< 32768` are written as u16; larger values as a u32 with the top bit set (masked off on
 * read). Protocol 5/6 always use a plain u16 instead — see [readIndexField]/[writeIndexField].
 */
internal object BigSmart {
    fun read(buf: JagexBuffer): Int {
        val hiBitSet = (buf.array[buf.pos].toInt() and 0x80) != 0
        return if (hiBitSet) buf.readInt() and Int.MAX_VALUE else buf.readUShort()
    }

    fun write(buf: JagexBuffer, value: Int) {
        require(value >= 0) { "bigSmart value must be non-negative, got $value" }
        if (value >= 32768) {
            buf.writeInt((1 shl 31) or value)
        } else {
            buf.writeShort(value)
        }
    }

    fun byteSize(value: Int): Int = if (value >= 32768) 4 else 2
}

/** Reads a count/id/delta field: bigSmart for protocol >= 7, plain u16 otherwise. */
internal fun readIndexField(buf: JagexBuffer, protocol: Int): Int =
    if (protocol >= 7) BigSmart.read(buf) else buf.readUShort()

/** Writes a count/id/delta field: bigSmart for protocol >= 7, plain u16 otherwise. */
internal fun writeIndexField(buf: JagexBuffer, protocol: Int, value: Int) {
    if (protocol >= 7) BigSmart.write(buf, value) else buf.writeShort(value)
}

/** Byte width of [writeIndexField] for the given [protocol] and [value]. */
internal fun indexFieldSize(protocol: Int, value: Int): Int =
    if (protocol >= 7) BigSmart.byteSize(value) else 2
