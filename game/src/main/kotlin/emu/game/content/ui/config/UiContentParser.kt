package emu.game.content.ui.config

import emu.game.content.ui.gameframe.GameframeParser
import org.tomlj.Toml

/** Parses all revision-pinned UI content from one TOML document. */
object UiContentParser {
    fun parse(source: String): UiContent {
        val result = Toml.parse(source)
        require(!result.hasErrors()) {
            result.errors().joinToString(prefix = "invalid UI content TOML: ")
        }
        val components = requireNotNull(result.getTable(COMPONENT_TABLE)) {
            "UI content TOML is missing [$COMPONENT_TABLE]"
        }
        val gameframe = requireNotNull(result.getTable(GAMEFRAME_TABLE)) {
            "UI content TOML is missing [$GAMEFRAME_TABLE]"
        }
        return UiContent(
            components = UiComponentMap.from(components),
            clientScripts = UiClientScriptMap.from(result.getTable(CLIENT_SCRIPT_TABLE)),
            clientConstants = UiClientConstantMap.from(result.getTable(CLIENT_CONSTANT_TABLE)),
            gameframe = GameframeParser.from(gameframe),
        )
    }

    private const val COMPONENT_TABLE = "components"
    private const val CLIENT_SCRIPT_TABLE = "client_scripts"
    private const val CLIENT_CONSTANT_TABLE = "client_constants"
    private const val GAMEFRAME_TABLE = "gameframe"
}
