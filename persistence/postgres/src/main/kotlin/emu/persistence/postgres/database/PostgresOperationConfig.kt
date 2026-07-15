package emu.persistence.postgres.database

/** PostgreSQL driver and server deadlines for every login, load, audit, and write-back query. */
data class PostgresOperationConfig(
    val connectTimeoutSeconds: Int = 5,
    val socketTimeoutSeconds: Int = 10,
    val statementTimeoutMillis: Int = 5_000,
) {
    init {
        require(connectTimeoutSeconds > 0) { "PostgreSQL connect timeout must be positive" }
        require(socketTimeoutSeconds > 0) { "PostgreSQL socket timeout must be positive" }
        require(statementTimeoutMillis > 0) { "PostgreSQL statement timeout must be positive" }
    }
}
