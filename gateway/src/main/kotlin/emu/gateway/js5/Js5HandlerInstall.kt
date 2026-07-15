package emu.gateway.js5

import emu.crypto.XorStreamCipher
import emu.gateway.js5.handler.Js5ControlHandler
import emu.gateway.js5.handler.Js5RequestHandler
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.js5.message.Js5Control
import emu.protocol.osrs239.js5.message.Js5Request
import org.koin.core.Koin

/** Binds the shared request handler and connection-local control handler. */
fun HandlerRepositoryBuilder.installJs5Handlers(koin: Koin, cipher: XorStreamCipher): HandlerRepositoryBuilder = this
    .bind(Js5Request::class.java, koin.get<Js5RequestHandler>())
    .bind(Js5Control::class.java, Js5ControlHandler(cipher))
