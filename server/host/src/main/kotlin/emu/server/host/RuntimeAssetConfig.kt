package emu.server.host

import java.io.File

/** Cache and login-key paths required before peer services can be composed. */
data class RuntimeAssetConfig(
    val cacheDirectory: File,
    val rsaPropertiesFile: File,
)
