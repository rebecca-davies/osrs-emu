rootProject.name = "osrsemu"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("common")
include("gateway")
include("tools:cache-fetch")
