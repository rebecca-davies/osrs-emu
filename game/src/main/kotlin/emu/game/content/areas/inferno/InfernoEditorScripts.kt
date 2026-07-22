package emu.game.content.areas.inferno

import emu.game.script.content.PlayerContent

/** Equipment-tab controls for editing one private Inferno free-mode instance. */
object InfernoEditorScripts {
    fun register(content: PlayerContent, arena: InfernoArena) {
        content.onButton(PLACE_NPC_BUTTON) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            if (!arena.isActive(player)) {
                player.messageGame("Enter the Inferno before placing NPCs.")
                return@onButton
            }
            val type = numberDialog("Enter an NPC ID:")
            val selection =
                when (val selected = arena.selectNpc(player, type)) {
                    is InfernoNpcSelection.Selected -> selected
                    InfernoNpcSelection.NotInArena -> {
                        player.messageGame("Enter the Inferno before placing NPCs.")
                        return@onButton
                    }
                    InfernoNpcSelection.UnknownType -> {
                        player.messageGame("That NPC is not available in this beta world.")
                        return@onButton
                    }
                }
            val tile = pickTile("Click a tile to place ${selection.type.name}. Press Escape to cancel.")
            val message =
                when (arena.place(player, selection, tile)) {
                    InfernoNpcPlacement.PLACED -> "Placed ${selection.type.name}; the simulation is paused."
                    InfernoNpcPlacement.NOT_IN_ARENA -> "Enter the Inferno before placing NPCs."
                    InfernoNpcPlacement.OUTSIDE_ARENA -> "That NPC would be outside the Inferno arena."
                    InfernoNpcPlacement.BLOCKED -> "That NPC would collide with the arena."
                    InfernoNpcPlacement.OCCUPIED -> "That tile is already occupied."
                    InfernoNpcPlacement.INSTANCE_CAPACITY -> "This Inferno instance has reached its NPC limit."
                    InfernoNpcPlacement.WORLD_CAPACITY -> "The game world has reached its NPC limit."
                }
            player.messageGame(message)
        }
        content.onButton(TOGGLE_PAUSE_BUTTON) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            player.messageGame(
                when (arena.togglePaused(player)) {
                    InfernoPauseResult.PAUSED -> "Inferno simulation paused."
                    InfernoPauseResult.RESUMED -> "Inferno simulation resumed."
                    InfernoPauseResult.NOT_IN_ARENA -> "Enter the Inferno before changing the simulation."
                },
            )
        }
        content.onButton(CLEAR_NPCS_BUTTON) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            val removed = arena.clear(player)
            player.messageGame(
                when (removed) {
                    null -> "Enter the Inferno before clearing NPCs."
                    0 -> "There are no NPCs to clear."
                    else -> "Cleared $removed NPC${if (removed == 1) "" else "s"}."
                },
            )
        }
    }

    private const val PLACE_NPC_BUTTON = "wornitems:pricechecker"
    private const val TOGGLE_PAUSE_BUTTON = "wornitems:deathkeep"
    private const val CLEAR_NPCS_BUTTON = "wornitems:call_follower"
}
