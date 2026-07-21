package emu.server.host.composition

import emu.compression.HuffmanCodec
import emu.crypto.Rsa
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.player.login.LoginNotices
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.PlayerMovement
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.game.pathfinding.route.PathRoute
import emu.game.pathfinding.route.PlayerRouteFinder
import emu.game.script.execution.PlayerScriptRunner
import emu.persistence.account.AccountRank
import emu.persistence.account.AccountRecord
import emu.persistence.account.AccountStore
import emu.persistence.account.StoredAccount
import emu.persistence.character.CharacterStore
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.model.CharacterSave
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.server.bot.config.BotConfig
import emu.server.bot.config.BotMovementConfig
import emu.server.bot.connection.BotConnectionRunner
import emu.server.game.GameServer
import emu.server.game.GameServerDispatchers
import emu.server.game.config.GameConnectionConfig
import emu.server.game.config.RouteSearchConfig
import emu.server.game.network.connection.GameConnectionRunner
import emu.server.game.network.input.GameInboundReader
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.runtime.lifecycle.WorldLifecycle
import emu.server.game.runtime.lifecycle.WorldRuntime
import emu.server.game.world.World
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.entry.WorldEntry
import emu.server.game.world.map.CollisionMapLoader
import emu.server.game.world.player.cheat.BotClientRequestResult
import emu.server.game.world.player.cheat.BotClientRequestSink
import emu.server.game.world.player.cheat.buildPlayerCheatRepository
import emu.server.game.world.player.process.PlayerActionProcess
import emu.server.game.world.player.process.PlayerChatActionProcess
import emu.server.game.world.player.process.PlayerLifecycleProcess
import emu.server.game.world.player.process.PlayerMainProcess
import emu.server.game.world.player.process.PlayerMovementCycleProcess
import emu.server.game.world.player.process.PlayerOutputProcess
import emu.server.game.world.player.process.PlayerTriggerProcess
import emu.server.game.world.player.route.RouteSearchBudget
import emu.server.gateway.GatewayConfig
import emu.server.gateway.GatewayListener
import emu.server.host.config.CoordinatorConfig
import emu.server.host.handoff.ServerCoordinator
import emu.server.js5.Js5Service
import emu.server.login.LoginServer
import emu.server.login.auth.AccountAuthenticator
import emu.server.login.auth.BcryptConfig
import emu.server.login.auth.BcryptPasswordHasher
import emu.server.login.config.LoginExecutionConfig
import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout

class BotConnectionIntegrationTest {
    @Test
    fun `generated client crosses real login and game services then moves in the world`() = runBlocking {
        val keyPair = Rsa.generateKeyPair(1_024)
        val persistence = BotPersistence()
        val saved = CompletableDeferred<CharacterSave>()
        val movementProcessed = CompletableDeferred<Unit>()
        val stack = gameStack(persistence, saved, movementProcessed)
        val login =
            LoginServer(
                keyPair,
                AccountAuthenticator(persistence, BcryptPasswordHasher(BcryptConfig(cost = 4))),
                LoginExecutionConfig(workerThreads = 1, authenticationTimeout = 2.seconds),
            )
        val coordinator = ServerCoordinator(NoJs5, login, stack.game, CoordinatorConfig(2.seconds))
        val listener =
            GatewayListener.bind(
                GatewayConfig(bindHost = "127.0.0.2", port = 0),
                coordinator.gatewayRoutes(),
            )
        val selector = SelectorManager(Dispatchers.IO)
        var listenerJob: Job? = null
        var botJob: Job? = null
        try {
            stack.game.start()
            listenerJob = launch(Dispatchers.IO) { listener.run() }
            val endpoint = checkNotNull(listener.localEndpoint.botEndpointOrNull())
            botJob =
                launch(Dispatchers.IO) {
                    BotConnectionRunner(
                        BotConfig(
                            loginTimeout = 2.seconds,
                            movement = BotMovementConfig(radius = 2, interval = 10.milliseconds),
                        ),
                        keyPair.publicKey,
                    ).run(endpoint, selector, Semaphore(1))
                }

            withTimeout(5.seconds) { movementProcessed.await() }
            botJob.cancelAndJoin()
            botJob = null
            val characterSave = withTimeout(5.seconds) { saved.await() }

            assertEquals(1, persistence.createdAccounts)
            assertNotEquals(SPAWN, characterSave.position)
        } finally {
            botJob?.cancelAndJoin()
            selector.close()
            listenerJob?.cancelAndJoin()
            listener.close()
            stack.game.stop()
            login.close()
            stack.dispatchers.close()
        }
    }

    @Test
    fun `wildcard gateway endpoints select a loopback address of the same family`() {
        val ipv4 = checkNotNull(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 43_594).botEndpointOrNull())
        val ipv6 = checkNotNull(InetSocketAddress(InetAddress.getByName("::"), 43_594).botEndpointOrNull())

        assertIs<Inet4Address>(ipv4.address)
        assertIs<Inet6Address>(ipv6.address)
        assertEquals(43_594, ipv4.port)
        assertEquals(43_594, ipv6.port)
    }

    @Test
    fun `non-loopback gateway endpoint cannot enable bot clients`() {
        val address = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1))

        assertNull(InetSocketAddress(address, 43_594).botEndpointOrNull())
    }

    private fun gameStack(
        characters: CharacterStore,
        saved: CompletableDeferred<CharacterSave>,
        movementProcessed: CompletableDeferred<Unit>,
    ): GameStack {
        val codecs = buildGameCodecRepository()
        val huffman = HuffmanCodec(ByteArray(256) { 8 })
        val ui = UiContentCatalog.load()
        val world = World(ui.gameframe, LoginNotices.ALL, maxPlayerIndex = 1)
        val commands = WorldCommandQueue(capacity = 32)
        val routeQueued = AtomicBoolean()
        val collision =
            CollisionMap { x, y, plane ->
                if (routeQueued.get()) movementProcessed.complete(Unit)
                OpenCollisionMap.flagsAt(x, y, plane)
            }
        val movement = PlayerMovementProcess(collision)
        val routeFinder = observedRoutes(movement, routeQueued)
        val scripts = PlayerScriptRunner(PlayerContentCatalog.load(ui.components))
        val triggers = PlayerTriggerProcess(scripts)
        val actions =
            PlayerActionProcess(
                routeFinder,
                PlayerChatActionProcess(huffman, ChatAuditSink { true }),
                scripts,
                buildPlayerCheatRepository(BotClientRequestSink { BotClientRequestResult.Unavailable }),
                RouteSearchBudget(RouteSearchConfig()),
            )
        val main =
            PlayerMainProcess(
                scripts,
                triggers,
                PlayerMovementCycleProcess(movement, PreparedCollision),
            )
        val writes =
            CharacterWriteQueue { save ->
                saved.complete(save)
                DurableCharacterWrite
            }
        val cycle =
            WorldCycle(
                world,
                commands,
                actions,
                main,
                PlayerLifecycleProcess(writes, triggers),
                PlayerOutputProcess(),
            )
        val dispatchers = GameServerDispatchers(connectionWorkerThreads = 1, entryWorkerThreads = 1)
        val connectionConfig = GameConnectionConfig(idleTimeout = 2.seconds)
        val entries = WorldEntry(commands, capacity = 1, loadCharacter = characters::load)
        val connections =
            GameConnectionRunner(
                codecs,
                connectionConfig,
                commands,
                GameInboundReader(codecs, huffman, connectionConfig.idleTimeout),
                dispatchers.connections,
            )
        val lifecycle =
            WorldLifecycle(
                dispatchers.world,
                WorldRuntime(cycle, tickInterval = 5.milliseconds)::run,
            )
        return GameStack(GameServer(entries, connections, lifecycle), dispatchers)
    }

    private fun observedRoutes(
        delegate: PlayerRouteFinder,
        routeQueued: AtomicBoolean,
    ): PlayerRouteFinder =
        object : PlayerRouteFinder {
            override fun routeTo(
                movement: PlayerMovement,
                destination: Tile,
                temporaryRun: Boolean?,
            ): PathRoute =
                delegate.routeTo(movement, destination, temporaryRun).also {
                    routeQueued.set(true)
                }
        }

    private class BotPersistence : AccountStore, CharacterStore {
        private var account: StoredAccount? = null
        private var character: CharacterRecord? = null

        val createdAccounts: Int
            @Synchronized get() = if (account == null) 0 else 1

        @Synchronized
        override fun findByUsername(username: String): StoredAccount? =
            account?.takeIf { it.account.username == username }

        @Synchronized
        override fun create(username: String, displayName: String, passwordHash: String): StoredAccount? {
            if (account != null) return null
            val record = AccountRecord(1, username, displayName, AccountRank.PLAYER)
            return StoredAccount(record, passwordHash).also {
                account = it
                character = CharacterRecord(1, displayName, SPAWN, playTimeSeconds = 0)
            }
        }

        @Synchronized
        override fun load(characterId: Long): CharacterRecord? =
            character?.takeIf { it.id == characterId }

        override fun save(save: CharacterSave) = Unit
    }

    private data class GameStack(
        val game: GameServer,
        val dispatchers: GameServerDispatchers,
    )

    private object PreparedCollision : CollisionMapLoader {
        override fun prepare(position: Tile) = Unit

        override fun request(position: Tile): Boolean = true
    }

    private object NoJs5 : Js5Service {
        override suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel) = Unit

        override fun close() = Unit
    }

    private companion object {
        val SPAWN = CharacterPosition(3_222, 3_218, 0)
    }
}
