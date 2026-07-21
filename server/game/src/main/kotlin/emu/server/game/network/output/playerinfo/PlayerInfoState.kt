package emu.server.game.network.output.playerinfo

import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoBitCode
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoSections
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoUpdate
import emu.protocol.osrs239.game.message.playerinfo.PlayerMovement

/** Per-observer rev-239 GPI flags, tracked slots, coordinates, and movement-speed cache. */
internal class PlayerInfoState(private val localIndex: Int) {
    private val tracked = BooleanArray(PLAYER_SLOTS)
    private val flags = ByteArray(PLAYER_SLOTS)
    private val additions = BooleanArray(PLAYER_SLOTS)
    private val removals = BooleanArray(PLAYER_SLOTS)
    private val knownX = IntArray(PLAYER_SLOTS)
    private val knownY = IntArray(PLAYER_SLOTS)
    private val knownPlane = IntArray(PLAYER_SLOTS)
    private val lowRegionX = IntArray(PLAYER_SLOTS)
    private val lowRegionY = IntArray(PLAYER_SLOTS)
    private val lowRegionPlane = IntArray(PLAYER_SLOTS)
    private val knownMoveSpeed = IntArray(PLAYER_SLOTS) { STATIONARY_SPEED }

    init {
        require(localIndex in 1 until PLAYER_SLOTS) { "local player index must be in 1..2047" }
        tracked[localIndex] = true
    }

    fun next(view: PlayerInfoView): PlayerInfo {
        val observer = checkNotNull(view[localIndex]) { "active observer $localIndex is absent from player-info view" }
        additions.fill(false)
        removals.fill(false)

        var survivingPlayers = 0
        for (index in 1 until PLAYER_SLOTS) {
            if (!tracked[index] || index == localIndex) continue
            val target = view[index]
            if (target != null && view.isVisible(observer, target)) {
                survivingPlayers++
            } else {
                removals[index] = true
            }
        }
        val available = (PREFERRED_PLAYERS - 1 - survivingPlayers).coerceAtLeast(0)
        view.selectAdditions(observer, tracked, additions, available)

        val highActive = buildSection(view, highResolution = true, inactive = false)
        val highInactive = buildSection(view, highResolution = true, inactive = true)
        val lowInactive = buildSection(view, highResolution = false, inactive = true)
        val lowActive = buildSection(view, highResolution = false, inactive = false)
        finishCycle()
        return PlayerInfo(PlayerInfoSections(highActive, highInactive, lowInactive, lowActive))
    }

    private fun buildSection(
        view: PlayerInfoView,
        highResolution: Boolean,
        inactive: Boolean,
    ): List<PlayerInfoBitCode> {
        val codes = mutableListOf<PlayerInfoBitCode>()
        var skipped = 0
        for (index in 1 until PLAYER_SLOTS) {
            if (tracked[index] != highResolution || isInactive(index) != inactive) continue
            val code = if (highResolution) highResolutionCode(view, index) else lowResolutionCode(view, index)
            if (code == null) {
                skipped++
                flags[index] = (flags[index].toInt() or NEXT_CYCLE_INACTIVE).toByte()
                continue
            }
            if (skipped > 0) {
                codes += PlayerInfoBitCode.Skip(skipped)
                skipped = 0
            }
            codes += code
            if (code is PlayerInfoBitCode.Add) {
                flags[index] = (flags[index].toInt() or NEXT_CYCLE_INACTIVE).toByte()
            }
        }
        if (skipped > 0) codes += PlayerInfoBitCode.Skip(skipped)
        return codes
    }

    private fun highResolutionCode(view: PlayerInfoView, index: Int): PlayerInfoBitCode? {
        if (removals[index]) {
            lowRegionPlane[index] = knownPlane[index]
            lowRegionX[index] = knownX[index] ushr REGION_SHIFT
            lowRegionY[index] = knownY[index] ushr REGION_SHIFT
            return PlayerInfoBitCode.Remove()
        }
        val snapshot = checkNotNull(view[index]) { "tracked player $index is absent without a removal" }
        remember(snapshot)
        val update = extendedInfo(snapshot, includeAppearance = false, includeTemporarySpeed = true)
        return if (update == null) {
            snapshot.movementOnlyCode
        } else {
            PlayerInfoBitCode.HighResolution(snapshot.protocolMovement, update)
        }
    }

    private fun lowResolutionCode(view: PlayerInfoView, index: Int): PlayerInfoBitCode? {
        if (!additions[index]) return null
        val snapshot = checkNotNull(view[index]) { "newly visible player $index is absent" }
        val targetRegionX = snapshot.position.x ushr REGION_SHIFT
        val targetRegionY = snapshot.position.y ushr REGION_SHIFT
        val targetPlane = snapshot.position.plane
        val regionChange =
            if (
                lowRegionX[index] == targetRegionX &&
                    lowRegionY[index] == targetRegionY &&
                    lowRegionPlane[index] == targetPlane
            ) {
                null
            } else {
                PlayerInfoBitCode.LowResolution.Region(
                    planeDelta = (targetPlane - lowRegionPlane[index]) and 3,
                    deltaX = (targetRegionX - lowRegionX[index]) and 0xFF,
                    deltaY = (targetRegionY - lowRegionY[index]) and 0xFF,
                )
            }
        lowRegionX[index] = targetRegionX
        lowRegionY[index] = targetRegionY
        lowRegionPlane[index] = targetPlane
        remember(snapshot)
        val update = checkNotNull(extendedInfo(snapshot, includeAppearance = true, includeTemporarySpeed = false))
        return PlayerInfoBitCode.Add(
            x = snapshot.position.x and LOCAL_COORDINATE_MASK,
            y = snapshot.position.y and LOCAL_COORDINATE_MASK,
            update = update,
            regionChange = regionChange,
        )
    }

    private fun extendedInfo(
        snapshot: PlayerInfoSnapshot,
        includeAppearance: Boolean,
        includeTemporarySpeed: Boolean,
    ): PlayerInfoUpdate? {
        val selectedSpeed = if (snapshot.runEnabled) RUN_SPEED else WALK_SPEED
        val moveSpeed = selectedSpeed.takeIf { it != knownMoveSpeed[snapshot.index] }
        knownMoveSpeed[snapshot.index] = selectedSpeed
        val temporarySpeed =
            if (includeTemporarySpeed && snapshot.protocolMovement != null) {
                val actual = if (snapshot.protocolMovement is PlayerMovement.Run) RUN_SPEED else WALK_SPEED
                actual.takeIf { it != selectedSpeed }
            } else {
                null
            }
        if (!includeAppearance && moveSpeed == null && temporarySpeed == null && snapshot.publicChat == null) return null
        return PlayerInfoUpdate(
            appearance = snapshot.appearance.takeIf { includeAppearance },
            moveSpeed = moveSpeed,
            temporaryMoveSpeed = temporarySpeed,
            publicChat = snapshot.publicChat,
        )
    }

    private fun remember(snapshot: PlayerInfoSnapshot) {
        knownX[snapshot.index] = snapshot.position.x
        knownY[snapshot.index] = snapshot.position.y
        knownPlane[snapshot.index] = snapshot.position.plane
    }

    private fun finishCycle() {
        for (index in 1 until PLAYER_SLOTS) {
            if (removals[index]) {
                tracked[index] = false
                knownMoveSpeed[index] = STATIONARY_SPEED
            }
            if (additions[index]) tracked[index] = true
            flags[index] = (flags[index].toInt() ushr 1).toByte()
        }
    }

    private fun isInactive(index: Int): Boolean = flags[index].toInt() and CURRENT_CYCLE_INACTIVE != 0

    private companion object {
        const val PLAYER_SLOTS = 2_048
        const val PREFERRED_PLAYERS = 250
        const val REGION_SHIFT = 13
        const val LOCAL_COORDINATE_MASK = 0x1FFF
        const val CURRENT_CYCLE_INACTIVE = 0x1
        const val NEXT_CYCLE_INACTIVE = 0x2
        const val WALK_SPEED = 1
        const val RUN_SPEED = 2
        const val STATIONARY_SPEED = 127
    }
}
