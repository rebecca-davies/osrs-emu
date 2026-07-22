package emu.server.game.network.output.npcinfo

import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcList
import emu.game.npc.NpcType
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcInfoStateTest {
    @Test
    fun `local identity is retained removed and safely replaced at a reused index`() {
        val instance = MapInstance.privateTo(1)
        val observer = Tile(100, 100)
        val type = NpcType(1, "Jal-Nib")
        val npcs = NpcList(capacity = 1)
        val first = requireNotNull(npcs.add(type, Tile(101, 100), instance))
        val state = NpcInfoState()

        val addition = state.next(view(npcs), observer, instance)
        assertEquals(listOf(NpcInfoAddition(first.index, type.id, 1, 0, 6)), addition.additions)
        assertTrue(addition.locals.isEmpty())

        first.walkTo(Tile(102, 100))
        assertEquals(listOf(NpcInfoLocal.Walk(4)), state.next(view(npcs), observer, instance).locals)
        first.finishCycle()
        assertEquals(listOf(NpcInfoLocal.Idle), state.next(view(npcs), observer, instance).locals)

        assertTrue(npcs.remove(first))
        val replacement = requireNotNull(npcs.add(type, Tile(102, 100), instance))
        val replaced = state.next(view(npcs), observer, instance)

        assertEquals(listOf(NpcInfoLocal.Remove), replaced.locals)
        assertEquals(listOf(NpcInfoAddition(replacement.index, type.id, 2, 0, 6)), replaced.additions)
    }

    @Test
    fun `private NPC additions are capped and hidden from other instances`() {
        val instance = MapInstance.privateTo(1)
        val npcs = NpcList(capacity = 251)
        val type = NpcType(1, "Jal-Nib")
        repeat(251) {
            requireNotNull(npcs.add(type, Tile(101, 100), instance))
        }

        val visible = NpcInfoState().next(view(npcs), Tile(100, 100), instance)
        val isolated = NpcInfoState().next(view(npcs), Tile(100, 100), MapInstance.privateTo(2))

        assertEquals(250, visible.additions.size)
        assertTrue(isolated.locals.isEmpty())
        assertTrue(isolated.additions.isEmpty())
    }

    private fun view(npcs: NpcList): NpcInfoView {
        val active = mutableListOf<Npc>()
        npcs.collect(active)
        return NpcInfoView(active)
    }
}
