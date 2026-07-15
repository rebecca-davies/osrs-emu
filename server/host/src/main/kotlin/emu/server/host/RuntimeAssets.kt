package emu.server.host

import emu.cache.store.FlatFileStore
import emu.cache.store.Store
import emu.crypto.RsaKeyPair

/** Cache storage and RSA material loaded at process startup. */
data class RuntimeAssets(
    val store: Store,
    val rsaKeyPair: RsaKeyPair,
)

/** Opens cache storage and requires the configured login RSA key. */
fun loadRuntimeAssets(config: RuntimeAssetConfig): RuntimeAssets =
    RuntimeAssets(
        store = FlatFileStore(config.cacheDirectory),
        rsaKeyPair = loadServerRsaKeyPair(config.rsaPropertiesFile),
    )
