package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.IsaacCipher
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import emu.protocol.osrs239.game.prot.GameServerProt
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals

// The GPI-init reference loop covers slots 1..2047 but skips the local index, so it emits 2046
// (not 2047) 18-bit entries — matching [RebuildNormalEncoder].
private const val OTHER_PLAYER_SLOTS = 2046
private const val GPI_INIT_BITS = 30 + OTHER_PLAYER_SLOTS * 18
private const val GPI_INIT_BYTES = (GPI_INIT_BITS + 7) / 8 // 4608
private const val ZONE_BYTES = 6
private const val REBUILD_NORMAL_BODY_SIZE = GPI_INIT_BYTES + ZONE_BYTES // 4614

/** Reads a big-endian u16 length prefix from [ch] (the plaintext frame length for a VAR_SHORT prot). */
private suspend fun readU16(ch: ByteReadChannel): Int {
    val hi = ch.readByte().toInt() and 0xFF
    val lo = ch.readByte().toInt() and 0xFF
    return (hi shl 8) or lo
}

/** Unpacks the leading 30 MSB-first bits of a RebuildNormal body back into (plane, x, y). */
private fun decodePacked30(body: ByteArray): Triple<Int, Int, Int> {
    var acc = 0L
    for (i in 0 until 4) acc = (acc shl 8) or (body[i].toLong() and 0xFF)
    val packed = (acc ushr 2).toInt()
    val plane = (packed ushr 28) and 0x3
    val x = (packed ushr 14) and 0x3FFF
    val y = packed and 0x3FFF
    return Triple(plane, x, y)
}

/**
 * Guards the milestone-5 login→game boundary: after a real login exchange reaches response code 2,
 * the server writes the success trailer as one advertised span byte + exactly **34** account-info
 * bytes (never a padded 37), then the first game packet — a [GameServerProt.REBUILD_NORMAL] whose
 * opcode is ISAAC-adjusted by the outbound cipher (seeds+50, per [performLoginBlock]'s doc) and whose
 * body bit-packs the Lumbridge spawn tile. A stray pad in the trailer would be decoded as an empty
 * first packet and advance the outbound keystream once, desyncing every following opcode — the exact
 * milestone-5 root cause this test exists to catch. The full post-login packet stream (the whole
 * initial cycle plus hundreds of heartbeat ticks) is asserted end-to-end by [GameStreamOracleTest].
 */
class GameStageTest {

    private fun loadRealOrSkip(): RsaKeyPair? {
        val file = File("../server-rsa.properties")
        if (!file.isFile) {
            println("SKIP: no server-rsa.properties — run :tools:client-patch:run first")
            return null
        }
        return ServerRsaKeyFile.load(file)
    }

    private fun rsaPlaintext(seeds: IntArray, serverKey: Long, password: String): ByteArray {
        val buf = JagexBuffer.alloc(1 + 16 + 8 + 1 + 1 + password.length + 1)
        buf.writeByte(1) // magic
        for (s in seeds) buf.writeInt(s)
        buf.writeLong(serverKey)
        buf.writeByte(0) // auth-method byte
        buf.writeByte(0) // string-type marker byte
        buf.writeCString(password)
        return buf.array
    }

    private fun loginBlockPayload(keyPair: RsaKeyPair, seeds: IntArray, serverKey: Long, password: String): ByteArray {
        val plaintext = rsaPlaintext(seeds, serverKey, password)
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        val header = ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE)
        val xteaTailFiller = ByteArray(8)
        val out = JagexBuffer.alloc(header.size + 2 + cipherBytes.size + xteaTailFiller.size)
        out.writeBytes(header)
        out.writeShort(cipherBytes.size)
        out.writeBytes(cipherBytes)
        out.writeBytes(xteaTailFiller)
        return out.array
    }

    @Test fun `after login the server writes the 34-byte success trailer then an ISAAC-adjusted RebuildNormal at the spawn tile`() = runBlocking {
        val keyPair = loadRealOrSkip() ?: return@runBlocking
        val gameCodecs = koinApplication { modules(gameModule) }.koin.buildCodecRepository()

        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        val serverJob = launch {
            val conn = server.accept()
            val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            when (r.readByte().toInt() and 0xFF) {
                14 -> {
                    val serverKey = performLoginInit(w)
                    when (r.readByte().toInt() and 0xFF) {
                        16, 18 -> {
                            val ciphers = performLoginBlock(r, w, serverKey, keyPair)
                            if (ciphers != null) {
                                runGameStage(
                                    r, w, ciphers.inbound, ciphers.outbound, gameCodecs,
                                    idleTimeout = 2.seconds,
                                    tickInterval = 20.milliseconds,
                                    maxTicks = 1,
                                )
                            }
                        }
                        else -> {}
                    }
                }
                else -> {}
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)

        cw.writeByte(14)
        val initReply = ByteArray(9)
        cr.readFully(initReply)
        assertEquals(0, initReply[0].toInt())

        var serverKey = 0L
        for (i in 1..8) serverKey = (serverKey shl 8) or (initReply[i].toLong() and 0xFF)

        val seeds = intArrayOf(11, 22, 33, 44)
        val payload = loginBlockPayload(keyPair, seeds, serverKey, password = "hunter2")

        cw.writeByte(16)
        cw.writeByte((payload.size ushr 8).toByte())
        cw.writeByte((payload.size and 0xFF).toByte())
        cw.writeFully(payload)

        val responseCode = cr.readByte().toInt() and 0xFF
        assertEquals(2, responseCode)

        // Mirror the client's real fresh-login boundary instead of trusting the server constant:
        // one advertised span byte, exactly 34 account-info bytes, then the first game header.
        // A three-byte pad here used to be consumed as an empty rebuild and advanced ISAAC once.
        assertEquals(37, cr.readByte().toInt() and 0xFF, "login-info advertised span")
        val loginInfo = ByteArray(34).also { cr.readFully(it) }
        assertEquals(LOGIN_SUCCESS_TRAILER.drop(1), loginInfo.toList(), "login-info payload")

        // Independent cipher, seeded identically to the server's outbound (seeds+50, per
        // performLoginBlock's doc), to compute the first game packet's expected ISAAC-adjusted opcode.
        val expectedOutboundCipher = IsaacCipher(IntArray(seeds.size) { seeds[it] + 50 })

        // The first game packet is REBUILD_NORMAL (VAR_SHORT: [opcode+K][u16 length][body]); its body
        // bit-packs the spawn tile in the leading 30 bits.
        val opcode1 = cr.readByte().toInt() and 0xFF
        val expectedOpcode1 = (GameServerProt.REBUILD_NORMAL.opcode + expectedOutboundCipher.nextInt()) and 0xFF
        assertEquals(expectedOpcode1, opcode1)
        assertEquals(REBUILD_NORMAL_BODY_SIZE, readU16(cr))

        val rebuildBody = ByteArray(REBUILD_NORMAL_BODY_SIZE)
        cr.readFully(rebuildBody)
        val (plane, x, y) = decodePacked30(rebuildBody)
        assertEquals(0, plane)
        assertEquals(3222, x)
        assertEquals(3218, y)

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
