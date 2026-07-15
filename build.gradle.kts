plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

apply(from = "gradle/verification/declaration-quality.gradle.kts")
apply(from = "gradle/verification/structural-quality.gradle.kts")
apply(from = "gradle/verification/protocol-boundaries.gradle.kts")
apply(from = "gradle/verification/architecture.gradle.kts")
apply(from = "gradle/verification/project-conventions.gradle.kts")
