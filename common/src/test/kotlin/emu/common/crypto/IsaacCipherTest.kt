package emu.common.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class IsaacCipherTest {
    @Test fun `matches client golden vector for seed 1,2,3,4`() {
        val expected = this::class.java.getResourceAsStream("/isaac-golden.txt")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }.map { it.trim().toInt() }
        val isaac = IsaacCipher(intArrayOf(1, 2, 3, 4))
        val actual = List(expected.size) { isaac.nextInt() }
        assertEquals(expected, actual)
    }
}
