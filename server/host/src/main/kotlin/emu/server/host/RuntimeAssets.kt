package emu.server.host

import emu.cache.store.FlatFileStore
import emu.cache.store.Store
import emu.crypto.RsaKeyPair
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

data class RuntimeAssets(
    val store: Store,
    val rsaKeyPair: RsaKeyPair?,
)

/** Opens cache storage and the optional login RSA key. */
fun loadRuntimeAssets(config: ServerConfig): RuntimeAssets =
    RuntimeAssets(
        store = FlatFileStore(config.cacheDirectory),
        rsaKeyPair =
            try {
                loadServerRsaKeyPair(config.rsaPropertiesFile)
            } catch (failure: Exception) {
                logger.warn(failure) { "login RSA key is unavailable; login connections will be rejected" }
                null
            },
    )
