package emu.cache.def

import emu.cache.def.codec.ItemDefCodec
import emu.cache.store.Store

/** Eagerly decoded rev-239 item definitions, kept off the game-cycle hot path. */
class CacheItemDefinitionCatalog(store: Store) {
    val definitions: List<ItemDefinition>
    private val byId: Array<ItemDefinition?>

    init {
        val encoded = ConfigDefinitionGroup.read(store, DefinitionType.ITEM)
        definitions = encoded.map { (id, data) -> ItemDefCodec.decode(id, data) }
        val highestId = definitions.maxOfOrNull(ItemDefinition::id) ?: -1
        byId = arrayOfNulls(highestId + 1)
        for (definition in definitions) byId[definition.id] = definition
    }

    operator fun get(id: Int): ItemDefinition? = byId.getOrNull(id)
}
