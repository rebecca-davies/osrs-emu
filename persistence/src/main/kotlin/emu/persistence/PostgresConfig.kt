package emu.persistence

/** Connection settings for the character database. */
data class PostgresConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val pool: PostgresPoolConfig = PostgresPoolConfig(),
) {
    override fun toString(): String =
        "PostgresConfig(jdbcUrl=$jdbcUrl, username=$username, password=<redacted>, pool=$pool)"

    companion object {
        /** Loads production overrides or the loopback-only compose development defaults. */
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): PostgresConfig =
            PostgresConfig(
                jdbcUrl = environment["OSRS_DATABASE_URL"] ?: "jdbc:postgresql://127.0.0.1:54330/osrsemu",
                username = environment["OSRS_DATABASE_USER"] ?: "osrsemu",
                password = environment["OSRS_DATABASE_PASSWORD"] ?: "osrsemu-dev",
                pool = PostgresPoolConfig.fromEnvironment(environment),
            )
    }
}
