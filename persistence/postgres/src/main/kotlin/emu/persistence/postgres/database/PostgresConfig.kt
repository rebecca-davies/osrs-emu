package emu.persistence.postgres.database

/** PostgreSQL connection and pool settings supplied by the process host. */
data class PostgresConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val loginPool: PostgresPoolConfig = PostgresPoolConfig(maximumSize = 4),
    val worldPool: PostgresPoolConfig = PostgresPoolConfig(),
    val operations: PostgresOperationConfig = PostgresOperationConfig(),
) {
    override fun toString(): String =
        "PostgresConfig(jdbcUrl=$jdbcUrl, username=$username, password=<redacted>, " +
            "loginPool=$loginPool, worldPool=$worldPool, operations=$operations)"
}
