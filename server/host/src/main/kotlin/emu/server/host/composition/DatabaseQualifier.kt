package emu.server.host.composition

import org.koin.core.qualifier.named

/** Explicit persistence bindings that prevent account and world work sharing a pool. */
internal object DatabaseQualifier {
    val login = named("login-database")
    val world = named("world-database")
}
