package emu.persistence.postgres.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

/** Bounded PostgreSQL connection pool. */
class PostgresDatabase(config: PostgresConfig) : AutoCloseable {
    private val pool = HikariDataSource(config.toHikariConfig())

    val dataSource: DataSource
        get() = pool

    val isClosed: Boolean
        get() = pool.isClosed

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

private fun PostgresConfig.toHikariConfig(): HikariConfig =
    HikariConfig().also { hikari ->
        hikari.jdbcUrl = jdbcUrl
        hikari.username = username
        hikari.password = password
        hikari.poolName = "osrsemu-postgres"
        hikari.maximumPoolSize = pool.maximumSize
        hikari.minimumIdle = pool.minimumIdle
        hikari.connectionTimeout = pool.connectionTimeout.inWholeMilliseconds
        hikari.validationTimeout = pool.validationTimeout.inWholeMilliseconds
        hikari.idleTimeout = pool.idleTimeout.inWholeMilliseconds
        hikari.maxLifetime = pool.maxLifetime.inWholeMilliseconds
        hikari.initializationFailTimeout = -1
        hikari.addDataSourceProperty("tcpKeepAlive", "true")
    }
