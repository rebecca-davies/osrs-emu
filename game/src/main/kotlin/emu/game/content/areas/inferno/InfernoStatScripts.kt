package emu.game.content.areas.inferno

import emu.game.player.stat.Skill
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScriptContext

/** Beta-world stat editing through the revision-239 skills tab. */
object InfernoStatScripts {
    fun register(content: PlayerContent) {
        for ((component, skill) in SKILL_BUTTONS) {
            content.onButton(component) {
                if (lastButton?.isPrimaryComponentClick != true) return@onButton
                editLevel(skill)
            }
        }
    }

    private suspend fun PlayerScriptContext.editLevel(skill: Skill) {
        val level = numberDialog("Set ${skill.displayName} level (1-99):")
        if (level !in 1..99) {
            player.messageGame("Choose a level from 1 to 99.")
            return
        }
        player.stats.setLevel(skill, level)
    }

    private val SKILL_BUTTONS =
        linkedMapOf(
            "stats:attack" to Skill.ATTACK,
            "stats:strength" to Skill.STRENGTH,
            "stats:defence" to Skill.DEFENCE,
            "stats:ranged" to Skill.RANGED,
            "stats:prayer" to Skill.PRAYER,
            "stats:magic" to Skill.MAGIC,
            "stats:runecraft" to Skill.RUNECRAFT,
            "stats:construction" to Skill.CONSTRUCTION,
            "stats:hitpoints" to Skill.HITPOINTS,
            "stats:agility" to Skill.AGILITY,
            "stats:herblore" to Skill.HERBLORE,
            "stats:thieving" to Skill.THIEVING,
            "stats:crafting" to Skill.CRAFTING,
            "stats:fletching" to Skill.FLETCHING,
            "stats:slayer" to Skill.SLAYER,
            "stats:hunter" to Skill.HUNTER,
            "stats:mining" to Skill.MINING,
            "stats:smithing" to Skill.SMITHING,
            "stats:fishing" to Skill.FISHING,
            "stats:cooking" to Skill.COOKING,
            "stats:firemaking" to Skill.FIREMAKING,
            "stats:woodcutting" to Skill.WOODCUTTING,
            "stats:farming" to Skill.FARMING,
        )
}
