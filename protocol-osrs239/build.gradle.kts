dependencies {
    implementation(project(":net-core"))
    implementation(project(":buffer"))
    implementation(project(":crypto"))
    // Koin modules live here so each protocol domain (js5/login/game) owns its own codec
    // registrations (CLAUDE.md §5a addendum) — net-core stays framework-agnostic; only this
    // service-adjacent layer and gateway take the Koin dependency.
    implementation(libs.koin.core)
    testImplementation(libs.kotlin.test)
}
