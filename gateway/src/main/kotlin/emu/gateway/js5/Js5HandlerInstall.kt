package emu.gateway.js5

import emu.cache.store.Store
import emu.crypto.XorStreamCipher
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.js5.Js5Control
import emu.protocol.osrs239.js5.Js5Request

/**
 * Binds the JS5 domain's per-packet handlers — [Js5RequestHandler] and [Js5ControlHandler] — into a
 * [HandlerRepositoryBuilder] (CLAUDE.md §5a design doc, section B). Lives in `gateway` rather than
 * `protocol-osrs239` (unlike `installJs5`'s codecs) because the handlers need the cache [store] and
 * the connection's [cipher]; both are supplied by the caller and constructed here per connection
 * (`Main.kt`'s `runJs5Pipeline`), since [cipher] is per-connection mutable state.
 */
fun HandlerRepositoryBuilder.installJs5Handlers(store: Store, cipher: XorStreamCipher): HandlerRepositoryBuilder = this
    .bind(Js5Request::class.java, Js5RequestHandler(store))
    .bind(Js5Control::class.java, Js5ControlHandler(cipher))
