package emu.game.content.areas.inferno

import emu.game.script.content.PlayerContent

/** Registers controls for editing one player's private Inferno simulation. */
object InfernoEditorScripts {
    fun register(
        content: PlayerContent,
        arena: InfernoArena,
        editor: InfernoEditorInterface,
    ) {
        content.onButton(InfernoEditorLayout.LAUNCHER_OPEN_BUTTON) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            val state = arena.editorState(player)
            if (state == null) {
                editor.restoreQuestTab(player)
                player.messageGame("Enter the Inferno before opening the editor.")
            } else {
                editor.openControls(player, state)
            }
        }

        arena.config.editorRoster.entries.forEachIndexed { index, _ ->
            content.onButton(InfernoEditorLayout.npcCards[index].button) {
                if (lastButton?.isPrimaryComponentClick != true) return@onButton
                val previous = arena.editorState(player)?.selectedNpcIndex ?: return@onButton
                when (arena.selectNpcAt(player, index)) {
                    is InfernoNpcSelection.Selected -> {
                        arena.editorState(player)?.let { editor.refreshSelection(player, previous, it) }
                    }
                    InfernoNpcSelection.NotInArena -> {
                        editor.close(player)
                        player.messageGame("Enter the Inferno before selecting NPCs.")
                    }
                    InfernoNpcSelection.UnknownType -> {
                        player.messageGame("That NPC is not available in this beta world.")
                    }
                }
            }
        }

        content.onButton(InfernoEditorLayout.placeCard.button) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            val state = arena.editorState(player)
            if (state == null) {
                editor.close(player)
                player.messageGame("Enter the Inferno before placing NPCs.")
                return@onButton
            }
            editor.closeControls(player)
            val tile =
                pickTile(
                    "Click a tile to place ${state.selectedNpc.displayName}. Press Escape to cancel.",
                )
            val message =
                when (arena.placeSelected(player, tile)) {
                    InfernoNpcPlacement.PLACED ->
                        "Placed ${state.selectedNpc.displayName}; the simulation is paused."
                    InfernoNpcPlacement.NOT_IN_ARENA -> "Enter the Inferno before placing NPCs."
                    InfernoNpcPlacement.OUTSIDE_ARENA -> "That NPC would be outside the Inferno arena."
                    InfernoNpcPlacement.BLOCKED -> "That NPC would collide with the arena."
                    InfernoNpcPlacement.OCCUPIED -> "That tile is already occupied."
                    InfernoNpcPlacement.INSTANCE_CAPACITY ->
                        "This Inferno instance has reached its NPC limit."
                    InfernoNpcPlacement.WORLD_CAPACITY -> "The game world has reached its NPC limit."
                    InfernoNpcPlacement.UNKNOWN_TYPE ->
                        "That NPC is not available in this beta world."
                }
            player.messageGame(message)
            arena.editorState(player)?.let { editor.openControls(player, it) }
        }

        content.onButton(InfernoEditorLayout.pauseCard.button) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            player.messageGame(
                when (arena.togglePaused(player)) {
                    InfernoPauseResult.PAUSED -> "Inferno simulation paused."
                    InfernoPauseResult.RESUMED -> "Inferno simulation resumed."
                    InfernoPauseResult.NOT_IN_ARENA -> "Enter the Inferno before changing the simulation."
                },
            )
            arena.editorState(player)?.let { editor.refreshState(player, it) }
        }

        content.onButton(InfernoEditorLayout.clearCard.button) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            val removed = arena.clear(player)
            player.messageGame(
                when (removed) {
                    null -> "Enter the Inferno before clearing NPCs."
                    0 -> "There are no NPCs to clear."
                    else -> "Cleared $removed NPC${if (removed == 1) "" else "s"}."
                },
            )
            if (removed != null) {
                arena.editorState(player)?.let { editor.refreshState(player, it) }
            }
        }

        content.onButton(InfernoEditorLayout.resetCard.button) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            val previous = arena.editorState(player)
            val state = arena.reset(player)
            if (state == null) {
                editor.close(player)
                player.messageGame("Enter the Inferno before resetting the simulation.")
            } else {
                player.messageGame("Inferno simulation reset.")
                if (previous != state) {
                    editor.refreshState(player, state)
                    previous?.let { editor.refreshSelection(player, it.selectedNpcIndex, state) }
                }
            }
        }

        content.onButton(InfernoEditorLayout.leaveCard.button) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            editor.close(player)
            arena.enterHub(player)
            player.messageGame("Returned to the Clan Wars beta hub.")
        }
    }
}
