package emu.game.content.player

import emu.game.varp.VarpCatalog
import emu.game.varp.VarbitType
import emu.game.varp.VarpScope
import emu.game.varp.VarpTransmit
import emu.game.varp.VarpType
import org.tomlj.Toml

/** Named revision metadata loaded from the content RSCM export at startup. */
internal class PlayerVariableDefinitions private constructor(
    private val varps: Map<String, VarpType>,
    private val varbits: Map<String, VarbitType>,
) {
    val catalog = VarpCatalog(*varps.values.toTypedArray())

    fun requireVarp(name: String): VarpType =
        requireNotNull(varps[name]) { "player varp mapping is missing: $name" }

    fun requireVarbit(name: String): VarbitType =
        requireNotNull(varbits[name]) { "player varbit mapping is missing: $name" }

    companion object {
        fun load(): PlayerVariableDefinitions {
            val source =
                checkNotNull(PlayerVariableDefinitions::class.java.getResourceAsStream(RESOURCE)) {
                    "player variable metadata is missing: $RESOURCE"
                }.bufferedReader().use { it.readText() }
            val result = Toml.parse(source)
            require(!result.hasErrors()) { result.errors().joinToString(prefix = "invalid player variable TOML: ") }
            val varpTable = requireNotNull(result.getTable("varps")) { "player variable TOML is missing [varps]" }
            val varps =
                varpTable.keySet().associateWith { name ->
                    val table = requireNotNull(varpTable.getTable(name))
                    VarpType(
                        id = Math.toIntExact(requireNotNull(table.getLong("id"))),
                        scope = VarpScope.valueOf(requireNotNull(table.getString("scope")).uppercase()),
                        transmit = VarpTransmit.valueOf(requireNotNull(table.getString("transmit")).uppercase()),
                    )
                }
            val varbitTable = requireNotNull(result.getTable("varbits")) { "player variable TOML is missing [varbits]" }
            val varbits =
                varbitTable.keySet().associateWith { name ->
                    val table = requireNotNull(varbitTable.getTable(name))
                    val baseName = requireNotNull(table.getString("base"))
                    VarbitType(
                        id = Math.toIntExact(requireNotNull(table.getLong("id"))),
                        baseVar = requireNotNull(varps[baseName]) { "unknown varbit base: $baseName" },
                        bits =
                            Math.toIntExact(requireNotNull(table.getLong("least_bit")))..
                                Math.toIntExact(requireNotNull(table.getLong("most_bit"))),
                    )
                }
            return PlayerVariableDefinitions(varps, varbits)
        }

        private const val RESOURCE = "/emu/game/content/player/player_variables.toml"
    }
}
