package emu.server.game.network.output

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.game.codec.cycle.PacketGroupStartEncoder
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.codec.CodecRepositoryBuilder
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.outbound.PacketWriter
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readFully
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class GameWritePacketTest {
    @Test
    fun `packet groups are length-prefixed inside one ordered batch`() = runBlocking {
        val codecs =
            CodecRepositoryBuilder()
                .bindEncoder(TestEncoder)
                .bindEncoder(PacketGroupStartEncoder)
                .build()
        val channel = ByteChannel(autoFlush = false)
        val writer = GameOutboundWriter(PacketWriter(codecs, NopStreamCipher, channel))
        val batch =
            GameOutputBatch.build {
                packet(TestMessage(1))
                packetGroup(listOf(TestMessage(2), TestMessage(3)))
                packet(TestMessage(4))
            }

        writer.write(batch)
        val expected =
            listOf(
                10, 1,
                PacketGroupStartEncoder.prot.opcode, 0, 4,
                10, 2,
                10, 3,
                10, 4,
            ).map(Int::toByte)
        val actual = ByteArray(expected.size)
        withTimeout(1.seconds) { channel.readFully(actual) }
        channel.close()

        assertEquals(expected, actual.toList())
    }

    @Test
    fun `packet group bodies are encoded exactly once`() = runBlocking {
        val encoder = CountingEncoder()
        val codecs =
            CodecRepositoryBuilder()
                .bindEncoder(encoder)
                .bindEncoder(PacketGroupStartEncoder)
                .build()
        val channel = ByteChannel(autoFlush = false)
        val writer = GameOutboundWriter(PacketWriter(codecs, NopStreamCipher, channel))

        writer.write(GameOutputBatch.build { packetGroup(listOf(TestMessage(1), TestMessage(2))) })
        channel.close()

        assertEquals(2, encoder.encodes)
    }

    private data class TestMessage(val value: Int) : OutgoingMessage

    private object TestEncoder : CipherIndependentMessageEncoder<TestMessage> {
        override val prot = Prot(10, 1)
        override val messageType = TestMessage::class.java
        override fun encode(message: TestMessage) = byteArrayOf(message.value.toByte())
    }

    private class CountingEncoder : CipherIndependentMessageEncoder<TestMessage> {
        override val prot = Prot(10, 1)
        override val messageType = TestMessage::class.java
        var encodes = 0
            private set

        override fun encode(message: TestMessage): ByteArray {
            encodes++
            return byteArrayOf(message.value.toByte())
        }
    }
}
