package emu.server.host.composition

import emu.compression.HuffmanCodec
import emu.persistence.character.CharacterStore
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.model.CharacterSave
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.server.game.GameService
import emu.server.game.config.GameExecutionConfig
import emu.server.game.world.map.CacheCollisionMap
import emu.transport.codec.CodecRepositoryBuilder
import kotlin.test.Test
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class GameWiringTest {
    @Test
    fun `host resolves one game service from explicit capabilities`() {
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

        val game = application.koin.get<GameService>()

        assertSame(game, application.koin.get<GameService>())
        application.close()
    }

    private object NoCharacters : CharacterStore {
        override fun load(characterId: Long): CharacterRecord? = null

        override fun save(save: CharacterSave) = Unit
    }
}
