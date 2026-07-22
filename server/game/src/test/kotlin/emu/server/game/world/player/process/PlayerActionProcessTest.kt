package emu.server.game.world.player.process

import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.chat.ChatFilterInput
import emu.game.chat.PublicChatInput
import emu.game.command.PlayerCommandInput
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.config.UiComponentMap
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.Tile
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.ui.ButtonClick
import emu.game.ui.Component
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.World
import emu.server.game.world.addTestPlayer
import emu.server.game.world.player.ConnectedPlayer
import emu.server.game.world.player.command.PlayerCommandRepositoryBuilder
import emu.server.game.world.player.command.bot.BotClientRequestResult
import emu.server.game.world.player.command.buildPlayerCommandRepository
import emu.server.game.world.testWorld
import emu.server.session.account.AccountPrivilege
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerActionProcessTest {
    private val huffman = HuffmanCodec(ByteArray(256) { 8 })

    @Test
    fun `button trigger runs before the latest route advances`() {
        val (player, connection) = player(position = CharacterPosition(0, 0, 2))
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val process = process(movement)
        connection.actions.submit(PlayerAction.Route(0, 3))
        connection.actions.submit(PlayerAction.Button(ButtonClick(160, 28)))
        connection.actions.submit(PlayerAction.Route(3, 0))

        process.process(player, connection)
        movement.process(player.movement)

        assertTrue(player.movement.runEnabled)
        assertEquals(1, player.varps[PlayerVarpCatalog.RUN_MODE])
        assertEquals(CharacterPosition(2, 0, 2), player.movement.position.toPosition())
    }

    @Test
    fun `control temporarily reverses run without changing persistent run mode`() {
        val (player, connection) = player(position = CharacterPosition(0, 0, 2))
        val movement = PlayerMovementProcess(OpenCollisionMap)
        connection.actions.submit(PlayerAction.Route(3, 0, invertRun = true))

        process(movement).process(player, connection)
        movement.process(player.movement)

        assertFalse(player.movement.runEnabled)
        assertEquals(CharacterPosition(2, 0, 2), player.movement.position.toPosition())
    }

    @Test
    fun `feature content can restrict operation and component payload`() {
        val (player, connection) = player()
        val process = process(PlayerMovementProcess(OpenCollisionMap))
        connection.actions.submit(PlayerAction.Button(ButtonClick(182, 8, op = 2)))
        connection.actions.submit(PlayerAction.Button(ButtonClick(182, 8, sub = 0)))
        process.process(player, connection)
        assertFalse(player.logoutRequested)

        connection.actions.submit(PlayerAction.Button(ButtonClick(182, 8)))
        process.process(player, connection)

        assertTrue(player.logoutRequested)
    }

    @Test
    fun `generic button dispatch preserves operation slot and object for feature content`() {
        val (player, connection) = player()
        var received: ButtonClick? = null
        val scripts =
            PlayerScriptRepository.build(
                UiComponentMap.parse("[components]\n\"test:button\" = 11927560"),
            ) {
                onButton("test:button") { received = lastButton }
            }
        val process =
            PlayerActionProcess(
                PlayerMovementProcess(OpenCollisionMap),
                PlayerChatActionProcess(huffman, ChatAuditSink { true }),
                PlayerScriptRunner(scripts),
                PlayerCommandRepositoryBuilder().build(),
            )
        val click = ButtonClick(182, 8, sub = 3, obj = 4_151, op = 4)
        connection.actions.submit(PlayerAction.Button(click))

        process.process(player, connection)

        assertEquals(click, received)
    }

    @Test
    fun `chat filters and audited public chat mutate only world state`() {
        val audits = mutableListOf<ChatAuditMessage>()
        val (player, connection) = player(privilege = AccountPrivilege.MODERATOR)
        val chat =
            PlayerChatActionProcess(
                huffman,
                ChatAuditSink { audits += it; true },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            )
        val scripts = PlayerContentCatalog.load(UiContentCatalog.load().components)
        val process =
            PlayerActionProcess(
                PlayerMovementProcess(OpenCollisionMap),
                chat,
                PlayerScriptRunner(scripts),
                PlayerCommandRepositoryBuilder().build(),
            )
        connection.actions.submit(PlayerAction.Chat(ChatFilterInput(3, 1, 2)))
        connection.actions.submit(PlayerAction.Chat(PublicChatInput(1, 2, "hello")))

        process.process(player, connection)

        assertEquals(3, player.chatFilters.publicMode)
        assertEquals(1, player.chatFilters.privateMode)
        assertEquals(2, player.chatFilters.tradeMode)
        assertEquals("hello", audits.single().text)
        val published = assertNotNull(connection.publicChat.current())
        assertEquals(AccountPrivilege.MODERATOR.level, published.modIcon)
        assertEquals("hello", huffman.decode(published.encodedText))

        val (rejected, rejectedConnection) = player(id = 2)
        PlayerChatActionProcess(huffman, ChatAuditSink { false })
            .process(rejected, rejectedConnection.publicChat, PublicChatInput(0, 0, "not audited"))
        assertNull(rejectedConnection.publicChat.current())
    }

    @Test
    fun `administrator bot command is selected on the world thread and queues feedback`() {
        val (player, connection) = player(privilege = AccountPrivilege.ADMINISTRATOR)
        val scripts = PlayerContentCatalog.load(UiContentCatalog.load().components)
        var requested = 0
        val process =
            PlayerActionProcess(
                PlayerMovementProcess(OpenCollisionMap),
                PlayerChatActionProcess(huffman, ChatAuditSink { true }),
                PlayerScriptRunner(scripts),
                buildPlayerCommandRepository { count ->
                    requested = count
                    BotClientRequestResult.Accepted(count, count)
                },
            )
        connection.actions.submit(PlayerAction.Command(PlayerCommandInput("addbots 2")))

        process.process(player, connection)

        assertEquals(2, requested)
        assertEquals(
            listOf("Starting 2 automated player client(s); 2 slot(s) reserved."),
            connection.drainGameMessages(),
        )
    }

    @Test
    fun `player controls request world work without closing the modal in client input`() {
        val (player, connection) = player()
        player.interfaces.openModal(Component.of(161, 500), 200)
        connection.actions.submit(PlayerAction.CloseModal)
        connection.actions.submit(PlayerAction.IdleLogout)

        process(PlayerMovementProcess(OpenCollisionMap)).process(player, connection)

        assertTrue(player.interfaces.hasModal())
        assertTrue(player.idleLogoutRequested)
        assertFalse(player.logoutRequested)
    }

    private fun process(movement: PlayerMovementProcess): PlayerActionProcess {
        val scripts = PlayerContentCatalog.load(UiContentCatalog.load().components)
        return PlayerActionProcess(
            movement,
            PlayerChatActionProcess(huffman, ChatAuditSink { true }),
            PlayerScriptRunner(scripts),
            PlayerCommandRepositoryBuilder().build(),
        )
    }

    private fun player(
        id: Long = 1,
        position: CharacterPosition = CharacterPosition(3222, 3218, 0),
        privilege: AccountPrivilege = AccountPrivilege.PLAYER,
    ): ConnectedPlayer =
        testWorld(maxPlayerIndex = 1).addTestPlayer(
            CharacterRecord(id, "Player$id", position, 0),
            IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
            GameOutputSink { true },
            privilege = privilege,
        ).also { it.player.activate(UiContentCatalog.load().gameframe) }

    private fun Tile.toPosition() = CharacterPosition(x, y, plane)
}
