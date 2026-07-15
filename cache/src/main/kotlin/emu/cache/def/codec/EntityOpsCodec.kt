package emu.cache.def.codec

import emu.buffer.JagexBuffer
import emu.cache.def.EntityOps

/**
 * Decodes and encodes [EntityOps] with a definition-specific opcode set. Plain operations use
 * opcodes 30-34; the sub/conditional operation
 * opcodes vary (objects 100-102, items 200-202, npcs 251-253) and are passed in per call.
 */
internal object EntityOpsCodec {
    /** Mutable accumulator used by the opcode loops, sealed into an immutable [EntityOps] via [build]. */
    class Builder {
        private val ops = LinkedHashMap<Int, String>()
        private val subOps = mutableListOf<EntityOps.SubOp>()
        private val conditionalOps = mutableListOf<EntityOps.ConditionalOp>()
        private val conditionalSubOps = mutableListOf<EntityOps.ConditionalSubOp>()

        /** opcodes 30-34: a plain menu op; "Hidden" text is dropped, matching the loader. */
        fun decodeOp(buf: JagexBuffer, index: Int) {
            val text = buf.readCString()
            if (!text.equals("Hidden", ignoreCase = true)) ops[index] = text
        }

        fun decodeSubOp(buf: JagexBuffer) {
            val index = buf.readUByte()
            val subId = buf.readUByte()
            val text = buf.readCString()
            subOps.add(EntityOps.SubOp(index, subId, text))
        }

        fun decodeConditionalOp(buf: JagexBuffer) {
            val index = buf.readUByte()
            val varp = buf.readUShort()
            val varb = buf.readUShort()
            val min = buf.readInt()
            val max = buf.readInt()
            val text = buf.readCString()
            conditionalOps.add(EntityOps.ConditionalOp(index, varp, varb, min, max, text))
        }

        fun decodeConditionalSubOp(buf: JagexBuffer) {
            val index = buf.readUByte()
            val subId = buf.readUShort()
            val varp = buf.readUShort()
            val varb = buf.readUShort()
            val min = buf.readInt()
            val max = buf.readInt()
            val text = buf.readCString()
            conditionalSubOps.add(EntityOps.ConditionalSubOp(index, subId, varp, varb, min, max, text))
        }

        fun build(): EntityOps = EntityOps(ops, subOps, conditionalOps, conditionalSubOps)
    }

    /**
     * Records every entity-op field as opcode fragments on [fw]: plain ops (30-34), then sub ops,
     * conditional ops and conditional sub ops at the given type-specific opcodes. [FragmentWriter]'s
     * stable ascending sort interleaves them correctly with the rest of the definition.
     */
    fun encode(fw: FragmentWriter, entityOps: EntityOps, subOpcode: Int, condOpcode: Int, condSubOpcode: Int) {
        for ((index, text) in entityOps.ops) {
            fw.field(30 + index) { writeString(text) }
        }
        for (s in entityOps.subOps) {
            fw.field(subOpcode) { writeByte(s.index); writeByte(s.subId); writeString(s.text) }
        }
        for (c in entityOps.conditionalOps) {
            fw.field(condOpcode) {
                writeByte(c.index); writeShort(c.varp); writeShort(c.varb)
                writeInt(c.min); writeInt(c.max); writeString(c.text)
            }
        }
        for (c in entityOps.conditionalSubOps) {
            fw.field(condSubOpcode) {
                writeByte(c.index); writeShort(c.subId); writeShort(c.varp); writeShort(c.varb)
                writeInt(c.min); writeInt(c.max); writeString(c.text)
            }
        }
    }
}
