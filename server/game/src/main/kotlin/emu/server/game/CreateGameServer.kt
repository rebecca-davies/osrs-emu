package emu.server.game

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.store.Store
import emu.netcore.codec.CodecRepository
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditSink
import emu.server.game.admission.GameAdmission
import emu.server.game.config.GameExecutionConfig
import emu.server.game.map.CacheCollisionMap
import emu.server.game.network.loadHuffmanCodec
import emu.server.game.world.WorldLifecycle
import emu.server.game.world.WorldRuntime
import kotlinx.coroutines.withContext

/** Assembles the in-process game service from host-owned assets and external capabilities. */
fun createGameServer(
    store: Store,
    codecs: CodecRepository,
    characters: CharacterStore,
    chatAudit: ChatAuditSink,
    config: GameExecutionConfig = GameExecutionConfig(),
): GameServer {
    val collision = CacheCollisionMap(CacheMapRepository(store), CacheObjectDefinitionRepository(store))
    val huffman = loadHuffmanCodec(store)
    val dispatchers = GameServerDispatchers(config.ioWorkerThreads)
    return try {
        val world = WorldRuntime(maxPlayerIndex = config.maxConcurrentSessions)
        val lifecycle = WorldLifecycle(dispatchers.world, world::run)
        val admissions =
            GameAdmission(world, config.maxConcurrentSessions) { accountId ->
                withContext(dispatchers.io) { characters.load(accountId) }
            }
        InProcessGameServer(
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
