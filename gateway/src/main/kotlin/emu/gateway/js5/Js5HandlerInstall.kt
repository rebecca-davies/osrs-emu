package emu.gateway.js5

import emu.crypto.XorStreamCipher
import emu.gateway.js5.handler.Js5ControlHandler
import emu.gateway.js5.handler.Js5RequestHandler
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.js5.message.Js5Control
import emu.protocol.osrs239.js5.message.Js5Request
import org.koin.core.Koin

/**
 * Binds the JS5 domain's per-packet handlers — [Js5RequestHandler] and [Js5ControlHandler] — into a
 * [HandlerRepositoryBuilder] (CLAUDE.md §5a design doc, section B). [Js5RequestHandler] is resolved
 * from [koin] (constructor-injected with the cache `Store` via [emu.gateway.gatewayModule]);
 * [Js5ControlHandler] is still constructed directly here (not via Koin) because its [cipher]
 * dependency is per-connection mutable state, not a Koin singleton (CLAUDE.md §5a addendum).
 */
fun HandlerRepositoryBuilder.installJs5Handlers(koin: Koin, cipher: XorStreamCipher): HandlerRepositoryBuilder = this
    .bind(Js5Request::class.java, koin.get<Js5RequestHandler>())
    .bind(Js5Control::class.java, Js5ControlHandler(cipher))
