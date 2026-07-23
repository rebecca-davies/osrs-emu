package emu.game.content.areas.inferno

/** Component names composing the rev-239 Inferno editor presentation. */
internal object InfernoEditorLayout {
    const val LAUNCHER_DESTINATION = "toplevel_osrs_stretch:side2"
    const val MODAL_DESTINATION = "toplevel_osrs_stretch:mainmodal"
    const val LAUNCHER_ROOT = "poh_options:title"
    const val LAUNCHER_TITLE = "poh_options:title"
    const val LAUNCHER_STATE = "poh_options:building_mode"
    const val LAUNCHER_SELECTION = "poh_options:teleport_inside"
    const val LAUNCHER_OPEN_LABEL = "poh_options:default_building_mode"
    const val LAUNCHER_OPEN_BUTTON = "poh_options:default_building_mode_on"
    const val CONTROLS_ROOT = "bookofscrolls:title"
    const val CONTROLS_TITLE = "bookofscrolls:title"

    val launcherUnused =
        listOf(
            "poh_options:viewer",
            "poh_options:on_text",
            "poh_options:off_text",
            "poh_options:building_mode_on",
            "poh_options:building_mode_off",
            "poh_options:teleport_inside_on",
            "poh_options:teleport_inside_off",
            "poh_options:default_building_mode_off",
            "poh_options:doors",
            "poh_options:doors_closed",
            "poh_options:icon_doors_closed",
            "poh_options:icon_doors_open",
            "poh_options:doors_open",
            "poh_options:icon_doors_none",
            "poh_options:doors_none",
            "poh_options:expel_guests",
            "poh_options:leave_house",
            "poh_options:call_servant",
            "poh_options:footer",
            "poh_options:close",
        )

    val npcCards =
        listOf(
            card("nardah"),
            card("digsite"),
            card("feldip"),
            card("lunarisle", title = "lunar"),
            card("mortton"),
            card("pestcontrol"),
            card("piscatoris"),
            card("taibwo"),
        )

    val gearCard = card("elf")
    val placeCard = card("mosles")
    val pauseCard = card("lumberyard")
    val clearCard = card("zulandra")
    val resetCard = card("cerberus")
    val leaveCard = card("revenants")

    val unusedCards =
        listOf(
            "bookofscrolls:teleportscroll_watson",
            "bookofscrolls:teleportscroll_guthixian_temple",
            "bookofscrolls:teleportscroll_spidercave",
            "bookofscrolls:teleportscroll_colossal_wyrm",
            "bookofscrolls:teleportscroll_chasmoffire",
        )

    private fun card(name: String, title: String = name): InfernoEditorCardNames =
        InfernoEditorCardNames(
            button = "bookofscrolls:teleportscroll_$name",
            status = "bookofscrolls:text_$name",
            label = "bookofscrolls:title_$title",
        )
}

/** Component names for one selectable editor card. */
internal data class InfernoEditorCardNames(
    val button: String,
    val status: String,
    val label: String,
)
