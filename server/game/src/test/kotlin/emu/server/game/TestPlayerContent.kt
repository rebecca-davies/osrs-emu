package emu.server.game

import emu.compression.HuffmanCodec
import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.GameMap
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.obj.ObjCatalog
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.script.execution.PlayerScriptRunner
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.server.game.network.output.PlayerOutput
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.World
import emu.server.game.world.cycle.PlayerPhase
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.player.PlayerLifecycle
import emu.server.game.world.player.action.PlayerActions
import emu.server.game.world.player.command.PlayerCommandRepository
import emu.server.game.world.player.command.PlayerCommandRepositoryBuilder

/** Shared immutable Kotlin-content runtime used by world tests. */
internal object TestPlayerContent {
    private val repository =
        PlayerContentCatalog.load(
            UiContentCatalog.load(),
            ObjCatalog.EMPTY,
            InfernoArena(
                GameMap(OpenCollisionMap),
                NpcCatalog.EMPTY,
                NpcList(),
                InfernoFreeModeCatalog.load(),
            ),
        )
    private val scripts = PlayerScriptRunner(repository)
    private val huffman = HuffmanCodec(ByteArray(256) { 8 })

    fun actions(
        audit: ChatAuditSink = ChatAuditSink { true },
        commands: PlayerCommandRepository = PlayerCommandRepositoryBuilder().build(),
    ) = PlayerActions(GameMap(OpenCollisionMap), scripts, commands, audit)

    fun playerPhase() = PlayerPhase(scripts)

    fun lifecycle(
        world: World,
        writes: CharacterWriteQueue,
        nanoTime: () -> Long = System::nanoTime,
    ) = PlayerLifecycle(world, writes, scripts, nanoTime)

    fun output(world: World) = PlayerOutput(world, huffman, UiContentCatalog.load().gameframe)

    fun cycle(
        world: World,
        commands: WorldCommandQueue = WorldCommandQueue(256),
        writes: CharacterWriteQueue = CharacterWriteQueue { DurableCharacterWrite },
        audit: ChatAuditSink = ChatAuditSink { true },
    ) =
        WorldCycle(
            world,
            commands,
            actions(audit),
            playerPhase(),
            lifecycle(world, writes),
            output(world),
        )
}
