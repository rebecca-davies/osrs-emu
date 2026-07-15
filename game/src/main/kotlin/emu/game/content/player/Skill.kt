package emu.game.content.player

/** Revision-pinned player skills in client protocol order. */
enum class Skill(
    val id: Int,
    val initialLevel: Int = 1,
    val initialExperience: Int = 0,
) {
    ATTACK(0),
    DEFENCE(1),
    STRENGTH(2),
    HITPOINTS(3, initialLevel = 10, initialExperience = 1_154),
    RANGED(4),
    PRAYER(5),
    MAGIC(6),
    COOKING(7),
    WOODCUTTING(8),
    FLETCHING(9),
    FISHING(10),
    FIREMAKING(11),
    CRAFTING(12),
    SMITHING(13),
    MINING(14),
    HERBLORE(15),
    AGILITY(16),
    THIEVING(17),
    SLAYER(18),
    FARMING(19),
    RUNECRAFT(20),
    HUNTER(21),
    CONSTRUCTION(22),
}
