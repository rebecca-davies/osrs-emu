package emu.server.host

import emu.compression.HuffmanCodec
import emu.game.pathfinding.OpenCollisionMap
import emu.persistence.character.CharacterStore
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import emu.persistence.chat.ChatAuditSink
import emu.server.world.WorldServer
import emu.server.world.config.GameExecutionConfig
import emu.transport.codec.CodecRepositoryBuilder
import kotlin.test.Test
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class WorldWiringTest {
    @Test
    fun `host resolves one world service from explicit capabilities`() {
        val dependencies =
            module {
                single<CharacterStore> { NoCharacters }
                single<ChatAuditSink> { ChatAuditSink { true } }
            }
        val application =
            koinApplication {
                allowOverride(false)
                modules(
                    dependencies,
                    worldModule(
                        codecs = CodecRepositoryBuilder().build(),
                        collision = OpenCollisionMap,
                        huffman = HuffmanCodec(ByteArray(256) { 8 }),
                        config = GameExecutionConfig(ioWorkerThreads = 1),
                    ),
                )
            }

        val world = application.koin.get<WorldServer>()

        assertSame(world, application.koin.get<WorldServer>())
        application.close()
    }

    private object NoCharacters : CharacterStore {
        override fun load(playerId: Long): PlayerRecord? = null

        override fun save(save: PlayerSessionSave) = Unit
    }
}
