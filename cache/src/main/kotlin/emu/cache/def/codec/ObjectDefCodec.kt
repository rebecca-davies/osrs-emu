package emu.cache.def.codec

import emu.buffer.JagexBuffer
import emu.cache.def.EntityOps
import emu.cache.def.ObjectDefinition
import emu.cache.def.Params
import emu.cache.def.VarTransform

/**
 * Rev-239 object definition codec. [decode] runs the
 * tagged opcode loop; [encode] re-emits every present field in ascending opcode order (the order
 * Jagex's own tooling writes, and which the order-tolerant client accepts) terminated by opcode 0.
 *
 * rev-239 gate: `rev220SoundData` is true, so opcodes 78/79 carry the extra `ambientSoundRetain` u8.
 */
object ObjectDefCodec {
    fun decode(id: Int, data: ByteArray): ObjectDefinition {
        val buf = JagexBuffer(data)
        val ops = EntityOpsCodec.Builder()

        var name: String? = null
        var models: List<Int>? = null
        var types: List<Int>? = null
        var sizeX: Int? = null
        var sizeY: Int? = null
        var interactType: Int? = null
        var blocksProjectileClear = false
        var wallOrDoor: Int? = null
        var contouredGround: Int? = null
        var mergeNormals = false
        var modelClipped = false
        var animationId: Int? = null
        var decorDisplacement: Int? = null
        var ambient: Int? = null
        var contrast: Int? = null
        var recolorToFind: List<Int>? = null
        var recolorToReplace: List<Int>? = null
        var retextureToFind: List<Int>? = null
        var retextureToReplace: List<Int>? = null
        var category: Int? = null
        var rotated = false
        var shadowClear = false
        var modelSizeX: Int? = null
        var modelSizeHeight: Int? = null
        var modelSizeY: Int? = null
        var mapSceneId: Int? = null
        var blockingMask: Int? = null
        var offsetX: Int? = null
        var offsetHeight: Int? = null
        var offsetY: Int? = null
        var obstructsGround = false
        var hollow = false
        var supportsItems: Int? = null
        var varTransform: VarTransform? = null
        var ambientSound: ObjectDefinition.AmbientSound? = null
        var ambientSoundChange: ObjectDefinition.AmbientSoundChange? = null
        var mapAreaId: Int? = null
        var randomizeAnimStart = false
        var deferAnimChange = false
        var soundDistanceFadeCurve: Int? = null
        var soundFade: ObjectDefinition.SoundFade? = null
        var unknown1 = false
        var soundVisibility: Int? = null
        var raise: Int? = null
        var params: Map<Int, emu.cache.def.ParamValue>? = null

        while (true) {
            val opcode = buf.readUByte()
            if (opcode == 0) break
            when {
                opcode == 1 || opcode == 6 -> {
                    val len = buf.readUByte()
                    if (len > 0) {
                        val m = ArrayList<Int>(len)
                        val t = ArrayList<Int>(len)
                        repeat(len) {
                            m.add(if (opcode == 1) buf.readUShort() else buf.readInt())
                            t.add(buf.readUByte())
                        }
                        models = m
                        types = t
                    }
                }
                opcode == 5 || opcode == 7 -> {
                    val len = buf.readUByte()
                    if (len > 0) {
                        types = null
                        val m = ArrayList<Int>(len)
                        repeat(len) { m.add(if (opcode == 5) buf.readUShort() else buf.readInt()) }
                        models = m
                    }
                }
                opcode == 2 -> name = buf.readCString()
                opcode == 14 -> sizeX = buf.readUByte()
                opcode == 15 -> sizeY = buf.readUByte()
                opcode == 17 -> interactType = 0
                opcode == 18 -> blocksProjectileClear = true
                opcode == 19 -> wallOrDoor = buf.readUByte()
                opcode == 21 -> contouredGround = 0
                opcode == 22 -> mergeNormals = true
                opcode == 23 -> modelClipped = true
                opcode == 24 -> buf.readUShort().let { animationId = if (it == 0xFFFF) -1 else it }
                opcode == 27 -> interactType = 1
                opcode == 28 -> decorDisplacement = buf.readUByte()
                opcode == 29 -> ambient = buf.readByte()
                opcode == 39 -> contrast = buf.readByte() * 25
                opcode in 30..34 -> ops.decodeOp(buf, opcode - 30)
                opcode == 40 -> {
                    val len = buf.readUByte()
                    val f = ArrayList<Int>(len)
                    val r = ArrayList<Int>(len)
                    repeat(len) { f.add(buf.readSignedShort()); r.add(buf.readSignedShort()) }
                    recolorToFind = f; recolorToReplace = r
                }
                opcode == 41 -> {
                    val len = buf.readUByte()
                    val f = ArrayList<Int>(len)
                    val r = ArrayList<Int>(len)
                    repeat(len) { f.add(buf.readSignedShort()); r.add(buf.readSignedShort()) }
                    retextureToFind = f; retextureToReplace = r
                }
                opcode == 61 -> category = buf.readUShort()
                opcode == 62 -> rotated = true
                opcode == 64 -> shadowClear = true
                opcode == 65 -> modelSizeX = buf.readUShort()
                opcode == 66 -> modelSizeHeight = buf.readUShort()
                opcode == 67 -> modelSizeY = buf.readUShort()
                opcode == 68 -> mapSceneId = buf.readUShort()
                opcode == 69 -> blockingMask = buf.readByte()
                opcode == 70 -> offsetX = buf.readUShort()
                opcode == 71 -> offsetHeight = buf.readUShort()
                opcode == 72 -> offsetY = buf.readUShort()
                opcode == 73 -> obstructsGround = true
                opcode == 74 -> hollow = true
                opcode == 75 -> supportsItems = buf.readUByte()
                opcode == 77 -> varTransform = decodeVarTransform(buf, extended = false)
                opcode == 92 -> varTransform = decodeVarTransform(buf, extended = true)
                opcode == 78 -> {
                    val soundId = buf.readUShort()
                    val distance = buf.readUByte()
                    val retain = buf.readUByte()
                    ambientSound = ObjectDefinition.AmbientSound(soundId, distance, retain)
                }
                opcode == 79 -> {
                    val ticksMin = buf.readUShort()
                    val ticksMax = buf.readUShort()
                    val distance = buf.readUByte()
                    val retain = buf.readUByte()
                    val len = buf.readUByte()
                    val ids = ArrayList<Int>(len)
                    repeat(len) { ids.add(buf.readUShort()) }
                    ambientSoundChange = ObjectDefinition.AmbientSoundChange(ticksMin, ticksMax, distance, retain, ids)
                }
                opcode == 81 -> contouredGround = buf.readUByte() * 256
                opcode == 82 -> mapAreaId = buf.readUShort()
                opcode == 89 -> randomizeAnimStart = true
                opcode == 90 -> deferAnimChange = true
                opcode == 91 -> soundDistanceFadeCurve = buf.readUByte()
                opcode == 93 -> soundFade = ObjectDefinition.SoundFade(
                    buf.readUByte(), buf.readUShort(), buf.readUByte(), buf.readUShort(),
                )
                opcode == 94 -> unknown1 = true
                opcode == 95 -> soundVisibility = buf.readUByte()
                opcode == 96 -> raise = buf.readUByte()
                opcode == 100 -> ops.decodeSubOp(buf)
                opcode == 101 -> ops.decodeConditionalOp(buf)
                opcode == 102 -> ops.decodeConditionalSubOp(buf)
                opcode == 249 -> params = Params.decode(buf)
                else -> error("Unrecognized object opcode $opcode for id $id")
            }
        }

        return ObjectDefinition(
            id = id, name = name, objectModels = models, objectTypes = types,
            sizeX = sizeX, sizeY = sizeY, interactType = interactType,
            blocksProjectileClear = blocksProjectileClear, wallOrDoor = wallOrDoor,
            contouredGround = contouredGround, mergeNormals = mergeNormals, modelClipped = modelClipped,
            animationId = animationId, decorDisplacement = decorDisplacement, ambient = ambient,
            contrast = contrast, ops = ops.build(), recolorToFind = recolorToFind,
            recolorToReplace = recolorToReplace, retextureToFind = retextureToFind,
            retextureToReplace = retextureToReplace, category = category, rotated = rotated,
            shadowClear = shadowClear, modelSizeX = modelSizeX, modelSizeHeight = modelSizeHeight,
            modelSizeY = modelSizeY, mapSceneId = mapSceneId, blockingMask = blockingMask,
            offsetX = offsetX, offsetHeight = offsetHeight, offsetY = offsetY,
            obstructsGround = obstructsGround, hollow = hollow, supportsItems = supportsItems,
            varTransform = varTransform, ambientSound = ambientSound,
            ambientSoundChange = ambientSoundChange, mapAreaId = mapAreaId,
            randomizeAnimStart = randomizeAnimStart, deferAnimChange = deferAnimChange,
            soundDistanceFadeCurve = soundDistanceFadeCurve, soundFade = soundFade,
            unknown1 = unknown1, soundVisibility = soundVisibility, raise = raise, params = params,
        )
    }

    private fun decodeVarTransform(buf: JagexBuffer, extended: Boolean): VarTransform {
        val varbitId = buf.readUShort().let { if (it == 0xFFFF) -1 else it }
        val varpId = buf.readUShort().let { if (it == 0xFFFF) -1 else it }
        val trailingVar = if (extended) buf.readUShort().let { if (it == 0xFFFF) -1 else it } else null
        val len = buf.readUByte()
        val dest = ArrayList<Int>(len + 1)
        repeat(len + 1) { dest.add(buf.readUShort().let { if (it == 0xFFFF) -1 else it }) }
        return VarTransform(varbitId, varpId, dest, trailingVar)
    }

    fun encode(def: ObjectDefinition): ByteArray {
        val fw = FragmentWriter()
        val models = def.objectModels
        if (models != null) {
            val wide = models.any { it !in 0..0xFFFF }
            if (def.objectTypes != null) {
                fw.field(if (wide) 6 else 1) {
                    writeByte(models.size)
                    for (i in models.indices) {
                        if (wide) writeInt(models[i]) else writeShort(models[i])
                        writeByte(def.objectTypes[i])
                    }
                }
            } else {
                fw.field(if (wide) 7 else 5) {
                    writeByte(models.size)
                    for (m in models) if (wide) writeInt(m) else writeShort(m)
                }
            }
        }
        def.name?.let { v -> fw.field(2) { writeString(v) } }
        def.sizeX?.let { v -> fw.field(14) { writeByte(v) } }
        def.sizeY?.let { v -> fw.field(15) { writeByte(v) } }
        fw.flag(17, def.interactType == 0)
        fw.flag(18, def.blocksProjectileClear)
        def.wallOrDoor?.let { v -> fw.field(19) { writeByte(v) } }
        fw.flag(21, def.contouredGround == 0)
        fw.flag(22, def.mergeNormals)
        fw.flag(23, def.modelClipped)
        def.animationId?.let { v -> fw.field(24) { writeShort(if (v == -1) 0xFFFF else v) } }
        fw.flag(27, def.interactType == 1)
        def.decorDisplacement?.let { v -> fw.field(28) { writeByte(v) } }
        def.ambient?.let { v -> fw.field(29) { writeByte(v) } }
        def.contrast?.let { v -> fw.field(39) { writeByte(v / 25) } }
        writePairs(fw, 40, def.recolorToFind, def.recolorToReplace)
        writePairs(fw, 41, def.retextureToFind, def.retextureToReplace)
        def.category?.let { v -> fw.field(61) { writeShort(v) } }
        fw.flag(62, def.rotated)
        fw.flag(64, def.shadowClear)
        def.modelSizeX?.let { v -> fw.field(65) { writeShort(v) } }
        def.modelSizeHeight?.let { v -> fw.field(66) { writeShort(v) } }
        def.modelSizeY?.let { v -> fw.field(67) { writeShort(v) } }
        def.mapSceneId?.let { v -> fw.field(68) { writeShort(v) } }
        def.blockingMask?.let { v -> fw.field(69) { writeByte(v) } }
        def.offsetX?.let { v -> fw.field(70) { writeShort(v) } }
        def.offsetHeight?.let { v -> fw.field(71) { writeShort(v) } }
        def.offsetY?.let { v -> fw.field(72) { writeShort(v) } }
        fw.flag(73, def.obstructsGround)
        fw.flag(74, def.hollow)
        def.supportsItems?.let { v -> fw.field(75) { writeByte(v) } }
        def.varTransform?.let { vt -> writeVarTransform(fw, vt, baseOpcode = 77, extendedOpcode = 92) }
        def.ambientSound?.let { s ->
            fw.field(78) {
                writeShort(s.soundId); writeByte(s.distance)
                s.retain?.let { writeByte(it) }
            }
        }
        def.ambientSoundChange?.let { s ->
            fw.field(79) {
                writeShort(s.ticksMin); writeShort(s.ticksMax); writeByte(s.distance)
                s.retain?.let { writeByte(it) }
                writeByte(s.soundIds.size)
                for (id in s.soundIds) writeShort(id)
            }
        }
        if (def.contouredGround != null && def.contouredGround != 0) {
            fw.field(81) { writeByte(def.contouredGround / 256) }
        }
        def.mapAreaId?.let { v -> fw.field(82) { writeShort(v) } }
        fw.flag(89, def.randomizeAnimStart)
        fw.flag(90, def.deferAnimChange)
        def.soundDistanceFadeCurve?.let { v -> fw.field(91) { writeByte(v) } }
        def.soundFade?.let { s ->
            fw.field(93) {
                writeByte(s.inCurve); writeShort(s.inDuration); writeByte(s.outCurve); writeShort(s.outDuration)
            }
        }
        fw.flag(94, def.unknown1)
        def.soundVisibility?.let { v -> fw.field(95) { writeByte(v) } }
        def.raise?.let { v -> fw.field(96) { writeByte(v) } }
        EntityOpsCodec.encode(fw, def.ops, subOpcode = 100, condOpcode = 101, condSubOpcode = 102)
        def.params?.let { p -> fw.field(249) { Params.encode(this, p) } }
        return fw.build()
    }
}

/** Emits a [VarTransform] as a base or extended opcode fragment. */
internal fun writeVarTransform(fw: FragmentWriter, vt: VarTransform, baseOpcode: Int, extendedOpcode: Int) {
    val len = vt.configChangeDest.size - 1
    if (vt.trailingVar != null) {
        fw.field(extendedOpcode) {
            writeShort(if (vt.varbitId == -1) 0xFFFF else vt.varbitId)
            writeShort(if (vt.varpId == -1) 0xFFFF else vt.varpId)
            writeShort(if (vt.trailingVar == -1) 0xFFFF else vt.trailingVar)
            writeByte(len)
            for (c in vt.configChangeDest) writeShort(if (c == -1) 0xFFFF else c)
        }
    } else {
        fw.field(baseOpcode) {
            writeShort(if (vt.varbitId == -1) 0xFFFF else vt.varbitId)
            writeShort(if (vt.varpId == -1) 0xFFFF else vt.varpId)
            writeByte(len)
            for (c in vt.configChangeDest) writeShort(if (c == -1) 0xFFFF else c)
        }
    }
}

/** Emits paired find/replace colour or texture values under [opcode]. */
internal fun writePairs(fw: FragmentWriter, opcode: Int, find: List<Int>?, replace: List<Int>?) {
    if (find == null || replace == null) return
    fw.field(opcode) {
        writeByte(find.size)
        for (i in find.indices) { writeShort(find[i]); writeShort(replace[i]) }
    }
}
