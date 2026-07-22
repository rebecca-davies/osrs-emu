package emu.server.game.world.cycle

import emu.game.npc.Npc
import emu.server.game.world.World
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Advances each world NPC once and retains movement until the information phase has published it. */
internal class NpcPhase(private val world: World) {
    private val processed = ArrayList<Npc>()

    fun run() {
        finish()
        world.collectNpcs(processed)
        for (npc in processed) {
            try {
                world.advanceNpc(npc)
            } catch (failure: Exception) {
                world.removeNpc(npc)
                logger.error(failure) { "world: NPC ${npc.index} failed during processing and was removed" }
            }
        }
    }

    fun finish() {
        for (npc in processed) npc.finishCycle()
        processed.clear()
    }
}
