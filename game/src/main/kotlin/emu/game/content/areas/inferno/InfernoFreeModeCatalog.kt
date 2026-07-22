package emu.game.content.areas.inferno

/** Loads the bundled revision-pinned Inferno free-mode content once per process. */
object InfernoFreeModeCatalog {
    private val config: InfernoFreeModeConfig by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream(RESOURCE)) {
            "bundled Inferno free-mode content is missing: $RESOURCE"
        }
        stream.bufferedReader().use { InfernoFreeModeConfigParser.parse(it.readText()) }
    }

    fun load(): InfernoFreeModeConfig = config

    private const val RESOURCE = "/emu/game/content/areas/inferno/free_mode.toml"
}
