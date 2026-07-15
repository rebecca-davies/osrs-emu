package emu.persistence.postgres.database

/** PostgreSQL connection and pool settings supplied by the process host. */
data class PostgresConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val pool: PostgresPoolConfig = PostgresPoolConfig(),
) {
    override fun toString(): String =
        "PostgresConfig(jdbcUrl=$jdbcUrl, username=$username, password=<redacted>, pool=$pool)"
}
