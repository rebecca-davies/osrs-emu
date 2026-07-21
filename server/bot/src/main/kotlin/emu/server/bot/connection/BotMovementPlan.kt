package emu.server.bot.connection

import emu.server.bot.config.BotMovementConfig
import java.util.SplittableRandom

/** Chooses changing destinations inside one bounded square around the configured centre. */
internal class BotMovementPlan(
    config: BotMovementConfig,
    seed: Long,
) {
    private val random = SplittableRandom(seed)
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
