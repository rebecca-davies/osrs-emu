rootProject.name = "osrsemu"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("buffer")
include("crypto")
include("net-core")
include("gateway")
include("tools:cache-fetch")
