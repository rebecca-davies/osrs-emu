package emu.server.host

import emu.compression.HuffmanCodec
import emu.server.world.map.CacheCollisionMap
import emu.persistence.character.CharacterStore
import emu.persistence.character.CharacterWriteQueue
import emu.persistence.character.DurableCharacterWrite
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import emu.persistence.chat.ChatAuditSink
import emu.server.world.GameService
import emu.server.world.config.GameExecutionConfig
import emu.transport.codec.CodecRepositoryBuilder
import kotlin.test.Test
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class GameWiringTest {
    @Test
    fun `host resolves one world service from explicit capabilities`() {
        val dependencies =
            module {
                single<CharacterStore> { NoCharacters }
                single<CharacterWriteQueue> {
                    CharacterWriteQueue { DurableCharacterWrite }
                }
                single<ChatAuditSink> { ChatAuditSink { true } }
            }
        val application =
            koinApplication {
                allowOverride(false)
                modules(
                    dependencies,
                    gameModule(
                        codecs = CodecRepositoryBuilder().build(),
                        collision = CacheCollisionMap({ _, _ -> null }, { null }),
                        huffman = HuffmanCodec(ByteArray(256) { 8 }),
                        config = GameExecutionConfig(connectionWorkerThreads = 1, entryWorkerThreads = 1),
                    ),
                )
            }

        val world = application.koin.get<GameService>()

        assertSame(world, application.koin.get<GameService>())
        application.close()
    }

    private object NoCharacters : CharacterStore {
        override fun load(playerId: Long): PlayerRecord? = null

        override fun save(save: PlayerSessionSave) = Unit
    }
}
