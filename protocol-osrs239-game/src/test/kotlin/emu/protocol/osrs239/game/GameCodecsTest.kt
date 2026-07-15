package emu.protocol.osrs239.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GameCodecsTest {
    @Test
    fun `repository contains every declared game codec`() {
        val repository = buildGameCodecRepository()

        GameCodecs.decoders.forEach { decoder ->
            assertSame(decoder, repository.decoder(decoder.prot.opcode))
        }
        GameCodecs.encoders.forEach { encoder ->
            assertSame(encoder, repository.encoder(encoder.messageType))
        }
        assertEquals(4, GameCodecs.decoders.size)
        assertEquals(35, GameCodecs.encoders.size)
    }
}
