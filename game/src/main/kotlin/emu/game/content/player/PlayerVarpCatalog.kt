package emu.game.content.player

/** Revision-pinned player variables referenced by game content. */
object PlayerVarpCatalog {
    private val definitions = PlayerVariableDefinitions.load()

    /** `0` is walk and `1` is run; the client reserves `2` for crawl. */
    val RUN_MODE = definitions.requireVarp("option_run")

    /** One-based Tournament Supplies load-out selection; zero means no selection. */
    val TOURNAMENT_LOADOUT = definitions.requireVarp("tournament_loadout")

    /** Client transmitter set once the account has a display name. */
    val HAS_DISPLAY_NAME = definitions.requireVarbit("has_display_name")

    /** Complete varp schema for a player in this content build. */
    val ALL = definitions.catalog
}
