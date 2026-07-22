package emu.game.player.stat

/** Revision-pinned player skills in client protocol order. */
enum class Skill(
    val id: Int,
    val displayName: String,
) {
    ATTACK(0, "Attack"),
    DEFENCE(1, "Defence"),
    STRENGTH(2, "Strength"),
    HITPOINTS(3, "Hitpoints"),
    RANGED(4, "Ranged"),
    PRAYER(5, "Prayer"),
    MAGIC(6, "Magic"),
    COOKING(7, "Cooking"),
    WOODCUTTING(8, "Woodcutting"),
    FLETCHING(9, "Fletching"),
    FISHING(10, "Fishing"),
    FIREMAKING(11, "Firemaking"),
    CRAFTING(12, "Crafting"),
    SMITHING(13, "Smithing"),
    MINING(14, "Mining"),
    HERBLORE(15, "Herblore"),
    AGILITY(16, "Agility"),
    THIEVING(17, "Thieving"),
    SLAYER(18, "Slayer"),
    FARMING(19, "Farming"),
    RUNECRAFT(20, "Runecraft"),
    HUNTER(21, "Hunter"),
    CONSTRUCTION(22, "Construction"),
}
