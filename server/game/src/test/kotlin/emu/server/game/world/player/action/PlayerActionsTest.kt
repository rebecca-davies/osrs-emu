package emu.server.game.world.player.action

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.chat.ChatFilterInput
import emu.game.chat.PublicChatInput
import emu.game.command.PlayerCommandInput
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.config.UiComponentMap
import emu.game.content.ui.config.UiContentCatalog
import emu.game.loc.Loc
import emu.game.loc.LocOpInput
import emu.game.loc.LocRepository
import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.obj.ObjCatalog
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType
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
import emu.server.game.world.player.command.PlayerCommandRepository
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

class PlayerActionsTest {
    @Test
    fun `button trigger runs before the latest route advances`() {
        val (world, player) = player(position = CharacterPosition(0, 0, 2))
        val actions = actions()
        actions.apply(player, PlayerAction.Route(0, 3))
        actions.apply(player, PlayerAction.Button(ButtonClick(160, 28)))
        actions.apply(player, PlayerAction.Route(3, 0))

        world.advanceMovement(player)

        assertTrue(player.movement.runEnabled)
        assertEquals(1, player.varps[PlayerVarpCatalog.RUN_MODE])
        assertEquals(CharacterPosition(2, 0, 2), player.movement.position.toPosition())
    }

    @Test
    fun `control temporarily reverses run without changing persistent run mode`() {
        val (world, player) = player(position = CharacterPosition(0, 0, 2))
        actions().apply(player, PlayerAction.Route(3, 0, invertRun = true))

        world.advanceMovement(player)

        assertFalse(player.movement.runEnabled)
        assertEquals(CharacterPosition(2, 0, 2), player.movement.position.toPosition())
    }

    @Test
    fun `feature content can restrict operation and component payload`() {
        val (_, player) = player()
        val actions = actions()
        actions.apply(player, PlayerAction.Button(ButtonClick(182, 8, op = 2)))
        actions.apply(player, PlayerAction.Button(ButtonClick(182, 8, sub = 0)))
        assertFalse(player.logoutRequested)

        actions.apply(player, PlayerAction.Button(ButtonClick(182, 8)))

        assertTrue(player.logoutRequested)
    }

    @Test
    fun `generic button dispatch preserves operation slot and object for feature content`() {
        val (_, player) = player()
        var received: ButtonClick? = null
        val scripts =
            PlayerScriptRepository.build(
                UiComponentMap.parse("[components]\n\"test:button\" = 11927560"),
            ) {
                onButton("test:button") { received = lastButton }
            }
        val actions =
            PlayerActions(
                worldMap(),
                PlayerScriptRunner(scripts),
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            )
        val click = ButtonClick(182, 8, sub = 3, obj = 4_151, op = 4)

        actions.apply(player, PlayerAction.Button(click))

        assertEquals(click, received)
    }

    @Test
    fun `chat filters and audited public chat mutate only player state`() {
        val audits = mutableListOf<ChatAuditMessage>()
        val (_, player) = player(privilege = AccountPrivilege.MODERATOR)
        val actions =
            actions(
                audit = ChatAuditSink { audits += it; true },
                clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            )
        actions.apply(player, PlayerAction.Chat(ChatFilterInput(3, 1, 2)))
        actions.apply(player, PlayerAction.Chat(PublicChatInput(1, 2, "hello")))

        assertEquals(3, player.chatFilters.publicMode)
        assertEquals(1, player.chatFilters.privateMode)
        assertEquals(2, player.chatFilters.tradeMode)
        assertEquals("hello", audits.single().text)
        assertEquals("hello", assertNotNull(player.publicChat).text)
        assertEquals(AccountPrivilege.MODERATOR.level, player.staffModLevel.value)

        val (_, rejected) = player(id = 2)
        actions(audit = ChatAuditSink { false }).apply(
            rejected,
            PlayerAction.Chat(PublicChatInput(0, 0, "not audited")),
        )
        assertNull(rejected.publicChat)
    }

    @Test
    fun `administrator bot command is selected on the world thread and queues feedback`() {
        val (_, player) = player(privilege = AccountPrivilege.ADMINISTRATOR)
        var requested = 0
        val commands =
            buildPlayerCommandRepository { count ->
                requested = count
                BotClientRequestResult.Accepted(count, count)
            }
        val actions = actions(commands = commands)

        actions.apply(player, PlayerAction.Command(PlayerCommandInput("addbots 2")))

        assertEquals(2, requested)
        assertEquals(
            listOf("Starting 2 automated player client(s); 2 slot(s) reserved."),
            player.takeGameMessages(),
        )
    }

    @Test
    fun `player controls defer modal close and request the idle logout path`() {
        val (_, player) = player()
        player.interfaces.openModal(Component.of(161, 500), 200)
        val actions = actions()

        actions.apply(player, PlayerAction.CloseModal)
        actions.apply(player, PlayerAction.IdleLogout)

        assertTrue(player.interfaces.hasModal())
        assertTrue(player.idleLogoutRequested)
        assertFalse(player.logoutRequested)
    }

    @Test
    fun `route action resumes tile selection without also walking the player`() {
        val (world, player) = player(position = CharacterPosition(3_200, 3_200, 0))
        var selected: Tile? = null
        val repository =
            PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {
                onLogin { selected = pickTile("Choose a tile.") }
            }
        val runner = PlayerScriptRunner(repository)
        val actions =
            PlayerActions(
                worldMap(),
                runner,
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            )
        assertTrue(runner.trigger(player, ServerTriggerType.LOGIN))

        actions.apply(player, PlayerAction.Route(3_205, 3_206))
        world.advanceMovement(player)

        assertEquals(Tile(3_205, 3_206), selected)
        assertEquals(CharacterPosition(3_200, 3_200, 0), player.movement.position.toPosition())
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `cache-backed challenge portal enters a private empty Inferno`() {
        val config = InfernoFreeModeCatalog.load()
        val (_, player) = player(position = config.clanWarsArrival.toPosition())
        val portalTile = Tile(3_126, 3_620)
        val portal =
            Loc(
                type = config.challengePortalType,
                tile = portalTile,
                shape = 10,
                angle = 1,
                width = 1,
                length = 4,
                options = setOf(1, 2, 3),
            )
        val map =
            GameMap(
                OpenCollisionMap,
                locs = LocRepository { type, tile -> portal.takeIf { it.type == type && it.tile == tile } },
            )
        val actions =
            PlayerActions(
                map,
                PlayerScriptRunner(content(map)),
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            )

        actions.apply(
            player,
            PlayerAction.LocOp(
                LocOpInput(config.challengePortalType, portalTile.x, portalTile.y, option = 1),
            ),
        )

        assertEquals(config.arenaArrival, player.movement.position)
        assertEquals(MapInstance.privateTo(player.id), player.mapInstance)
        assertEquals(
            listOf(
                "Inferno free mode started empty and paused.",
                "Equipment tab: choose gear, place NPCs, pause, or clear the arena.",
            ),
            player.takeGameMessages(),
        )
    }

    @Test
    fun `loc action rejects a valid type when the player is not adjacent`() {
        val (_, player) = player(position = CharacterPosition(3_120, 3_620, 0))
        val portal =
            Loc(
                type = 26_642,
                tile = Tile(3_126, 3_620),
                shape = 10,
                angle = 1,
                width = 1,
                length = 4,
                options = setOf(1),
            )
        var triggered = false
        val scripts =
            PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {
                onLoc1(portal.type) { triggered = true }
            }
        val map =
            GameMap(
                OpenCollisionMap,
                locs = LocRepository { type, tile -> portal.takeIf { it.type == type && it.tile == tile } },
            )

        PlayerActions(
            map,
            PlayerScriptRunner(scripts),
            PlayerCommandRepositoryBuilder().build(),
            ChatAuditSink { true },
        ).apply(
            player,
            PlayerAction.LocOp(LocOpInput(portal.type, portal.tile.x, portal.tile.y, option = 1)),
        )

        assertFalse(triggered)
        assertEquals(CharacterPosition(3_120, 3_620, 0), player.movement.position.toPosition())
    }

    private fun actions(
        audit: ChatAuditSink = ChatAuditSink { true },
        clock: Clock = Clock.systemUTC(),
        commands: PlayerCommandRepository = PlayerCommandRepositoryBuilder().build(),
    ): PlayerActions {
        val map = worldMap()
        return PlayerActions(map, PlayerScriptRunner(content(map)), commands, audit, clock)
    }

    private fun content(map: GameMap) =
        PlayerContentCatalog.load(
            UiContentCatalog.load(),
            ObjCatalog.EMPTY,
            InfernoArena(map, NpcCatalog.EMPTY, NpcList(), InfernoFreeModeCatalog.load()),
        )

    private fun worldMap() = GameMap(OpenCollisionMap)

    private fun player(
        id: Long = 1,
        position: CharacterPosition = CharacterPosition(3_222, 3_218, 0),
        privilege: AccountPrivilege = AccountPrivilege.PLAYER,
    ): Pair<World, Player> {
        val world = testWorld(maxPlayerIndex = 1)
        val player =
            world.addTestPlayer(
                CharacterRecord(id, "Player$id", position, 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
                privilege = privilege,
            )
        player.activate(UiContentCatalog.load().gameframe)
        return world to player
    }

    private fun Tile.toPosition() = CharacterPosition(x, y, plane)
}
