package emu.persistence.postgres.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

/** Bounded PostgreSQL connection pool. */
class PostgresDatabase(
    config: PostgresConfig,
    poolConfig: PostgresPoolConfig,
    poolName: String,
) : AutoCloseable {
    private val pool = HikariDataSource(config.toHikariConfig(poolConfig, poolName))

    val dataSource: DataSource
        get() = pool

    val isClosed: Boolean
        get() = pool.isClosed

    val poolName: String
        get() = pool.poolName

    val maximumPoolSize: Int
        get() = pool.maximumPoolSize

    fun <T> connection(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun <T> transaction(block: (Connection) -> T): T =
        connection { connection ->
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }

    override fun close() = pool.close()
}

private fun PostgresConfig.toHikariConfig(
    poolConfig: PostgresPoolConfig,
    poolName: String,
): HikariConfig =
    HikariConfig().also { hikari ->
        hikari.jdbcUrl = jdbcUrl
        hikari.username = username
        hikari.password = password
        hikari.poolName = poolName
        hikari.maximumPoolSize = poolConfig.maximumSize
        hikari.minimumIdle = poolConfig.minimumIdle
        hikari.connectionTimeout = poolConfig.connectionTimeout.inWholeMilliseconds
        hikari.validationTimeout = poolConfig.validationTimeout.inWholeMilliseconds
        hikari.idleTimeout = poolConfig.idleTimeout.inWholeMilliseconds
        hikari.maxLifetime = poolConfig.maxLifetime.inWholeMilliseconds
        hikari.initializationFailTimeout = -1
        hikari.addDataSourceProperty("tcpKeepAlive", "true")
        hikari.addDataSourceProperty("connectTimeout", operations.connectTimeoutSeconds.toString())
        hikari.addDataSourceProperty("socketTimeout", operations.socketTimeoutSeconds.toString())
        hikari.addDataSourceProperty(
            "options",
            "-c statement_timeout=${operations.statementTimeoutMillis}",
        )
    }
