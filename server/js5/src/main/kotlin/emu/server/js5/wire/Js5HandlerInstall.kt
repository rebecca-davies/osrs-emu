package emu.server.js5.wire

import emu.crypto.XorStreamCipher
import emu.server.js5.handler.Js5ControlHandler
import emu.server.js5.handler.Js5RequestHandler
import emu.transport.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.js5.message.Js5Control
import emu.protocol.osrs239.js5.message.Js5Request

/** Binds JS5 request and connection-local XOR handlers to the protocol pipeline. */
fun HandlerRepositoryBuilder.installJs5Handlers(requests: Js5RequestHandler, cipher: XorStreamCipher): HandlerRepositoryBuilder = this
    .bind(Js5Request::class.java, requests)
    .bind(Js5Control::class.java, Js5ControlHandler(cipher))
