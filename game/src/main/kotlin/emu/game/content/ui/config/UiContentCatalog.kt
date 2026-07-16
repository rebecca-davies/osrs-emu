package emu.game.content.ui.config

/** Loads the bundled revision-pinned UI content once per process. */
object UiContentCatalog {
    private val content: UiContent by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream(RESOURCE)) {
            "bundled UI content is missing: $RESOURCE"
        }
        stream.bufferedReader().use { UiContentParser.parse(it.readText()) }
    }

    fun load(): UiContent = content

    private const val RESOURCE = "/emu/game/content/ui/components.toml"
}
