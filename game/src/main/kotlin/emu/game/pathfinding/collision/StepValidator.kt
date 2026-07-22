package emu.game.pathfinding.collision

/** Tests one allocation-free size-one step with the same destination and corner masks as Blurite. */
internal fun CollisionMap.canTravel(
    x: Int,
    y: Int,
    plane: Int,
    deltaX: Int,
    deltaY: Int,
    extraFlag: Int = 0,
): Boolean {
    require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
        "step delta must be one adjacent tile"
    }
    return when ((deltaY + 1) * 3 + deltaX + 1) {
        3 -> clear(x - 1, y, plane, CollisionFlag.BLOCK_WEST, extraFlag)
        5 -> clear(x + 1, y, plane, CollisionFlag.BLOCK_EAST, extraFlag)
        1 -> clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH, extraFlag)
        7 -> clear(x, y + 1, plane, CollisionFlag.BLOCK_NORTH, extraFlag)
        0 ->
            clear(x - 1, y - 1, plane, CollisionFlag.BLOCK_SOUTH_WEST, extraFlag) &&
                clear(x - 1, y, plane, CollisionFlag.BLOCK_WEST, extraFlag) &&
                clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH, extraFlag)
        2 ->
            clear(x + 1, y - 1, plane, CollisionFlag.BLOCK_SOUTH_EAST, extraFlag) &&
                clear(x + 1, y, plane, CollisionFlag.BLOCK_EAST, extraFlag) &&
                clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH, extraFlag)
        6 ->
            clear(x - 1, y + 1, plane, CollisionFlag.BLOCK_NORTH_WEST, extraFlag) &&
                clear(x - 1, y, plane, CollisionFlag.BLOCK_WEST, extraFlag) &&
                clear(x, y + 1, plane, CollisionFlag.BLOCK_NORTH, extraFlag)
        8 ->
            clear(x + 1, y + 1, plane, CollisionFlag.BLOCK_NORTH_EAST, extraFlag) &&
                clear(x + 1, y, plane, CollisionFlag.BLOCK_EAST, extraFlag) &&
                clear(x, y + 1, plane, CollisionFlag.BLOCK_NORTH, extraFlag)
        else -> false
    }
}

/** Tests one allocation-free step for a square entity whose coordinate is its south-west tile. */
internal fun CollisionMap.canTravel(
    x: Int,
    y: Int,
    plane: Int,
    deltaX: Int,
    deltaY: Int,
    size: Int,
    extraFlag: Int,
): Boolean {
    require(size > 0) { "entity size must be positive" }
    if (size == 1) return canTravel(x, y, plane, deltaX, deltaY, extraFlag)
    require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
        "step delta must be one adjacent tile"
    }
    return when ((deltaY + 1) * 3 + deltaX + 1) {
        3 -> canTravelWest(x, y, plane, size, extraFlag)
        5 -> canTravelEast(x, y, plane, size, extraFlag)
        1 -> canTravelSouth(x, y, plane, size, extraFlag)
        7 -> canTravelNorth(x, y, plane, size, extraFlag)
        0 -> canTravelSouthWest(x, y, plane, size, extraFlag)
        2 -> canTravelSouthEast(x, y, plane, size, extraFlag)
        6 -> canTravelNorthWest(x, y, plane, size, extraFlag)
        8 -> canTravelNorthEast(x, y, plane, size, extraFlag)
        else -> false
    }
}

private fun CollisionMap.canTravelSouth(x: Int, y: Int, plane: Int, size: Int, extraFlag: Int): Boolean {
    if (!clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH_WEST, extraFlag)) return false
    if (!clear(x + size - 1, y - 1, plane, CollisionFlag.BLOCK_SOUTH_EAST, extraFlag)) return false
    for (midX in x + 1 until x + size - 1) {
        if (!clear(midX, y - 1, plane, CollisionFlag.BLOCK_NORTH_EAST_AND_WEST, extraFlag)) return false
    }
    return true
}

private fun CollisionMap.canTravelNorth(x: Int, y: Int, plane: Int, size: Int, extraFlag: Int): Boolean {
    if (!clear(x, y + size, plane, CollisionFlag.BLOCK_NORTH_WEST, extraFlag)) return false
    if (!clear(x + size - 1, y + size, plane, CollisionFlag.BLOCK_NORTH_EAST, extraFlag)) return false
    for (midX in x + 1 until x + size - 1) {
        if (!clear(midX, y + size, plane, CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST, extraFlag)) return false
    }
    return true
}

private fun CollisionMap.canTravelWest(x: Int, y: Int, plane: Int, size: Int, extraFlag: Int): Boolean {
    if (!clear(x - 1, y, plane, CollisionFlag.BLOCK_SOUTH_WEST, extraFlag)) return false
    if (!clear(x - 1, y + size - 1, plane, CollisionFlag.BLOCK_NORTH_WEST, extraFlag)) return false
    for (midY in y + 1 until y + size - 1) {
        if (!clear(x - 1, midY, plane, CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST, extraFlag)) return false
    }
    return true
}

private fun CollisionMap.canTravelEast(x: Int, y: Int, plane: Int, size: Int, extraFlag: Int): Boolean {
    if (!clear(x + size, y, plane, CollisionFlag.BLOCK_SOUTH_EAST, extraFlag)) return false
    if (!clear(x + size, y + size - 1, plane, CollisionFlag.BLOCK_NORTH_EAST, extraFlag)) return false
    for (midY in y + 1 until y + size - 1) {
        if (!clear(x + size, midY, plane, CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST, extraFlag)) return false
    }
    return true
}

private fun CollisionMap.canTravelSouthWest(
    x: Int,
    y: Int,
    plane: Int,
    size: Int,
    extraFlag: Int,
): Boolean {
    if (!clear(x - 1, y - 1, plane, CollisionFlag.BLOCK_SOUTH_WEST, extraFlag)) return false
    for (mid in 1 until size) {
        if (!clear(x - 1, y + mid - 1, plane, CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST, extraFlag)) {
            return false
        }
        if (!clear(x + mid - 1, y - 1, plane, CollisionFlag.BLOCK_NORTH_EAST_AND_WEST, extraFlag)) {
            return false
        }
    }
    return true
}

private fun CollisionMap.canTravelSouthEast(
    x: Int,
    y: Int,
    plane: Int,
    size: Int,
    extraFlag: Int,
): Boolean {
    if (!clear(x + size, y - 1, plane, CollisionFlag.BLOCK_SOUTH_EAST, extraFlag)) return false
    for (mid in 1 until size) {
        if (!clear(x + size, y + mid - 1, plane, CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST, extraFlag)) {
            return false
        }
        if (!clear(x + mid, y - 1, plane, CollisionFlag.BLOCK_NORTH_EAST_AND_WEST, extraFlag)) {
            return false
        }
    }
    return true
}

private fun CollisionMap.canTravelNorthWest(
    x: Int,
    y: Int,
    plane: Int,
    size: Int,
    extraFlag: Int,
): Boolean {
    if (!clear(x - 1, y + size, plane, CollisionFlag.BLOCK_NORTH_WEST, extraFlag)) return false
    for (mid in 1 until size) {
        if (!clear(x - 1, y + mid, plane, CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST, extraFlag)) {
            return false
        }
        if (!clear(x + mid - 1, y + size, plane, CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST, extraFlag)) {
            return false
        }
    }
    return true
}

private fun CollisionMap.canTravelNorthEast(
    x: Int,
    y: Int,
    plane: Int,
    size: Int,
    extraFlag: Int,
): Boolean {
    if (!clear(x + size, y + size, plane, CollisionFlag.BLOCK_NORTH_EAST, extraFlag)) return false
    for (mid in 1 until size) {
        if (!clear(x + mid, y + size, plane, CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST, extraFlag)) {
            return false
        }
        if (!clear(x + size, y + mid, plane, CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST, extraFlag)) {
            return false
        }
    }
    return true
}

private fun CollisionMap.clear(
    x: Int,
    y: Int,
    plane: Int,
    mask: Int,
    extraFlag: Int,
): Boolean = flagsAt(x, y, plane) and (mask or extraFlag) == 0
