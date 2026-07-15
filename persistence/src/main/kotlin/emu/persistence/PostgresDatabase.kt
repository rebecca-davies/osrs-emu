package emu.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/** Bounded PostgreSQL connection pool and small, versioned schema migration runner. */
class PostgresDatabase(config: PostgresConfig) : AutoCloseable {
    private val pool = HikariDataSource(config.hikariConfig())

    /** Shared pooled data source retained for callers that need the existing JDBC boundary. */
    val dataSource: DataSource
        get() = pool

    val isClosed: Boolean
        get() = pool.isClosed

    /** Applies each bundled migration once, recording it in `schema_migrations`. */
    fun migrate() {
        connection { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.execute(SCHEMA_MIGRATIONS_SQL)
                    statement.execute("SELECT pg_advisory_xact_lock($MIGRATION_LOCK_ID)")
                    for (migration in MIGRATIONS) {
                        val applied =
                            connection.prepareStatement(
                                "SELECT 1 FROM schema_migrations WHERE version = ?",
                            ).use { query ->
                                query.setInt(1, migration.version)
                                query.executeQuery().use { it.next() }
                            }
                        if (applied) continue
                        val sql = checkNotNull(javaClass.getResource(migration.resource)) {
                            "missing database migration ${migration.resource}"
                        }.readText()
                        statement.execute(sql)
                        connection.prepareStatement(
                            "INSERT INTO schema_migrations(version) VALUES (?)",
                        ).use { insert ->
                            insert.setInt(1, migration.version)
                            insert.executeUpdate()
                        }
                        logger.info { "applied PostgreSQL migration V${migration.version}" }
                    }
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    /** Opens one JDBC connection for [block] and always closes it afterwards. */
    fun <T> connection(block: (Connection) -> T): T = dataSource.connection.use(block)

    /** Stops pool workers and closes every idle physical PostgreSQL connection. */
    override fun close() = pool.close()

    private data class Migration(val version: Int, val resource: String)

    private companion object {
        const val MIGRATION_LOCK_ID = 0x4F535253L
        const val SCHEMA_MIGRATIONS_SQL =
            "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                "version INTEGER PRIMARY KEY, " +
                "applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
        val MIGRATIONS =
            listOf(
                Migration(1, "/db/migration/V1__players.sql"),
                Migration(2, "/db/migration/V2__player_rank.sql"),
                Migration(3, "/db/migration/V3__player_varps.sql"),
                Migration(4, "/db/migration/V4__chat_messages.sql"),
                Migration(5, "/db/migration/V5__character_defaults.sql"),
            )
    }
}

private fun PostgresConfig.hikariConfig(): HikariConfig =
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
