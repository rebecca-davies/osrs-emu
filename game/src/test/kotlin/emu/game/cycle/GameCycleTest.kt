package emu.game.cycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameCycleTest {
    @Test
    fun `phases run in authentic order regardless of registration order`() {
        val calls = mutableListOf<String>()
        val processes =
            CyclePhase.entries
                .reversed()
                .map { phase -> CycleProcess(phase) { tick -> calls += "$tick:$phase" } }
        val cycle = GameCycle(processes)

        val processedTick = cycle.tick()

        assertEquals(0L, processedTick)
        assertEquals(
            CyclePhase.entries.map { "0:$it" },
            calls,
        )
        assertEquals(1L, cycle.currentTick)
    }

    @Test
    fun `processes in the same phase retain registration order`() {
        val calls = mutableListOf<String>()
        val cycle =
            GameCycle(
                listOf(
                    CycleProcess(CyclePhase.PLAYER) { calls += "first" },
                    CycleProcess(CyclePhase.PLAYER) { calls += "second" },
                ),
            )

        cycle.tick()

        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun `an authoritative world tick is passed through to every phase`() {
        val observedTicks = mutableListOf<Long>()
        val cycle =
            GameCycle(
                CyclePhase.entries.map { phase ->
                    CycleProcess(phase) { tick -> observedTicks += tick }
                },
            )

        val processedTick = cycle.tick(731L)

        assertEquals(731L, processedTick)
        assertEquals(List(CyclePhase.entries.size) { 731L }, observedTicks)
        assertEquals(732L, cycle.currentTick)
    }

    @Test
    fun `failed phase stops the cycle without advancing its clock`() {
        val calls = mutableListOf<CyclePhase>()
        val cycle =
            GameCycle(
                CyclePhase.entries.map { phase ->
                    CycleProcess(phase) {
                        calls += phase
                        if (phase == CyclePhase.NPC) error("broken npc")
                    }
                },
            )

        assertFailsWith<IllegalStateException> { cycle.tick() }
        assertEquals(CyclePhase.entries.takeWhile { it != CyclePhase.PLAYER }, calls)
        assertEquals(0L, cycle.currentTick)
    }
}
