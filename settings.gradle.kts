rootProject.name = "osrsemu"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("buffer")
include("cache")
include("crypto")
include("net-core")
include("gateway")
include("tools:cache-fetch")
