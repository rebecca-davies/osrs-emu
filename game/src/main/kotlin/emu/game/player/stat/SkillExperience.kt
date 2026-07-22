package emu.game.player.stat

/** RuneScape experience thresholds for base levels 1 through 99. */
object SkillExperience {
    private val thresholds =
        IntArray(MAX_LEVEL + 1).also { values ->
            var points = 0
            for (level in 1 until MAX_LEVEL) {
                points += level + (300.0 * Math.pow(2.0, level / 7.0)).toInt()
                values[level + 1] = points / 4
            }
        }

    fun forLevel(level: Int): Int {
        require(level in MIN_LEVEL..MAX_LEVEL) { "skill level must be in $MIN_LEVEL..$MAX_LEVEL" }
        return thresholds[level]
    }

    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 99
}
