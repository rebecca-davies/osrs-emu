package emu.server.game.world.npc

import emu.cache.def.EntityOps
import emu.cache.def.NpcDefinition
import emu.cache.def.VarTransform
import emu.cache.def.VarbitDefinition
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcConditionalOperation
import emu.game.npc.NpcOperations
import emu.game.npc.NpcTransform
import emu.game.npc.NpcType
import emu.game.varp.PlayerVariable

/** Maps cache NPC definitions into the immutable type model owned by game. */
class CacheNpcCatalog(
    definitions: Iterable<NpcDefinition>,
    varbits: Iterable<VarbitDefinition> = emptyList(),
) : NpcCatalog {
    private val types = arrayOfNulls<NpcType>(MAX_NPC_TYPE + 1)

    init {
        val varbitsById = varbits.associateBy(VarbitDefinition::id)
        for (definition in definitions) {
            val name = definition.name?.takeIf(String::isNotBlank) ?: continue
            if (definition.id !in types.indices) continue
            types[definition.id] =
                NpcType(
                    id = definition.id,
                    name = name,
                    size = definition.size ?: DEFAULT_SIZE,
                    operations = definition.ops.toNpcOperations(varbitsById),
                    transform = definition.varTransform?.toNpcTransform(varbitsById),
                )
        }
    }

    override fun get(type: Int): NpcType? = types.getOrNull(type)

    private fun EntityOps.toNpcOperations(
        varbitsById: Map<Int, VarbitDefinition>,
    ): NpcOperations =
        NpcOperations(
            options =
                ops
                    .filterValues { it.isVisibleOperation() }
                    .keys
                    .mapTo(linkedSetOf()) { it + 1 },
            subOptions =
                subOps
                    .filter { it.subId in WIRE_SUB_OPTIONS && it.text.isVisibleOperation() }
                    .groupBy { it.index + 1 }
                    .mapValues { (_, entries) -> entries.mapTo(linkedSetOf(), EntityOps.SubOp::subId) },
            conditionalOptions =
                conditionalOps
                    .groupBy { it.index + 1 }
                    .mapValues { (_, entries) ->
                        entries.map { operation -> conditionalOperation(operation, varbitsById) }
                    },
            conditionalSubOptions =
                conditionalSubOps
                    .filter { it.subId in WIRE_SUB_OPTIONS }
                    .groupBy { it.index + 1 }
                    .mapValues { (_, entries) ->
                        entries
                            .groupBy(EntityOps.ConditionalSubOp::subId)
                            .mapValues { (_, alternatives) ->
                                alternatives.map { operation ->
                                    conditionalOperation(operation, varbitsById)
                                }
                            }
                    },
        )

    private fun conditionalOperation(
        operation: EntityOps.ConditionalOp,
        varbitsById: Map<Int, VarbitDefinition>,
    ): NpcConditionalOperation =
        NpcConditionalOperation(
            variable = variable(operation.varp, operation.varb, varbitsById),
            values = operation.min..operation.max,
            visible = operation.text.isVisibleOperation(),
        )

    private fun conditionalOperation(
        operation: EntityOps.ConditionalSubOp,
        varbitsById: Map<Int, VarbitDefinition>,
    ): NpcConditionalOperation =
        NpcConditionalOperation(
            variable = variable(operation.varp, operation.varb, varbitsById),
            values = operation.min..operation.max,
            visible = operation.text.isVisibleOperation(),
        )

    private fun VarTransform.toNpcTransform(
        varbitsById: Map<Int, VarbitDefinition>,
    ): NpcTransform =
        NpcTransform(
            variable = variable(varpId, varbitId, varbitsById, nullSentinel = -1),
            destinations = configChangeDest + (trailingVar ?: -1),
        )

    private fun variable(
        varp: Int,
        varbit: Int,
        varbitsById: Map<Int, VarbitDefinition>,
        nullSentinel: Int = 0xFFFF,
    ): PlayerVariable =
        if (varbit != nullSentinel) {
            val type = requireNotNull(varbitsById[varbit]) { "cache varbit $varbit is missing" }
            PlayerVariable.Varbit(type.id, type.baseVar, type.bits)
        } else {
            require(varp != nullSentinel) { "cache NPC variable has neither a varp nor a varbit" }
            PlayerVariable.Varp(varp)
        }

    private fun String.isVisibleOperation(): Boolean = isNotEmpty() && !equals(HIDDEN, ignoreCase = true)

    private companion object {
        const val MAX_NPC_TYPE = NpcType.MAX_ID
        const val DEFAULT_SIZE = 1
        const val HIDDEN = "Hidden"
        val WIRE_SUB_OPTIONS = 1..0xFF
    }
}
