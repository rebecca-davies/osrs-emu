rootProject.name = "osrsemu"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("buffer")
include("cache")
include("crypto")
include("net-core")
include("protocol-osrs239")
include("gateway")
include("tools:cache-fetch")
include("tools:client-patch")
