rootProject.name = "osrsemu"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("buffer")
include("cache")
include("crypto")
include("net-core")
include("protocol-osrs235")
include("gateway")
include("tools:cache-fetch")
