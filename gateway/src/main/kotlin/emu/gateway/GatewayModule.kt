package emu.gateway

import emu.cache.store.Store
import emu.crypto.RsaKeyPair
import emu.gateway.js5.handler.Js5RequestHandler
import org.koin.dsl.module

/**
 * Declares the gateway's per-packet handlers that have Koin-injectable dependencies — currently
 * just [Js5RequestHandler] (needs the cache [store]). [Js5ControlHandler][emu.gateway.js5.handler.Js5ControlHandler]
 * is deliberately NOT declared here: its XOR-cipher dependency is per-connection mutable state, not
 * a Koin singleton (CLAUDE.md §5a addendum — "ciphers stay per-connection as today"), so it is still
 * constructed directly in [emu.gateway.js5.installJs5Handlers] once per connection.
 *
 * A factory function (rather than a top-level `val module`) because [store]/[rsaKeyPair] are loaded
 * from I/O ([main]'s `cacheDir()`/`loadServerRsaKeyPair()`) before Koin starts; this also lets tests
 * supply their own fixtures (e.g. a temp-directory [Store]) without touching real files.
 */
fun gatewayModule(store: Store, rsaKeyPair: RsaKeyPair?) = module {
    single { store }
    single { rsaKeyPair }
    single { Js5RequestHandler(get()) }
}
