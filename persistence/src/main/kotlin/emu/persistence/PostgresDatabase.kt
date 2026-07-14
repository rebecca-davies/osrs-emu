package emu.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource

private val logger = KotlinLogging.logger {}

/** PostgreSQL connection factory and small, versioned schema migration runner. */
class PostgresDatabase(config: PostgresConfig) {
    val dataSource: DataSource =
        PGSimpleDataSource().apply {
            setURL(config.jdbcUrl)
            user = config.username
            password = config.password
        }

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
            )
    }
}
