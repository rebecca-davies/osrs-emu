package emu.cache.def.codec

import emu.buffer.JagexBuffer
import emu.cache.def.NpcDefinition
import emu.cache.def.ParamValue
import emu.cache.def.Params
import emu.cache.def.VarTransform

/**
 * Byte-exact inverse of RuneLite's `NpcLoader` for rev 239 (recon doc §4b). rev-239 gates are fixed:
 * opcode 102 uses the `rev210` head-icon bitfield, and opcode 111 sets `renderPriority = 2`.
 */
object NpcDefCodec {
    fun decode(id: Int, data: ByteArray): NpcDefinition {
        val buf = JagexBuffer(data)
        val ops = EntityOpsCodec.Builder()

        var name: String? = null
        var size: Int? = null
        var models: List<Int>? = null
        var chatheadModels: List<Int>? = null
        var standingAnimation: Int? = null
        var walkingAnimation: Int? = null
        var idleRotateLeftAnimation: Int? = null
        var idleRotateRightAnimation: Int? = null
        var walkSequence: NpcDefinition.RotationSequence? = null
        var category: Int? = null
        var recolorToFind: List<Int>? = null
        var recolorToReplace: List<Int>? = null
        var retextureToFind: List<Int>? = null
        var retextureToReplace: List<Int>? = null
        val stats = LinkedHashMap<Int, Int>()
        var minimapVisibleClear = false
        var combatLevel: Int? = null
        var widthScale: Int? = null
        var heightScale: Int? = null
        var renderPriority: Int? = null
        var ambient: Int? = null
        var contrast: Int? = null
        var headIcons: List<NpcDefinition.HeadIcon>? = null
        var rotationSpeed: Int? = null
        var varTransform: VarTransform? = null
        var interactableClear = false
        var rotationFlagClear = false
        var runAnimation: Int? = null
        var runSequence: NpcDefinition.RotationSequence? = null
        var crawlAnimation: Int? = null
        var crawlSequence: NpcDefinition.RotationSequence? = null
        var isFollower = false
        var lowPriorityFollowerOps = false
        var height: Int? = null
        var footprintSize: Int? = null
        var unknown1 = false
        var idleAnimRestart = false
        var canHideForOverlap = false
        var overlapTintHSL: Int? = null
        var zbufClear = false
        var params: Map<Int, ParamValue>? = null

        while (true) {
            val opcode = buf.readUByte()
            if (opcode == 0) break
            when (opcode) {
                1 -> models = readModels(buf, wide = false)
                61 -> models = readModels(buf, wide = true)
                60 -> chatheadModels = readModels(buf, wide = false)
                62 -> chatheadModels = readModels(buf, wide = true)
                2 -> name = buf.readCString()
                12 -> size = buf.readUByte()
                13 -> standingAnimation = buf.readUShort()
                14 -> walkingAnimation = buf.readUShort()
                15 -> idleRotateLeftAnimation = buf.readUShort()
                16 -> idleRotateRightAnimation = buf.readUShort()
                17 -> walkSequence = readSequence(buf)
                18 -> category = buf.readUShort()
                in 30..34 -> ops.decodeOp(buf, opcode - 30)
                40 -> { val (f, r) = readPairs(buf); recolorToFind = f; recolorToReplace = r }
                41 -> { val (f, r) = readPairs(buf); retextureToFind = f; retextureToReplace = r }
                in 74..79 -> stats[opcode - 74] = buf.readUShort()
                93 -> minimapVisibleClear = true
                95 -> combatLevel = buf.readUShort()
                97 -> widthScale = buf.readUShort()
                98 -> heightScale = buf.readUShort()
                99 -> renderPriority = 1
                100 -> ambient = buf.readByte()
                101 -> contrast = buf.readByte()
                102 -> headIcons = readHeadIcons(buf)
                103 -> rotationSpeed = buf.readUShort()
                106 -> varTransform = readVarTransform(buf, extended = false)
                118 -> varTransform = readVarTransform(buf, extended = true)
                107 -> interactableClear = true
                109 -> rotationFlagClear = true
                111 -> renderPriority = 2
                114 -> runAnimation = buf.readUShort()
                115 -> runSequence = readSequence(buf)
                116 -> crawlAnimation = buf.readUShort()
                117 -> crawlSequence = readSequence(buf)
                122 -> isFollower = true
                123 -> lowPriorityFollowerOps = true
                124 -> height = buf.readUShort()
                126 -> footprintSize = buf.readUShort()
                129 -> unknown1 = true
                130 -> idleAnimRestart = true
                145 -> canHideForOverlap = true
                146 -> overlapTintHSL = buf.readUShort()
                147 -> zbufClear = true
                249 -> params = Params.decode(buf)
                251 -> ops.decodeSubOp(buf)
                252 -> ops.decodeConditionalOp(buf)
                253 -> ops.decodeConditionalSubOp(buf)
                else -> error("Unrecognized npc opcode $opcode for id $id")
            }
        }

        return NpcDefinition(
            id = id, name = name, size = size, models = models, chatheadModels = chatheadModels,
            standingAnimation = standingAnimation, walkingAnimation = walkingAnimation,
            idleRotateLeftAnimation = idleRotateLeftAnimation,
            idleRotateRightAnimation = idleRotateRightAnimation, walkSequence = walkSequence,
            category = category, ops = ops.build(), recolorToFind = recolorToFind,
            recolorToReplace = recolorToReplace, retextureToFind = retextureToFind,
            retextureToReplace = retextureToReplace, stats = stats,
            minimapVisibleClear = minimapVisibleClear, combatLevel = combatLevel,
            widthScale = widthScale, heightScale = heightScale, renderPriority = renderPriority,
            ambient = ambient, contrast = contrast, headIcons = headIcons,
            rotationSpeed = rotationSpeed, varTransform = varTransform,
            interactableClear = interactableClear, rotationFlagClear = rotationFlagClear,
            runAnimation = runAnimation, runSequence = runSequence, crawlAnimation = crawlAnimation,
            crawlSequence = crawlSequence, isFollower = isFollower,
            lowPriorityFollowerOps = lowPriorityFollowerOps, height = height,
            footprintSize = footprintSize, unknown1 = unknown1, idleAnimRestart = idleAnimRestart,
            canHideForOverlap = canHideForOverlap, overlapTintHSL = overlapTintHSL,
            zbufClear = zbufClear, params = params,
        )
    }

    private fun readModels(buf: JagexBuffer, wide: Boolean): List<Int> {
        val len = buf.readUByte()
        return List(len) { if (wide) buf.readInt() else buf.readUShort() }
    }

    private fun readSequence(buf: JagexBuffer) = NpcDefinition.RotationSequence(
        buf.readUShort(), buf.readUShort(), buf.readUShort(), buf.readUShort(),
    )

    private fun readPairs(buf: JagexBuffer): Pair<List<Int>, List<Int>> {
        val len = buf.readUByte()
        val f = ArrayList<Int>(len)
        val r = ArrayList<Int>(len)
        repeat(len) { f.add(buf.readUShort()); r.add(buf.readUShort()) }
        return f to r
    }

    private fun readHeadIcons(buf: JagexBuffer): List<NpcDefinition.HeadIcon> {
        val bitfield = buf.readUByte()
        var len = 0
        var v = bitfield
        while (v != 0) { len++; v = v shr 1 }
        return List(len) { i ->
            if ((bitfield and (1 shl i)) == 0) {
                NpcDefinition.HeadIcon(-1, -1)
            } else {
                NpcDefinition.HeadIcon(buf.readBigSmart2(), buf.readUnsignedShortSmartMinusOne())
            }
        }
    }

    private fun readVarTransform(buf: JagexBuffer, extended: Boolean): VarTransform {
        val varbitId = buf.readUShort().let { if (it == 0xFFFF) -1 else it }
        val varpId = buf.readUShort().let { if (it == 0xFFFF) -1 else it }
        val trailingVar = if (extended) buf.readUShort().let { if (it == 0xFFFF) -1 else it } else null
        val len = buf.readUByte()
        val dest = ArrayList<Int>(len + 1)
        repeat(len + 1) { dest.add(buf.readUShort().let { if (it == 0xFFFF) -1 else it }) }
        return VarTransform(varbitId, varpId, dest, trailingVar)
    }

    fun encode(def: NpcDefinition): ByteArray {
        val fw = FragmentWriter()
        def.models?.let { writeModels(fw, if (it.any { m -> m !in 0..0xFFFF }) 61 else 1, it) }
        def.name?.let { v -> fw.field(2) { writeString(v) } }
        def.size?.let { v -> fw.field(12) { writeByte(v) } }
        def.standingAnimation?.let { v -> fw.field(13) { writeShort(v) } }
        def.walkingAnimation?.let { v -> fw.field(14) { writeShort(v) } }
        def.idleRotateLeftAnimation?.let { v -> fw.field(15) { writeShort(v) } }
        def.idleRotateRightAnimation?.let { v -> fw.field(16) { writeShort(v) } }
        def.walkSequence?.let { s -> fw.field(17) { writeSequence(this, s) } }
        def.category?.let { v -> fw.field(18) { writeShort(v) } }
        writePairs(fw, 40, def.recolorToFind, def.recolorToReplace)
        writePairs(fw, 41, def.retextureToFind, def.retextureToReplace)
        def.chatheadModels?.let { writeModels(fw, if (it.any { m -> m !in 0..0xFFFF }) 62 else 60, it) }
        for ((slot, value) in def.stats) fw.field(74 + slot) { writeShort(value) }
        fw.flag(93, def.minimapVisibleClear)
        def.combatLevel?.let { v -> fw.field(95) { writeShort(v) } }
        def.widthScale?.let { v -> fw.field(97) { writeShort(v) } }
        def.heightScale?.let { v -> fw.field(98) { writeShort(v) } }
        fw.flag(99, def.renderPriority == 1)
        def.ambient?.let { v -> fw.field(100) { writeByte(v) } }
        def.contrast?.let { v -> fw.field(101) { writeByte(v) } }
        def.headIcons?.let { writeHeadIcons(fw, it) }
        def.rotationSpeed?.let { v -> fw.field(103) { writeShort(v) } }
        def.varTransform?.let { writeVarTransform(fw, it, baseOpcode = 106, extendedOpcode = 118) }
        fw.flag(107, def.interactableClear)
        fw.flag(109, def.rotationFlagClear)
        fw.flag(111, def.renderPriority == 2)
        def.runAnimation?.let { v -> fw.field(114) { writeShort(v) } }
        def.runSequence?.let { s -> fw.field(115) { writeSequence(this, s) } }
        def.crawlAnimation?.let { v -> fw.field(116) { writeShort(v) } }
        def.crawlSequence?.let { s -> fw.field(117) { writeSequence(this, s) } }
        fw.flag(122, def.isFollower)
        fw.flag(123, def.lowPriorityFollowerOps)
        def.height?.let { v -> fw.field(124) { writeShort(v) } }
        def.footprintSize?.let { v -> fw.field(126) { writeShort(v) } }
        fw.flag(129, def.unknown1)
        fw.flag(130, def.idleAnimRestart)
        fw.flag(145, def.canHideForOverlap)
        def.overlapTintHSL?.let { v -> fw.field(146) { writeShort(v) } }
        fw.flag(147, def.zbufClear)
        EntityOpsCodec.encode(fw, def.ops, subOpcode = 251, condOpcode = 252, condSubOpcode = 253)
        def.params?.let { p -> fw.field(249) { Params.encode(this, p) } }
        return fw.build()
    }

    private fun writeModels(fw: FragmentWriter, opcode: Int, models: List<Int>) {
        val wide = opcode == 61 || opcode == 62
        fw.field(opcode) {
            writeByte(models.size)
            for (m in models) if (wide) writeInt(m) else writeShort(m)
        }
    }

    private fun writeSequence(w: DefWriter, s: NpcDefinition.RotationSequence) {
        w.writeShort(s.animation); w.writeShort(s.rotate180); w.writeShort(s.rotateLeft); w.writeShort(s.rotateRight)
    }

    private fun writeHeadIcons(fw: FragmentWriter, icons: List<NpcDefinition.HeadIcon>) {
        var bitfield = 0
        for (i in icons.indices) {
            val ic = icons[i]
            if (ic.archiveId != -1 || ic.spriteIndex != -1) bitfield = bitfield or (1 shl i)
        }
        fw.field(102) {
            writeByte(bitfield)
            for (i in icons.indices) {
                if ((bitfield and (1 shl i)) != 0) {
                    writeBigSmart2(icons[i].archiveId)
                    writeUnsignedShortSmartMinusOne(icons[i].spriteIndex)
                }
            }
        }
    }
}
