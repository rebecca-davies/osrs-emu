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

/** Appearance-less PlayerInfo body: HD byte + two LD bytes (see [PlayerInfoEncoder]). */
private const val PLAYER_INFO_BODY_SIZE = 3

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

/** Number of per-tick PLAYER_INFO heartbeats the test drives the loop through (see [GameLoop]). */
private const val HEARTBEAT_TICKS = 3

/**
 * Proves the milestone-5 game stage: after a real login exchange reaches response code 2, the
 * server proactively pushes the initial scene ([emu.protocol.osrs239.game.message.RebuildNormal])
 * and then, per [GameLoop], a PLAYER_INFO heartbeat **every tick** — each with its opcode
 * ISAAC-adjusted by the outbound cipher (seeds+50, per [performLoginBlock]'s doc). Mirrors
 * [LoginBlockFlowTest]'s harness, extended to drive [runGameStage] with a short tick interval and a
 * small tick cap so a handful of heartbeats can be asserted without a real-time sleep.
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

    @Test fun `after login, the server proactively sends RebuildNormal then PlayerInfo with ISAAC-adjusted opcodes`() = runBlocking {
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
                                    maxTicks = HEARTBEAT_TICKS,
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
        val trailer = ByteArray(LOGIN_SUCCESS_TRAILER.size)
        cr.readFully(trailer)
        assertEquals(2, responseCode)
        assertEquals(LOGIN_SUCCESS_TRAILER.toList(), trailer.toList())

        // Independent cipher, seeded identically to the server's outbound (seeds+50, per
        // performLoginBlock's doc), to compute the expected ISAAC-adjusted opcode bytes.
        val expectedOutboundCipher = IsaacCipher(IntArray(seeds.size) { seeds[it] + 50 })

        // REBUILD_NORMAL is VAR_SHORT: [opcode+K][u16 plaintext length][body].
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

        // The heartbeat: EACH tick the loop sends PLAYER_INFO (GPI) then SERVER_TICK_END (the
        // per-cycle terminator), so a healthy in-game client receives a steady stream of both. Assert
        // the first [HEARTBEAT_TICKS] each arrive, in that order, with the next ISAAC-adjusted opcode
        // (proving the outbound keystream advances exactly once per packet — PLAYER_INFO then
        // SERVER_TICK_END — and stays in lockstep with the client) and the byte-exact body framing.
        repeat(HEARTBEAT_TICKS) { tick ->
            // PLAYER_INFO is VAR_SHORT: [opcode+K][u16 plaintext length][body].
            val piOpcode = cr.readByte().toInt() and 0xFF
            val expectedPiOpcode = (GameServerProt.PLAYER_INFO.opcode + expectedOutboundCipher.nextInt()) and 0xFF
            assertEquals(expectedPiOpcode, piOpcode, "PLAYER_INFO opcode for heartbeat tick $tick")
            assertEquals(PLAYER_INFO_BODY_SIZE, readU16(cr), "PLAYER_INFO body size for heartbeat tick $tick")

            val playerInfoBody = ByteArray(PLAYER_INFO_BODY_SIZE)
            cr.readFully(playerInfoBody) // presence/length only — full body bytes are MEDIUM confidence (see PlayerInfoEncoder)

            // SERVER_TICK_END is FIXED size 0: [opcode+K] alone — no length prefix, no body.
            val steOpcode = cr.readByte().toInt() and 0xFF
            val expectedSteOpcode = (GameServerProt.SERVER_TICK_END.opcode + expectedOutboundCipher.nextInt()) and 0xFF
            assertEquals(expectedSteOpcode, steOpcode, "SERVER_TICK_END opcode for heartbeat tick $tick")
        }

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
