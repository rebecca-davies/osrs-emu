package emu.server.game.world.player.action

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.chat.ChatFilterInput
import emu.game.chat.PublicChatInput
import emu.game.command.PlayerCommandInput
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.beta.BetaWorldContentCatalog
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
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
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
import emu.server.game.testNpcTargets
import emu.server.game.world.World
import emu.server.game.world.addTestPlayer
import emu.server.game.world.player.command.PlayerCommandRepository
import emu.server.game.world.player.command.PlayerCommandRepositoryBuilder
import emu.server.game.world.player.command.bot.BotClientRequestResult
import emu.server.game.world.player.command.buildPlayerCommandRepository
import emu.server.game.world.player.interaction.PlayerInteractionProcess
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
                testNpcTargets(),
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
                testNpcTargets(),
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
        val runner = PlayerScriptRunner(content(map))
        val actions =
            PlayerActions(
                map,
                testNpcTargets(),
                runner,
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            )

        actions.apply(
            player,
            PlayerAction.LocOp(
                LocOpInput(config.challengePortalType, portalTile.x, portalTile.y, option = 1),
            ),
        )

        assertEquals(config.clanWarsArrival, player.movement.position)
        assertNotNull(player.interaction)
        PlayerInteractionProcess(map, runner, testNpcTargets()).beforeMovement(player)

        assertEquals(config.arenaArrival, player.movement.position)
        assertEquals(MapInstance.privateTo(player.id), player.mapInstance)
        assertEquals(
            listOf(
                "Inferno free mode started empty and paused.",
                "Use the Inferno Editor quest tab to configure the arena.",
            ),
            player.takeGameMessages(),
        )
    }

    @Test
    fun `loc action closes a modal and completes through the player phase`() {
        val (_, player) = player(position = CharacterPosition(3_120, 3_620, 0))
        val loc =
            Loc(
                type = 1,
                tile = Tile(3_121, 3_620),
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        val map = GameMap(OpenCollisionMap, LocRepository { type, tile -> loc.takeIf { type == 1 && tile == loc.tile } })
        var triggered = false
        val runner =
            PlayerScriptRunner(
                PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {
                    onLoc1(loc.type) { triggered = true }
                },
            )
        player.interfaces.openModal(Component.of(161, 50), 200)

        PlayerActions(
            map,
            testNpcTargets(),
            runner,
            PlayerCommandRepositoryBuilder().build(),
            ChatAuditSink { true },
        ).apply(
            player,
            PlayerAction.LocOp(LocOpInput(loc.type, loc.tile.x, loc.tile.y, option = 1)),
        )

        assertFalse(player.interfaces.hasModal())
        assertFalse(triggered)
        assertNotNull(player.interaction)
        PlayerInteractionProcess(map, runner, testNpcTargets()).beforeMovement(player)
        assertTrue(triggered)
        assertNull(player.interaction)
    }

    @Test
    fun `invalid modal loc action preserves an established walking route`() {
        val (_, player) = player(position = CharacterPosition(3_120, 3_620, 0))
        val map = GameMap(OpenCollisionMap)
        val previousTarget =
            Loc(
                type = 1,
                tile = Tile(3_128, 3_620),
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        player.walkTo(Tile(3_125, 3_620))
        map.resolveRoute(player)
        player.beginInteraction(
            PlayerInteraction.LocOp(
                previousTarget,
                option = 1,
                subOption = 0,
                mapInstance = player.mapInstance,
            ),
        )
        player.pathTo(previousTarget)
        player.interfaces.openModal(Component.of(161, 50), 200)

        PlayerActions(
            map,
            testNpcTargets(),
            PlayerScriptRunner(PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {}),
            PlayerCommandRepositoryBuilder().build(),
            ChatAuditSink { true },
        ).apply(
            player,
            PlayerAction.LocOp(
                LocOpInput(previousTarget.type + 1, previousTarget.tile.x, previousTarget.tile.y, option = 1),
            ),
        )

        assertFalse(player.interfaces.hasModal())
        assertNull(player.interaction)
        assertNull(map.resolveRoute(player))
        assertTrue(player.movement.hasRoute)
        map.advance(player)
        assertEquals(CharacterPosition(3_121, 3_620, 0), player.movement.position.toPosition())
    }

    @Test
    fun `control temporarily reverses run for a loc route`() {
        val (_, player) = player(position = CharacterPosition(3_120, 3_620, 0))
        val loc =
            Loc(
                type = 1,
                tile = Tile(3_128, 3_620),
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        val map = GameMap(OpenCollisionMap, LocRepository { type, tile -> loc.takeIf { type == 1 && tile == loc.tile } })
        val runner = PlayerScriptRunner(PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {})

        PlayerActions(
            map,
            testNpcTargets(),
            runner,
            PlayerCommandRepositoryBuilder().build(),
            ChatAuditSink { true },
        ).apply(
            player,
            PlayerAction.LocOp(
                LocOpInput(loc.type, loc.tile.x, loc.tile.y, option = 1, controlKey = true),
            ),
        )
        map.resolveRoute(player)
        val interactions = PlayerInteractionProcess(map, runner, testNpcTargets())
        interactions.beforeMovement(player)
        map.advance(player)
        interactions.afterMovement(player)

        assertFalse(player.movement.runEnabled)
        assertEquals(CharacterPosition(3_122, 3_620, 0), player.movement.position.toPosition())
    }

    @Test
    fun `Inferno exit portal click paths back to the shared hub`() {
        val config = InfernoFreeModeCatalog.load()
        val (_, player) = player(position = config.clanWarsArrival.toPosition())
        val exit =
            Loc(
                type = config.exitPortalType,
                tile = Tile(2_269, 5_325),
                shape = 10,
                angle = 0,
                width = 5,
                length = 4,
                options = setOf(1),
            )
        val map = GameMap(OpenCollisionMap, LocRepository { type, tile -> exit.takeIf { type == exit.type && tile == exit.tile } })
        val arena = InfernoArena(map, NpcCatalog.EMPTY, NpcList(), config)
        val runner = PlayerScriptRunner(BetaWorldContentCatalog.load(UiContentCatalog.load(), arena))
        val actions =
            PlayerActions(
                map,
                testNpcTargets(),
                runner,
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            )
        arena.enter(player)
        player.buildArea.recenterIfRequired(player.movement.position)
        player.finishCycle()

        actions.apply(
            player,
            PlayerAction.LocOp(LocOpInput(exit.type, exit.tile.x, exit.tile.y, option = 1)),
        )
        map.resolveRoute(player)
        val interactions = PlayerInteractionProcess(map, runner, testNpcTargets())
        repeat(12) {
            interactions.beforeMovement(player)
            map.advance(player)
            interactions.afterMovement(player)
        }

        assertEquals(config.clanWarsArrival, player.movement.position)
        assertEquals(MapInstance.SHARED, player.mapInstance)
        assertNull(player.interaction)
        assertEquals(listOf("You leave the Inferno."), player.takeGameMessages())
    }

    @Test
    fun `loc action paths to the authoritative footprint before triggering content`() {
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

        val runner = PlayerScriptRunner(scripts)
        val actions = PlayerActions(
            map,
            testNpcTargets(),
            runner,
            PlayerCommandRepositoryBuilder().build(),
            ChatAuditSink { true },
        )
        actions.apply(
            player,
            PlayerAction.LocOp(LocOpInput(portal.type, portal.tile.x, portal.tile.y, option = 1)),
        )

        assertFalse(triggered)
        assertNotNull(player.interaction)
        map.resolveRoute(player)
        val interactions = PlayerInteractionProcess(map, runner, testNpcTargets())
        repeat(10) {
            interactions.beforeMovement(player)
            map.advance(player)
            interactions.afterMovement(player)
        }

        assertTrue(triggered)
        assertTrue(map.canReachLoc(player.movement.position, portal))
        assertNull(player.interaction)
    }

    @Test
    fun `new invalid loc action cancels the previous interaction`() {
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
        val map =
            GameMap(
                OpenCollisionMap,
                locs = LocRepository { type, tile -> portal.takeIf { it.type == type && it.tile == tile } },
            )
        val actions =
            PlayerActions(
                map,
                testNpcTargets(),
                PlayerScriptRunner(PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {}),
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            )

        actions.apply(
            player,
            PlayerAction.LocOp(LocOpInput(portal.type, portal.tile.x, portal.tile.y, option = 1)),
        )
        assertNotNull(player.interaction)

        actions.apply(
            player,
            PlayerAction.LocOp(LocOpInput(portal.type + 1, portal.tile.x, portal.tile.y, option = 1)),
        )

        assertNull(player.interaction)
        assertNull(map.resolveRoute(player))
        map.advance(player)
        assertEquals(CharacterPosition(3_120, 3_620, 0), player.movement.position.toPosition())
        assertFalse(player.movement.hasRoute)
    }

    @Test
    fun `loc action rejects an authoritative placement outside the current build area`() {
        val (_, player) = player(position = CharacterPosition(3_100, 3_620, 0))
        val portal =
            Loc(
                type = 26_642,
                tile = Tile(3_200, 3_620),
                shape = 10,
                angle = 1,
                width = 1,
                length = 4,
                options = setOf(1),
            )
        val map =
            GameMap(
                OpenCollisionMap,
                locs = LocRepository { type, tile -> portal.takeIf { it.type == type && it.tile == tile } },
            )

        PlayerActions(
            map,
            testNpcTargets(),
            PlayerScriptRunner(PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {}),
            PlayerCommandRepositoryBuilder().build(),
            ChatAuditSink { true },
        ).apply(
            player,
            PlayerAction.LocOp(LocOpInput(portal.type, portal.tile.x, portal.tile.y, option = 1)),
        )

        assertEquals(CharacterPosition(3_100, 3_620, 0), player.movement.position.toPosition())
        assertNull(player.interaction)
    }

    private fun actions(
        audit: ChatAuditSink = ChatAuditSink { true },
        clock: Clock = Clock.systemUTC(),
        commands: PlayerCommandRepository = PlayerCommandRepositoryBuilder().build(),
    ): PlayerActions {
        val map = worldMap()
        return PlayerActions(map, testNpcTargets(), PlayerScriptRunner(content(map)), commands, audit, clock)
    }

    private fun content(map: GameMap) =
        BetaWorldContentCatalog.load(
            UiContentCatalog.load(),
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
