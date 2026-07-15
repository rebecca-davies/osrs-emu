package emu.server.world.runtime

import emu.game.cycle.CyclePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldCyclePlanCompilerTest {
    @Test
    fun `plan uses phase and explicit order instead of contribution order`() {
        val calls = mutableListOf<String>()
        val plan =
            WorldCyclePlanCompiler.compile(
                listOf(
                    recordingSystem("output", CyclePhase.CLIENT_OUTPUT, 10, calls),
                    recordingSystem("player-late", CyclePhase.PLAYER, 20, calls),
                    recordingSystem("input", CyclePhase.CLIENT_INPUT, 10, calls),
                    recordingSystem("player-early", CyclePhase.PLAYER, 10, calls),
                ),
            )

        plan.execute(WorldTick(73))

        assertEquals(
            listOf("73:input", "73:player-early", "73:player-late", "73:output"),
            calls,
        )
    }

    @Test
    fun `duplicate system ids are rejected`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                WorldCyclePlanCompiler.compile(
                    listOf(
                        recordingSystem("player", CyclePhase.PLAYER, 10),
                        recordingSystem("player", CyclePhase.CLEANUP, 10),
                    ),
                )
            }

        assertTrue(failure.message.orEmpty().contains("duplicate world system id 'player'"))
    }

    @Test
    fun `duplicate phase order slots are rejected`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                WorldCyclePlanCompiler.compile(
                    listOf(
                        recordingSystem("movement", CyclePhase.PLAYER, 20),
                        recordingSystem("timers", CyclePhase.PLAYER, 20),
                    ),
                )
            }

        assertTrue(failure.message.orEmpty().contains("duplicate world system slot PLAYER:20"))
    }

    @Test
    fun `missing required phases are rejected`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                WorldCyclePlanCompiler.compile(
                    systems = listOf(recordingSystem("input", CyclePhase.CLIENT_INPUT, 10)),
                    requiredPhases = setOf(CyclePhase.CLIENT_INPUT, CyclePhase.CLEANUP),
                )
            }

        assertTrue(failure.message.orEmpty().contains("required world phases have no systems: CLEANUP"))
    }

    @Test
    fun `world ticks cannot be negative`() {
        assertFailsWith<IllegalArgumentException> { WorldTick(-1) }
    }

    private fun recordingSystem(
        id: String,
        phase: CyclePhase,
        order: Int,
        calls: MutableList<String> = mutableListOf(),
    ): WorldSystem =
        object : WorldSystem {
            override val id = WorldSystemId(id)
            override val phase = phase
            override val order = order

            override fun execute(tick: WorldTick) {
                calls += "${tick.value}:$id"
            }
        }
}
