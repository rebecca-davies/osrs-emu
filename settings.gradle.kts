rootProject.name = "osrsemu"

// Configures a JDK toolchain download repository (foojay) so any build environment — IntelliJ's
// Gradle sync, CI, a machine without JDK 21 installed — can auto-provision the JDK 21 toolchain
// the modules require, instead of failing with "toolchain download repositories have not been
// configured". Locally-installed JDK 21 is still detected and preferred; download is the fallback.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("buffer")
include("cache")
include("crypto")
include("compression")
include("game")
include("net-core")
include("protocol-login")
project(":protocol-login").projectDir = file("protocol/login")
include("protocol-js5")
project(":protocol-js5").projectDir = file("protocol/js5")
include("protocol-game")
project(":protocol-game").projectDir = file("protocol/game")
include("persistence-api")
project(":persistence-api").projectDir = file("persistence/api")
include("persistence-postgres")
project(":persistence-postgres").projectDir = file("persistence/postgres")
include("server-gateway")
project(":server-gateway").projectDir = file("server/gateway")
include("server-session")
project(":server-session").projectDir = file("server/session")
include("server-js5")
project(":server-js5").projectDir = file("server/js5")
include("server-login")
project(":server-login").projectDir = file("server/login")
include("server-game")
project(":server-game").projectDir = file("server/game")
include("server-app")
project(":server-app").projectDir = file("server/app")
include("tools:cache-fetch")
include("tools:client-patch")
