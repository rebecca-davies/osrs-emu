package emu.game.content.beta.supplies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TournamentSuppliesConfigParserTest {
    @Test
    fun `parser reads bounded beta-world catalogue policy`() {
        val config =
            TournamentSuppliesConfigParser.parse(
                """
                [supplies]
                preview_inventory = 207
                item_catalogue_enum = 1124
                item_stack_size = 10000
                """.trimIndent(),
            )

        assertEquals(TournamentSuppliesConfig(207, 1124, 10_000), config)
    }

    @Test
    fun `parser rejects out-of-range catalogue policy`() {
        listOf(
            policy(previewInventory = 65_536),
            policy(itemCatalogueEnum = 65_536),
            policy(itemStackSize = 0),
        ).forEach { source ->
            assertFailsWith<IllegalArgumentException> {
                TournamentSuppliesConfigParser.parse(source)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            TournamentSuppliesConfigParser.parse(" ".repeat(16_385))
        }
    }

    private fun policy(
        previewInventory: Int = 207,
        itemCatalogueEnum: Int = 1_124,
        itemStackSize: Int = 10_000,
    ): String =
        """
        [supplies]
        preview_inventory = $previewInventory
        item_catalogue_enum = $itemCatalogueEnum
        item_stack_size = $itemStackSize
        """.trimIndent()
}
