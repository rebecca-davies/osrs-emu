package emu.server.bot.behavior

import emu.server.bot.config.BotMovementConfig
import kotlinx.coroutines.delay

/** Creates automated players that repeatedly choose destinations inside one bounded area. */
class WanderingBotBehaviorFactory(
    private val config: BotMovementConfig,
) : BotBehaviorFactory {
    override fun create(seed: Long): BotBehavior = WanderingBotBehavior(config, seed)
}

private class WanderingBotBehavior(
    private val config: BotMovementConfig,
    seed: Long,
) : BotBehavior {
    private val initialDelayMillis = Math.floorMod(seed, config.interval.inWholeMilliseconds)
    private val movement = BotMovementPlan(config, seed)

    override suspend fun run(client: BotClient) {
        delay(initialDelayMillis)
        while (true) {
            movement.chooseNext()
            client.walkTo(movement.x, movement.z)
            delay(config.interval)
        }
    }
}

private class BotMovementPlan(
    config: BotMovementConfig,
    seed: Long,
) {
    private val random = java.util.SplittableRandom(seed)
    private val minX = config.centreX - config.radius
    private val maxX = config.centreX + config.radius
    private val minZ = config.centreZ - config.radius
    private val maxZ = config.centreZ + config.radius

    var x: Int = config.centreX
        private set
    var z: Int = config.centreZ
        private set

    fun chooseNext() {
        val previousX = x
        val previousZ = z
        do {
            x = random.nextInt(minX, maxX + 1)
            z = random.nextInt(minZ, maxZ + 1)
        } while (x == previousX && z == previousZ)
    }
}
