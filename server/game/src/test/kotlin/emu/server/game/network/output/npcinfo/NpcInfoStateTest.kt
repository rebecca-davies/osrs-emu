package emu.server.game.network.output.npcinfo

import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcList
import emu.game.npc.NpcType
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import emu.protocol.osrs239.game.message.npc.NpcSequence
import emu.protocol.osrs239.game.message.entity.InfoHeadbar
import emu.protocol.osrs239.game.message.entity.InfoHitmarkType
import emu.protocol.osrs239.game.message.entity.InfoSpotAnimation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
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
        assertEquals(listOf(NpcInfoLocal.Walk.EAST), state.next(view(npcs), observer, instance).locals)
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

    @Test
    fun `private NPC additions retain zone priority before client index`() {
        val instance = MapInstance.privateTo(1)
        val observer = Tile(100, 100)
        val type = NpcType(1, "Jal-Nib")
        val npcs = NpcList(capacity = 2)
        val eastZone = requireNotNull(npcs.add(type, Tile(108, 100), instance))
        val observerZone = requireNotNull(npcs.add(type, Tile(101, 100), instance))

        val additions = NpcInfoState().next(view(npcs), observer, instance).additions

        assertEquals(listOf(observerZone.index, eastZone.index), additions.map(NpcInfoAddition::index))
    }

    @Test
    fun `NPC combat visuals are retained for the information phase and cleared after cleanup`() {
        val instance = MapInstance.privateTo(1)
        val npcs = NpcList(capacity = 1)
        val npc = requireNotNull(npcs.add(NpcType(1, "Jal-Nib"), Tile(101, 100), instance))
        npc.playAnimation(7_574, delay = 1)
        npc.showHitmark(8, delay = 2)
        npc.showHealthBar(current = 12, maximum = 20)
        npc.playSpotAnimation(id = 1_376, delay = 3, height = 4)

        val addition = NpcInfoState().next(view(npcs), Tile(100, 100), instance).additions.single()
        val update = requireNotNull(addition.update)

        assertEquals(NpcSequence(7_574, 1), update.sequence)
        assertEquals(InfoHitmarkType.DAMAGE_OTHER, update.hitmarks.single().type)
        assertEquals(8, update.hitmarks.single().value)
        assertEquals(InfoHeadbar(type = 0, startFill = 18), update.headbars.single())
        assertEquals(InfoSpotAnimation(slot = 0, id = 1_376, height = 4, delay = 3), update.spotAnimations.single())

        npc.finishCycle()
        assertEquals(null, NpcInfoState().next(view(npcs), Tile(100, 100), instance).additions.single().update)
    }

    @Test
    fun `one immutable NPC visual update is shared for the entire information snapshot`() {
        val instance = MapInstance.privateTo(1)
        val npcs = NpcList(capacity = 1)
        val npc = requireNotNull(npcs.add(NpcType(1, "Jal-Nib"), Tile(101, 100), instance))
        npc.showHitmark(8)
        val view = view(npcs)

        val first = NpcInfoState().next(view, Tile(100, 100), instance).additions.single().update
        npc.showHitmark(9)
        val second = NpcInfoState().next(view, Tile(100, 100), instance).additions.single().update

        assertSame(first, second)
        assertEquals(listOf(8), requireNotNull(second).hitmarks.map { it.value })
    }

    @Test
    fun `local uid remains stale until a replacement has been published`() {
        val instance = MapInstance.privateTo(1)
        val npcs = NpcList(capacity = 1)
        val type = NpcType(1, "Jal-Nib")
        val first = requireNotNull(npcs.add(type, Tile(101, 100), instance))
        val state = NpcInfoState()
        state.next(view(npcs), Tile(100, 100), instance)
        val firstUid = requireNotNull(state.resolveUid(first.index))

        assertTrue(npcs.remove(first))
        val replacement = requireNotNull(npcs.add(type, Tile(101, 100), instance))

        assertEquals(firstUid, state.resolveUid(replacement.index))
        assertNull(npcs.resolve(firstUid))
        state.next(view(npcs), Tile(100, 100), instance)
        assertEquals(replacement.uid, requireNotNull(state.resolveUid(replacement.index)).value)
    }

    private fun view(npcs: NpcList): NpcInfoView {
        val active = mutableListOf<Npc>()
        npcs.collect(active)
        return NpcInfoView(active)
    }
}
