package emu.server.world

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.store.Store
import emu.netcore.codec.CodecRepository
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditSink
import emu.server.world.admission.GameAdmission
import emu.server.world.config.GameExecutionConfig
import emu.server.world.map.CacheCollisionMap
import emu.server.world.network.loadHuffmanCodec
import emu.server.world.runtime.WorldLifecycle
import emu.server.world.runtime.WorldRuntime
import kotlinx.coroutines.withContext

/** Assembles the in-process world service from host-owned assets and external capabilities. */
fun createWorldServer(
    store: Store,
    codecs: CodecRepository,
    characters: CharacterStore,
    chatAudit: ChatAuditSink,
    config: GameExecutionConfig = GameExecutionConfig(),
): WorldServer {
    val collision = CacheCollisionMap(CacheMapRepository(store), CacheObjectDefinitionRepository(store))
    val huffman = loadHuffmanCodec(store)
    val dispatchers = WorldServerDispatchers(config.ioWorkerThreads)
    return try {
        val world = WorldRuntime(maxPlayerIndex = config.maxConcurrentSessions)
        val lifecycle = WorldLifecycle(dispatchers.world, world::run)
        val admissions =
            GameAdmission(world, config.maxConcurrentSessions) { accountId ->
                withContext(dispatchers.io) { characters.load(accountId) }
            }
        InProcessWorldServer(
            codecs = codecs,
            characters = characters,
            chatAudit = chatAudit,
            config = config,
            world = world,
            collision = collision,
            huffman = huffman,
            admissions = admissions,
            worldLifecycle = lifecycle,
            dispatchers = dispatchers,
        )
    } catch (failure: Throwable) {
        dispatchers.close()
        throw failure
    }
}
