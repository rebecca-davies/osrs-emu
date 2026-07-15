package emu.server.host

import emu.game.cycle.CyclePhase
import emu.server.world.runtime.WorldCyclePlanCompiler
import emu.server.world.runtime.WorldSystem
import emu.server.world.runtime.WorldSystemId
import emu.server.world.runtime.WorldTick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

class WorldSystemModuleTest {
    @Test
    fun `qualified systems are collected and ordered independently of bindings`() {
        val calls = mutableListOf<String>()
        val systems =
            worldSystemModule {
                worldSystem("output-binding") {
                    RecordingSystem("output", CyclePhase.CLIENT_OUTPUT, 10, calls)
                }
                worldSystem("input-binding") {
                    RecordingSystem("input", CyclePhase.CLIENT_INPUT, 10, calls)
                }
            }
        val application =
            koinApplication {
                allowOverride(false)
                modules(systems)
            }

        val contributions = application.koin.getAll<WorldSystem>()
        WorldCyclePlanCompiler.compile(contributions).execute(WorldTick(9))

        assertEquals(listOf("9:input", "9:output"), calls)
        application.close()
    }

    @Test
    fun `duplicate binding keys fail before koin module construction`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                worldSystemModule {
                    worldSystem("player") {
                        RecordingSystem("first", CyclePhase.PLAYER, 10, mutableListOf())
                    }
                    worldSystem("player") {
                        RecordingSystem("second", CyclePhase.PLAYER, 20, mutableListOf())
                    }
                }
            }

        assertTrue(failure.message.orEmpty().contains("duplicate world system binding key 'player'"))
    }

    private class RecordingSystem(
        id: String,
        override val phase: CyclePhase,
        override val order: Int,
        private val calls: MutableList<String>,
    ) : WorldSystem {
        override val id = WorldSystemId(id)

        override fun execute(tick: WorldTick) {
            calls += "${tick.value}:${id.value}"
        }
    }
}
