package emu.cache.def.codec

import emu.buffer.JagexBuffer
import emu.cache.def.ItemDefinition
import emu.cache.def.ParamValue
import emu.cache.def.codec.field.FragmentWriter
import emu.cache.def.codec.field.Params
import emu.cache.def.codec.field.readSignedShort
import emu.cache.def.codec.field.writePairs

/**
 * Rev-239 item definition codec. Model fields have a u16
 * and an i32 opcode form; [encode] selects the u16 form unless a value falls outside `0..0xFFFF`.
 */
object ItemDefCodec {
    fun decode(id: Int, data: ByteArray): ItemDefinition {
        val buf = JagexBuffer(data)
        val ops = EntityOpsCodec.Builder()

        var inventoryModel: Int? = null
        var name: String? = null
        var examine: String? = null
        var zoom2d: Int? = null
        var xan2d: Int? = null
        var yan2d: Int? = null
        var xOffset2d: Int? = null
        var yOffset2d: Int? = null
        var unknown1: String? = null
        var stackable: Int? = null
        var cost: Int? = null
        var wearPos1: Int? = null
        var wearPos2: Int? = null
        var tradeableClear = false
        var members = false
        var maleModel0: Int? = null
        var maleOffset: Int? = null
        var maleModel1: Int? = null
        var femaleModel0: Int? = null
        var femaleOffset: Int? = null
        var femaleModel1: Int? = null
        var wearPos3: Int? = null
        val interfaceOptions = LinkedHashMap<Int, String>()
        var colorFind: List<Int>? = null
        var colorReplace: List<Int>? = null
        var textureFind: List<Int>? = null
        var textureReplace: List<Int>? = null
        var shiftClickDropIndex: Int? = null
        var subops: LinkedHashMap<Int, LinkedHashMap<Int, String>>? = null
        var maleModel2: Int? = null
        var femaleModel2: Int? = null
        var maleHeadModel: Int? = null
        var maleHeadModel2: Int? = null
        var femaleHeadModel: Int? = null
        var femaleHeadModel2: Int? = null
        var geTradeable = false
        var weight: Int? = null
        var category: Int? = null
        var zan2d: Int? = null
        var notedId: Int? = null
        var notedTemplate: Int? = null
        val stackVariants = LinkedHashMap<Int, ItemDefinition.StackVariant>()
        var resizeX: Int? = null
        var resizeY: Int? = null
        var resizeZ: Int? = null
        var ambient: Int? = null
        var contrast: Int? = null
        var team: Int? = null
        var boughtId: Int? = null
        var boughtTemplateId: Int? = null
        var placeholderId: Int? = null
        var placeholderTemplateId: Int? = null
        var params: Map<Int, ParamValue>? = null

        while (true) {
            val opcode = buf.readUByte()
            if (opcode == 0) break
            when (opcode) {
                1 -> inventoryModel = buf.readUShort()
                44 -> inventoryModel = buf.readInt()
                2 -> name = buf.readCString()
                3 -> examine = buf.readCString()
                4 -> zoom2d = buf.readUShort()
                5 -> xan2d = buf.readUShort()
                6 -> yan2d = buf.readUShort()
                7 -> xOffset2d = buf.readUShort().let { if (it > 32767) it - 65536 else it }
                8 -> yOffset2d = buf.readUShort().let { if (it > 32767) it - 65536 else it }
                9 -> unknown1 = buf.readCString()
                11 -> stackable = 1
                160 -> stackable = 2
                12 -> cost = buf.readInt()
                13 -> wearPos1 = buf.readByte()
                14 -> wearPos2 = buf.readByte()
                15 -> tradeableClear = true
                16 -> members = true
                23 -> { maleModel0 = buf.readUShort(); maleOffset = buf.readUByte() }
                45 -> { maleModel0 = buf.readInt(); maleOffset = buf.readUByte() }
                24 -> maleModel1 = buf.readUShort()
                46 -> maleModel1 = buf.readInt()
                25 -> { femaleModel0 = buf.readUShort(); femaleOffset = buf.readUByte() }
                48 -> { femaleModel0 = buf.readInt(); femaleOffset = buf.readUByte() }
                26 -> femaleModel1 = buf.readUShort()
                49 -> femaleModel1 = buf.readInt()
                27 -> wearPos3 = buf.readByte()
                in 30..34 -> ops.decodeOp(buf, opcode - 30)
                in 35..39 -> interfaceOptions[opcode - 35] = buf.readCString()
                40 -> { val (f, r) = readPairs(buf); colorFind = f; colorReplace = r }
                41 -> { val (f, r) = readPairs(buf); textureFind = f; textureReplace = r }
                42 -> shiftClickDropIndex = buf.readByte()
                43 -> {
                    val opId = buf.readUByte()
                    val map = subops ?: LinkedHashMap<Int, LinkedHashMap<Int, String>>().also { subops = it }
                    val slot = map.getOrPut(opId) { LinkedHashMap() }
                    while (true) {
                        val subopId = buf.readUByte() - 1
                        if (subopId == -1) break
                        slot[subopId] = buf.readCString()
                    }
                }
                47 -> maleModel2 = buf.readInt()
                78 -> maleModel2 = buf.readUShort()
                50 -> femaleModel2 = buf.readInt()
                79 -> femaleModel2 = buf.readUShort()
                51 -> maleHeadModel = buf.readInt()
                90 -> maleHeadModel = buf.readUShort()
                52 -> maleHeadModel2 = buf.readInt()
                92 -> maleHeadModel2 = buf.readUShort()
                53 -> femaleHeadModel = buf.readInt()
                91 -> femaleHeadModel = buf.readUShort()
                54 -> femaleHeadModel2 = buf.readInt()
                93 -> femaleHeadModel2 = buf.readUShort()
                65 -> geTradeable = true
                75 -> weight = buf.readSignedShort()
                94 -> category = buf.readUShort()
                95 -> zan2d = buf.readUShort()
                97 -> notedId = buf.readUShort()
                98 -> notedTemplate = buf.readUShort()
                in 100..109 -> stackVariants[opcode - 100] =
                    ItemDefinition.StackVariant(buf.readUShort(), buf.readUShort())
                110 -> resizeX = buf.readUShort()
                111 -> resizeY = buf.readUShort()
                112 -> resizeZ = buf.readUShort()
                113 -> ambient = buf.readByte()
                114 -> contrast = buf.readByte()
                115 -> team = buf.readUByte()
                139 -> boughtId = buf.readUShort()
                140 -> boughtTemplateId = buf.readUShort()
                148 -> placeholderId = buf.readUShort()
                149 -> placeholderTemplateId = buf.readUShort()
                200 -> ops.decodeSubOp(buf)
                201 -> ops.decodeConditionalOp(buf)
                202 -> ops.decodeConditionalSubOp(buf)
                249 -> params = Params.decode(buf)
                else -> error("Unrecognized item opcode $opcode for id $id")
            }
        }

        return ItemDefinition(
            id = id, inventoryModel = inventoryModel, name = name, examine = examine, zoom2d = zoom2d,
            xan2d = xan2d, yan2d = yan2d, xOffset2d = xOffset2d, yOffset2d = yOffset2d,
            unknown1 = unknown1, stackable = stackable, cost = cost, wearPos1 = wearPos1,
            wearPos2 = wearPos2, tradeableClear = tradeableClear, members = members,
            maleModel0 = maleModel0, maleOffset = maleOffset, maleModel1 = maleModel1,
            femaleModel0 = femaleModel0, femaleOffset = femaleOffset, femaleModel1 = femaleModel1,
            wearPos3 = wearPos3, groundOps = ops.build(), interfaceOptions = interfaceOptions,
            colorFind = colorFind, colorReplace = colorReplace, textureFind = textureFind,
            textureReplace = textureReplace, shiftClickDropIndex = shiftClickDropIndex, subops = subops,
            maleModel2 = maleModel2, femaleModel2 = femaleModel2, maleHeadModel = maleHeadModel,
            maleHeadModel2 = maleHeadModel2, femaleHeadModel = femaleHeadModel,
            femaleHeadModel2 = femaleHeadModel2, geTradeable = geTradeable, weight = weight,
            category = category, zan2d = zan2d, notedId = notedId, notedTemplate = notedTemplate,
            stackVariants = stackVariants, resizeX = resizeX, resizeY = resizeY, resizeZ = resizeZ,
            ambient = ambient, contrast = contrast, team = team, boughtId = boughtId,
            boughtTemplateId = boughtTemplateId, placeholderId = placeholderId,
            placeholderTemplateId = placeholderTemplateId, params = params,
        )
    }

    private fun readPairs(buf: JagexBuffer): Pair<List<Int>, List<Int>> {
        val len = buf.readUByte()
        val f = ArrayList<Int>(len)
        val r = ArrayList<Int>(len)
        repeat(len) { f.add(buf.readUShort()); r.add(buf.readUShort()) }
        return f to r
    }

    fun encode(def: ItemDefinition): ByteArray {
        val fw = FragmentWriter()
        writeModel(fw, def.inventoryModel, narrow = 1, wide = 44)
        def.name?.let { v -> fw.field(2) { writeString(v) } }
        def.examine?.let { v -> fw.field(3) { writeString(v) } }
        def.zoom2d?.let { v -> fw.field(4) { writeShort(v) } }
        def.xan2d?.let { v -> fw.field(5) { writeShort(v) } }
        def.yan2d?.let { v -> fw.field(6) { writeShort(v) } }
        def.xOffset2d?.let { v -> fw.field(7) { writeShort(v and 0xFFFF) } }
        def.yOffset2d?.let { v -> fw.field(8) { writeShort(v and 0xFFFF) } }
        def.unknown1?.let { v -> fw.field(9) { writeString(v) } }
        fw.flag(11, def.stackable == 1)
        def.cost?.let { v -> fw.field(12) { writeInt(v) } }
        def.wearPos1?.let { v -> fw.field(13) { writeByte(v) } }
        def.wearPos2?.let { v -> fw.field(14) { writeByte(v) } }
        fw.flag(15, def.tradeableClear)
        fw.flag(16, def.members)
        writeOffsetModel(fw, def.maleModel0, def.maleOffset, narrow = 23, wide = 45)
        writeModel(fw, def.maleModel1, narrow = 24, wide = 46)
        writeOffsetModel(fw, def.femaleModel0, def.femaleOffset, narrow = 25, wide = 48)
        writeModel(fw, def.femaleModel1, narrow = 26, wide = 49)
        def.wearPos3?.let { v -> fw.field(27) { writeByte(v) } }
        for ((slot, text) in def.interfaceOptions) fw.field(35 + slot) { writeString(text) }
        writePairs(fw, 40, def.colorFind, def.colorReplace)
        writePairs(fw, 41, def.textureFind, def.textureReplace)
        def.shiftClickDropIndex?.let { v -> fw.field(42) { writeByte(v) } }
        def.subops?.let { writeSubops(fw, it) }
        writeModel(fw, def.maleModel2, narrow = 78, wide = 47)
        writeModel(fw, def.femaleModel2, narrow = 79, wide = 50)
        writeModel(fw, def.maleHeadModel, narrow = 90, wide = 51)
        writeModel(fw, def.maleHeadModel2, narrow = 92, wide = 52)
        writeModel(fw, def.femaleHeadModel, narrow = 91, wide = 53)
        writeModel(fw, def.femaleHeadModel2, narrow = 93, wide = 54)
        fw.flag(65, def.geTradeable)
        def.weight?.let { v -> fw.field(75) { writeShort(v and 0xFFFF) } }
        def.category?.let { v -> fw.field(94) { writeShort(v) } }
        def.zan2d?.let { v -> fw.field(95) { writeShort(v) } }
        def.notedId?.let { v -> fw.field(97) { writeShort(v) } }
        def.notedTemplate?.let { v -> fw.field(98) { writeShort(v) } }
        for ((slot, sv) in def.stackVariants) fw.field(100 + slot) { writeShort(sv.obj); writeShort(sv.count) }
        def.resizeX?.let { v -> fw.field(110) { writeShort(v) } }
        def.resizeY?.let { v -> fw.field(111) { writeShort(v) } }
        def.resizeZ?.let { v -> fw.field(112) { writeShort(v) } }
        def.ambient?.let { v -> fw.field(113) { writeByte(v) } }
        def.contrast?.let { v -> fw.field(114) { writeByte(v) } }
        def.team?.let { v -> fw.field(115) { writeByte(v) } }
        def.boughtId?.let { v -> fw.field(139) { writeShort(v) } }
        def.boughtTemplateId?.let { v -> fw.field(140) { writeShort(v) } }
        def.placeholderId?.let { v -> fw.field(148) { writeShort(v) } }
        def.placeholderTemplateId?.let { v -> fw.field(149) { writeShort(v) } }
        EntityOpsCodec.encode(fw, def.groundOps, subOpcode = 200, condOpcode = 201, condSubOpcode = 202)
        def.params?.let { p -> fw.field(249) { Params.encode(this, p) } }
        return fw.build()
    }

    private fun writeModel(fw: FragmentWriter, model: Int?, narrow: Int, wide: Int) {
        model ?: return
        if (model in 0..0xFFFF) fw.field(narrow) { writeShort(model) } else fw.field(wide) { writeInt(model) }
    }

    private fun writeOffsetModel(fw: FragmentWriter, model: Int?, offset: Int?, narrow: Int, wide: Int) {
        model ?: return
        val off = offset ?: 0
        if (model in 0..0xFFFF) {
            fw.field(narrow) { writeShort(model); writeByte(off) }
        } else {
            fw.field(wide) { writeInt(model); writeByte(off) }
        }
    }

    private fun writeSubops(fw: FragmentWriter, subops: Map<Int, Map<Int, String>>) {
        for ((opId, slot) in subops) {
            fw.field(43) {
                writeByte(opId)
                for ((subopId, text) in slot.toSortedMap()) {
                    writeByte(subopId + 1)
                    writeString(text)
                }
                writeByte(0)
            }
        }
    }
}
