package emu.server.game.network.output.stat

import emu.game.content.player.Skill
import emu.protocol.osrs239.game.message.player.UpdateStat

/** Builds the initial stat messages for a new revision-239 character. */
internal object PlayerStatOutput {
    fun initialMessages(): List<UpdateStat> =
        Skill.entries.map { skill ->
            UpdateStat(
                stat = skill.id,
                currentLevel = skill.initialLevel,
                invisibleBoostedLevel = skill.initialLevel,
                experience = skill.initialExperience,
            )
        }
}
