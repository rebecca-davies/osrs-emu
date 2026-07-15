# Historical rev-239 client-patch notes

This file records that the self-built injected rev-239 client successfully loaded the local cache
and entered the game through the emulator on 2026-07-14. It is not a launch guide; the original
commands predated the current service boundaries and network-isolation rules.

The maintained runtime entry point is the `:server-app` Gradle project under `server/app`. It
composes the gateway, login, JS5, and game services. The gateway only accepts sockets and routes the
first opcode; protocol and game behavior belongs to the corresponding peer service.

Client acceptance is allowed only through `/tmp/m5-injail.sh`, which runs the local server, HTTP
configuration endpoint, and Java 11 client inside `unshare -rn`. The jail must verify loopback-only
networking and use an isolated `user.home`. Never launch stock RuneLite, use the user's real
RuneLite home, or recreate the removed direct-launch helper.

Automated verification remains headless: `./gradlew build`, protocol oracle tests, and
`scripts/verify-deployment-config.sh`. A GUI client is a rare acceptance check after those pass.
