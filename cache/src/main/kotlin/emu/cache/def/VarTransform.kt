package emu.cache.def

/**
 * A varbit/varplayer-driven appearance switch, shared by objects (opcodes 77/92) and npcs
 * (opcodes 106/118) — recon doc §4a/§4b.
 *
 * [configChangeDest] holds the `length + 1` explicitly-written destination ids (each `0xFFFF`
 * decoded to `-1`). The loader stores a trailing slot too: opcodes 92/118 fill it with an extra
 * `var` u16 ([trailingVar] non-null), opcodes 77/106 leave it `-1` ([trailingVar] null) — that
 * distinction is what selects the base vs. extended opcode on re-encode.
 */
data class VarTransform(
    val varbitId: Int,
    val varpId: Int,
    val configChangeDest: List<Int>,
    val trailingVar: Int? = null,
)
