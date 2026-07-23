package emu.protocol.osrs239.game.codec.component

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.game.message.component.IfSetText
import emu.protocol.osrs239.game.prot.GameServerProt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IfSetTextEncoderTest {
    @Test
    fun `encodes the exact rev 239 component text body`() {
        assertEquals(GameServerProt.IF_SET_TEXT, IfSetTextEncoder.prot)
        assertEquals(
            listOf('h'.code, 'i'.code, 0, 0, 39, 0, 165).map(Int::toByte),
            IfSetTextEncoder.encode(
                NopStreamCipher,
                IfSetText(interfaceId = 165, componentId = 39, text = "hi"),
            ).toList(),
        )
    }

    @Test
    fun `encodes component text as exact CP 1252 bytes`() {
        assertEquals(
            listOf(0x80, 0, 0, 39, 0, 165).map(Int::toByte),
            IfSetTextEncoder.encode(
                NopStreamCipher,
                IfSetText(interfaceId = 165, componentId = 39, text = "€"),
            ).toList(),
        )
    }

    @Test
    fun `rejects text that cannot form one exact C string`() {
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = 165, componentId = 39, text = "before\u0000after")
        }
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = 165, componentId = 39, text = "😀")
        }
    }

    @Test
    fun `accepts only unsigned short component id halves`() {
        val minimum = IfSetText(interfaceId = 0, componentId = 0, text = "")
        val maximum = IfSetText(interfaceId = 0xFFFF, componentId = 0xFFFF, text = "")

        assertEquals(0, minimum.interfaceId)
        assertEquals(0xFFFF, maximum.componentId)
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = -1, componentId = 0, text = "")
        }
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = 0x1_0000, componentId = 0, text = "")
        }
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = 0, componentId = -1, text = "")
        }
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = 0, componentId = 0x1_0000, text = "")
        }
    }

    @Test
    fun `accepts the exact maximum variable short body and rejects one byte more`() {
        val maximumText = "a".repeat(MAX_TEXT_BYTES)
        val message = IfSetText(interfaceId = 0xFFFF, componentId = 0xFFFF, text = maximumText)

        assertEquals(MAX_TEXT_BYTES, message.text.length)
        assertEquals(
            MAX_PACKET_BODY_BYTES,
            IfSetTextEncoder.encode(NopStreamCipher, message).size,
        )
        assertFailsWith<IllegalArgumentException> {
            IfSetText(interfaceId = 0, componentId = 0, text = "a".repeat(MAX_TEXT_BYTES + 1))
        }
    }

    private companion object {
        const val MAX_PACKET_BODY_BYTES = 0xFFFF
        const val MAX_TEXT_BYTES = MAX_PACKET_BODY_BYTES - Int.SIZE_BYTES - 1
    }
}
