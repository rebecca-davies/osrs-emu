package emu.server.game.world.player.route

import emu.server.game.config.RouteSearchConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteSearchBudgetTest {
    @Test
    fun `budget admits exactly its configured number of searches each cycle`() {
        val budget = RouteSearchBudget(RouteSearchConfig(maxPerCycle = 32))
        budget.beginCycle()
        val firstCycle = List(40) { budget.acquire() }
        budget.beginCycle()
        val secondCycle = List(40) { budget.acquire() }

        assertEquals(List(32) { true } + List(8) { false }, firstCycle)
        assertEquals(firstCycle, secondCycle)
    }
}
