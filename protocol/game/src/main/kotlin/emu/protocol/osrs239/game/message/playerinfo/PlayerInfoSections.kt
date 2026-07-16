package emu.protocol.osrs239.game.message.playerinfo

/** Four byte-aligned rev-239 GPI sections in client decode order. */
data class PlayerInfoSections(
    val highResolutionActive: List<PlayerInfoBitCode> = emptyList(),
    val highResolutionInactive: List<PlayerInfoBitCode> = emptyList(),
    val lowResolutionInactive: List<PlayerInfoBitCode> = emptyList(),
    val lowResolutionActive: List<PlayerInfoBitCode> = emptyList(),
) {
    val ordered: List<List<PlayerInfoBitCode>>
        get() =
            listOf(
                highResolutionActive,
                highResolutionInactive,
                lowResolutionInactive,
                lowResolutionActive,
            )
}
