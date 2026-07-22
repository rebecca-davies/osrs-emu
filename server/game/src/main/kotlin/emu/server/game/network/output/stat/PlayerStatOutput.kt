package emu.server.game.network.output.stat

import emu.game.player.stat.SkillStat
import emu.protocol.osrs239.game.message.player.UpdateStat

/** Maps authoritative skill state to revision-239 stat messages. */
internal object PlayerStatOutput {
    fun messages(stats: List<SkillStat>): List<UpdateStat> = stats.map(::message)

    fun message(stat: SkillStat): UpdateStat =
        UpdateStat(
            stat = stat.skill.id,
            currentLevel = stat.currentLevel,
            invisibleBoostedLevel = stat.baseLevel,
            experience = stat.experience,
        )
}
