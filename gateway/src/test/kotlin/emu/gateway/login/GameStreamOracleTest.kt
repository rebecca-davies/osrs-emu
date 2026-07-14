package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.IsaacCipher
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Number of heartbeat ticks driven — enough packets to cross the ISAAC 256-value regeneration boundary twice. */
private const val ORACLE_TICKS = 300

/**
 * The rev-239 client's complete server-packet size table, extracted verbatim from the running
 * client's decompiled `jc.java` static initializer (`new jc(opcode, size)`): size >= 0 is a fixed
 * body length, -1 a u8 length prefix, -2 a big-endian u16 length prefix. This is the client's OWN
 * framing ground truth — any opcode absent here would crash the client with "Invalid ServerProt".
 */
private val CLIENT_PROT_SIZES: Map<Int, Int> = mapOf(
    0 to 4, 1 to 5, 2 to -2, 3 to 0, 4 to 9, 5 to 1, 6 to -1, 7 to 7, 8 to -2, 9 to 3, 10 to 2,
    11 to -1, 12 to 6, 13 to 14, 14 to 8, 15 to -2, 16 to 6, 17 to 10, 18 to -2, 19 to -2, 20 to 5,
    21 to 1, 22 to -2, 23 to 4, 24 to -1, 25 to -2, 26 to 1, 27 to -2, 28 to -2, 29 to 4, 30 to 6,
    31 to 2, 32 to 7, 33 to -2, 34 to 14, 35 to 10, 36 to 0, 37 to -1, 38 to 1, 39 to -2, 40 to 7,
    41 to 10, 42 to -2, 43 to 1, 44 to 0, 45 to 2, 46 to 7, 47 to 3, 48 to 27, 49 to -2, 50 to 6,
    51 to 7, 52 to 20, 53 to 20, 54 to 3, 55 to 6, 56 to -2, 57 to 0, 58 to 1, 59 to 6, 60 to 0,
    61 to -1, 62 to -1, 63 to 5, 64 to 2, 65 to 4, 66 to 2, 67 to -1, 68 to 8, 69 to -2, 70 to 7,
    71 to -2, 72 to 9, 73 to 1, 74 to -1, 75 to 1, 76 to 0, 77 to 5, 78 to 17, 79 to 4, 80 to -2,
    81 to -2, 82 to 6, 83 to 0, 84 to 6, 85 to -2, 86 to 6, 87 to 5, 88 to 0, 89 to 6, 90 to 8,
    91 to -1, 92 to 0, 93 to 2, 94 to 4, 95 to -2, 96 to 2, 97 to 3, 98 to 8, 99 to 12, 100 to 5,
    101 to 9, 102 to 8, 103 to 4, 104 to 10, 105 to -2, 106 to 10, 107 to 8, 108 to 16, 109 to -2,
    110 to 4, 111 to -2, 112 to -1, 113 to 28, 114 to -2, 115 to 8, 116 to 2, 117 to 0, 118 to 5,
    119 to 3, 120 to 4, 121 to 5, 122 to -2, 123 to 8, 124 to 2, 125 to -2, 126 to -2, 127 to -1,
    128 to 15, 129 to 14, 130 to 11, 131 to 1, 132 to -2, 133 to 13, 134 to 10, 135 to 4, 136 to -2,
    137 to 10, 138 to 1, 139 to 8, 140 to 9, 141 to 9, 142 to 8, 143 to 4, 144 to 9, 145 to 3,
    146 to 8, 147 to 9, 148 to 11,
)

/**
 * The client's outbound-decrypt keystream with the non-advancing peek its smart-opcode width
 * decision needs (decompiled `xv.ax` peeks via `xs.af`, then `xv.ap` consumes via `xs.ag`). Backed
 * by our [IsaacCipher], whose value-parity with the client's real `xs` class is proven by
 * `IsaacCipherTest`'s 4096-value golden vector (generated FROM that class) — so a decode failure
 * here is a genuine stream bug, not a cipher-implementation gap.
 */
private class ClientIsaacOracle(seeds: IntArray) {
    private val cipher = IsaacCipher(seeds)
    private var buffered: Int? = null

    fun peek(): Int = buffered ?: cipher.nextInt().also { buffered = it }
    fun next(): Int = (buffered ?: cipher.nextInt()).also { buffered = null }
}

/**
 * Decodes a captured server->client game-stream byte-for-byte the way the rev-239 client does
 * (decompiled `client.java` packet loop + `xv.ax`/`xv.ap`): peek-decrypt the next byte; if the
 * decrypted value is >= 128 the opcode is a 2-byte smart (each byte consuming one keystream int),
 * else 1 byte; then a plaintext u8/u16 length for var-size prots per [CLIENT_PROT_SIZES]; then the
 * body. Returns the (opcode, bodyLength) sequence, failing loudly at the first undecodable byte.
 */
private fun decodeAsClient(stream: ByteArray, oracle: ClientIsaacOracle): List<Pair<Int, Int>> {
    val decoded = mutableListOf<Pair<Int, Int>>()
    var pos = 0
    fun u8(): Int = stream[pos++].toInt() and 0xFF
    while (pos < stream.size) {
        val at = pos
        val peeked = (stream[pos].toInt() and 0xFF) - oracle.peek() and 0xFF
        val opcode = if (peeked < 128) {
            u8() - oracle.next() and 0xFF
        } else {
            val hi = u8() - oracle.next() and 0xFF
            val lo = u8() - oracle.next() and 0xFF
            ((hi - 128) shl 8) + lo
        }
        val size = CLIENT_PROT_SIZES[opcode]
            ?: fail("stream desync: opcode $opcode is not a client prot (offset $at, packet #${decoded.size})")
        val bodyLength = when (size) {
            -1 -> u8()
            -2 -> (u8() shl 8) or u8()
            else -> size
        }
        if (pos + bodyLength > stream.size) {
            fail("stream desync: opcode $opcode declares $bodyLength body bytes but only ${stream.size - pos} remain (offset $at, packet #${decoded.size})")
        }
        pos += bodyLength
        decoded += opcode to bodyLength
    }
    return decoded
}

/**
 * Milestone-5 oracle: proves the ENTIRE post-login outbound stream (RebuildNormal + the rsmod
 * onLogin init batch + [ORACLE_TICKS] heartbeat ticks) decodes cleanly under the client's own
 * cipher implementation and its own opcode/size table. This is the headless stand-in for the real
 * client (CLAUDE.md §12a — iterate headless, client as acceptance): any opcode/length/keystream
 * divergence fails here with the exact byte offset instead of an opaque in-game disconnect.
 *
 * Skips (with a note) when the gitignored client jar or server RSA key is absent.
 */
class GameStreamOracleTest {

    @Test fun `the client's own cipher and size table decode our entire post-login stream`() = runBlocking {
        val keyFile = File("../server-rsa.properties")
        if (!keyFile.isFile) {
            println("SKIP: no server-rsa.properties — run :tools:client-patch first")
            return@runBlocking
        }
        val keyPair = ServerRsaKeyFile.load(keyFile)
        val gameCodecs = koinApplication { modules(gameModule) }.koin.buildCodecRepository()
        val seeds = intArrayOf(0x1234, 0x5678, -0x1AB5F00D, 0x0C0FFEE)

        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        val serverJob = launch {
            val conn = server.accept()
            try {
                val r = conn.openReadChannel()
                val w = conn.openWriteChannel(autoFlush = false)
                check(r.readByte().toInt() and 0xFF == 14) { "expected op14" }
                val serverKey = performLoginInit(w)
                check(r.readByte().toInt() and 0xFF == 16) { "expected op16" }
                val ciphers = performLoginBlock(
                    r,
                    w,
                    serverKey,
                    keyPair,
                    authenticate = ::acceptTestLogin,
                ) ?: error("login block rejected")
                runGameStage(
                    r, w, ciphers.inbound, ciphers.outbound, gameCodecs,
                    player = ciphers.player,
                    saveSession = { _, _, _ -> },
                    idleTimeout = 10.seconds,
                    tickInterval = 1.milliseconds,
                    maxTicks = ORACLE_TICKS,
                )
                w.flush()
            } finally {
                conn.close()
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel()
        val cw = client.openWriteChannel(autoFlush = true)

        cw.writeByte(14)
        val initReply = ByteArray(9).also { cr.readFully(it) }
        check(initReply[0].toInt() == 0) { "login init rejected" }
        var serverKey = 0L
        for (i in 1..8) serverKey = (serverKey shl 8) or (initReply[i].toLong() and 0xFF)

        val payload = loginBlockPayload(keyPair, seeds, serverKey)
        cw.writeByte(16)
        cw.writeByte((payload.size ushr 8).toByte())
        cw.writeByte((payload.size and 0xFF).toByte())
        cw.writeFully(payload)

        check(cr.readByte().toInt() and 0xFF == 2) { "expected login response 2" }
        ByteArray(LOGIN_SUCCESS_TRAILER.size).also { cr.readFully(it) }

        val stream = readUntilEof(cr)
        serverJob.join()
        client.close()
        server.close()
        selector.close()

        val expected = buildList {
            add(49 to 4614)
            addAll(listOf(67 to 1, 124 to 2, 75 to 1, 21 to 1, 73 to 1, 44 to 0, 97 to 3))
            add(93 to 2)
            add(47 to 3)
            add(116 to 2)
            add(122 to 1)
            add(28 to 75)
            add(85 to 1)
            repeat(49) { add(54 to 3) }
            add(87 to 5)
            addAll(listOf(22 to 8, 22 to 8, 96 to 2))
            repeat(23) { add(7 to 7) }
            addAll(listOf(3 to 0, 138 to 1, 25 to 179))
            repeat(25) { add(46 to 7) }
            addAll(listOf(31 to 2, 64 to 2, 92 to 0, 43 to 1))
            add(74 to 24) // MESSAGE_GAME "Welcome to RuneScape." (1 smart type + 1 flag + 21 + NUL)
            add(83 to 0)
            // Every steady-state cycle is also packet-grouped: active world, NPC origin, idle
            // player info and empty NPC info, followed by the tick terminator.
            repeat(ORACLE_TICKS) {
                add(93 to 2)
                add(47 to 3)
                add(116 to 2)
                add(28 to 3)
                add(85 to 1)
                add(83 to 0)
            }
        }
        val outboundSeeds = IntArray(seeds.size) { seeds[it] + 50 }
        val decoded = decodeAsClient(stream, ClientIsaacOracle(outboundSeeds))
        assertEquals(expected, decoded)
    }

    private suspend fun readUntilEof(cr: ByteReadChannel): ByteArray {
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (true) {
            val n = cr.readAvailable(tmp, 0, tmp.size)
            if (n == -1) break
            out.write(tmp, 0, n)
        }
        return out.toByteArray()
    }

    private fun loginBlockPayload(keyPair: RsaKeyPair, seeds: IntArray, serverKey: Long): ByteArray {
        val password = "testpass"
        val plaintext = JagexBuffer.alloc(1 + 16 + 8 + 1 + 1 + password.length + 1).apply {
            writeByte(1)
            for (s in seeds) writeInt(s)
            writeLong(serverKey)
            writeByte(0)
            writeByte(0)
            writeCString(password)
        }.array
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        val tail = encryptedUsernameTail(TEST_LOGIN_NAME, seeds)
        return JagexBuffer.alloc(LoginBlockParser.CLEARTEXT_HEADER_SIZE + 2 + cipherBytes.size + tail.size).apply {
            writeBytes(ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE))
            writeShort(cipherBytes.size)
            writeBytes(cipherBytes)
            writeBytes(tail)
        }.array
    }
}
