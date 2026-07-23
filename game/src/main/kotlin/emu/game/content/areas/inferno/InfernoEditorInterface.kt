package emu.game.content.areas.inferno

import emu.game.content.ui.config.UiComponentMap
import emu.game.player.Player
import emu.game.ui.Component
import emu.game.ui.InterfaceLayout

/** Renders Inferno editor state with the configured cache-interface layouts. */
class InfernoEditorInterface(
    components: UiComponentMap,
    private val roster: InfernoEditorRoster,
) {
    private val launcherTitle = components.require(InfernoEditorLayout.LAUNCHER_TITLE)
    private val launcherState = components.require(InfernoEditorLayout.LAUNCHER_STATE)
    private val launcherSelection = components.require(InfernoEditorLayout.LAUNCHER_SELECTION)
    private val launcherOpenLabel = components.require(InfernoEditorLayout.LAUNCHER_OPEN_LABEL)
    private val launcherLayout =
        InterfaceLayout(
            destination = components.require(InfernoEditorLayout.LAUNCHER_DESTINATION),
            interfaceId = components.require(InfernoEditorLayout.LAUNCHER_ROOT).interfaceId,
            visibleComponents =
                listOf(
                    launcherTitle,
                    launcherState,
                    launcherSelection,
                    launcherOpenLabel,
                    components.require(InfernoEditorLayout.LAUNCHER_OPEN_BUTTON),
                ),
            hiddenComponents = InfernoEditorLayout.launcherUnused.map(components::require),
        )
    private val npcCards = InfernoEditorLayout.npcCards.map { it.resolve(components) }
    private val gearCard = InfernoEditorLayout.gearCard.resolve(components)
    private val placeCard = InfernoEditorLayout.placeCard.resolve(components)
    private val pauseCard = InfernoEditorLayout.pauseCard.resolve(components)
    private val clearCard = InfernoEditorLayout.clearCard.resolve(components)
    private val resetCard = InfernoEditorLayout.resetCard.resolve(components)
    private val leaveCard = InfernoEditorLayout.leaveCard.resolve(components)
    private val controlsTitle = components.require(InfernoEditorLayout.CONTROLS_TITLE)
    private val controlsLayout =
        InterfaceLayout(
            destination = components.require(InfernoEditorLayout.MODAL_DESTINATION),
            interfaceId = components.require(InfernoEditorLayout.CONTROLS_ROOT).interfaceId,
            visibleComponents =
                listOf(controlsTitle) +
                    npcCards.take(roster.entries.size).map(InfernoEditorCard::button) +
                    listOf(gearCard, placeCard, pauseCard, clearCard, resetCard, leaveCard)
                        .map(InfernoEditorCard::button),
            hiddenComponents =
                npcCards.drop(roster.entries.size).map(InfernoEditorCard::button) +
                    InfernoEditorLayout.unusedCards.map(components::require),
        )

    /** Replaces the quest tab with a compact launcher while the simulation is active. */
    fun openLauncher(player: Player, state: InfernoEditorState) {
        if (player.interfaces.replaceOverlay(launcherLayout)) {
            player.interfaces.setText(launcherTitle, "Inferno Editor")
            player.interfaces.setText(launcherOpenLabel, "Open editor")
        }
        player.interfaces.setText(launcherState, state.summary())
        player.interfaces.setText(launcherSelection, "NPC: ${state.selectedNpc.displayName}")
    }

    /** Restores the subtree displaced by this editor while its launcher still owns the tab. */
    fun restoreQuestTab(player: Player) {
        player.interfaces.restoreOverlay(launcherLayout)
    }

    /** Opens and fully initializes the free-mode card editor. */
    fun openControls(player: Player, state: InfernoEditorState) {
        if (controlsOpen(player)) {
            refreshState(player, state)
            return
        }
        openLauncher(player, state)
        player.interfaces.openModal(controlsLayout)
        player.interfaces.setText(controlsTitle, state.title())
        npcCards.take(roster.entries.size).forEachIndexed { index, card ->
            val npc = checkNotNull(roster[index])
            player.interfaces.setText(card.label, npc.displayName)
            player.interfaces.setText(
                card.status,
                if (index == state.selectedNpcIndex) SELECTED else SELECT,
            )
        }
        configureControl(player, gearCard, "Gear", "Choose equipment")
        configureControl(player, placeCard, "Place NPC", state.selectedNpc.displayName)
        configureControl(player, pauseCard, state.pauseAction(), "Simulation")
        configureControl(player, clearCard, "Clear NPCs", "Remove all")
        configureControl(player, resetCard, "Reset", "Empty and return")
        configureControl(player, leaveCard, "Leave", "Clan Wars")
    }

    /** Publishes current simulation state to each open editor surface. */
    fun refreshState(player: Player, state: InfernoEditorState) {
        if (player.interfaces.isOpen(launcherLayout)) {
            player.interfaces.setText(launcherState, state.summary())
            player.interfaces.setText(launcherSelection, "NPC: ${state.selectedNpc.displayName}")
        }
        if (!controlsOpen(player)) return
        player.interfaces.setText(controlsTitle, state.title())
        player.interfaces.setText(placeCard.status, state.selectedNpc.displayName)
        player.interfaces.setText(pauseCard.label, state.pauseAction())
    }

    /** Publishes a changed NPC selection to each open editor surface. */
    fun refreshSelection(player: Player, previousIndex: Int, state: InfernoEditorState) {
        if (previousIndex == state.selectedNpcIndex) return
        if (player.interfaces.isOpen(launcherLayout)) {
            player.interfaces.setText(launcherSelection, "NPC: ${state.selectedNpc.displayName}")
        }
        if (!controlsOpen(player)) return
        player.interfaces.setText(placeCard.status, state.selectedNpc.displayName)
        npcCards.getOrNull(previousIndex)?.let { player.interfaces.setText(it.status, SELECT) }
        npcCards.getOrNull(state.selectedNpcIndex)?.let {
            player.interfaces.setText(it.status, SELECTED)
        }
    }

    /** Closes the card editor while preserving the side-tab launcher. */
    fun closeControls(player: Player) {
        if (controlsOpen(player)) player.closeModal()
    }

    /** Closes editor presentation and restores the normal quest tab. */
    fun close(player: Player) {
        closeControls(player)
        restoreQuestTab(player)
    }

    private fun controlsOpen(player: Player): Boolean = player.interfaces.isOpen(controlsLayout)

    private fun configureControl(
        player: Player,
        card: InfernoEditorCard,
        label: String,
        status: String,
    ) {
        player.interfaces.setText(card.label, label)
        player.interfaces.setText(card.status, status)
    }

    private fun InfernoEditorCardNames.resolve(components: UiComponentMap): InfernoEditorCard =
        InfernoEditorCard(
            button = components.require(button),
            status = components.require(status),
            label = components.require(label),
        )

    private fun InfernoEditorState.summary(): String =
        "${if (paused) "Paused" else "Running"}: $npcCount/$maxNpcs " +
            "NPC${if (npcCount == 1) "" else "s"}"

    private fun InfernoEditorState.title(): String = "Inferno - ${summary()}"

    private fun InfernoEditorState.pauseAction(): String = if (paused) "Resume" else "Pause"

    private data class InfernoEditorCard(
        val button: Component,
        val status: Component,
        val label: Component,
    )

    private companion object {
        const val SELECT = "Select"
        const val SELECTED = "Selected"
    }
}
