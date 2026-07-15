package emu.persistence.postgres.database

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val MIGRATION_LOCK_ID = 0x4F535253L
private const val SCHEMA_MIGRATIONS_SQL =
    "CREATE TABLE IF NOT EXISTS schema_migrations (" +
        "version INTEGER PRIMARY KEY, " +
        "applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
private data class Migration(val version: Int, val resource: String)
private val MIGRATIONS =
    listOf(
        Migration(1, "/db/migration/V1__players.sql"),
        Migration(2, "/db/migration/V2__player_rank.sql"),
        Migration(3, "/db/migration/V3__player_varps.sql"),
        Migration(4, "/db/migration/V4__chat_messages.sql"),
        Migration(5, "/db/migration/V5__character_defaults.sql"),
        Migration(6, "/db/migration/V6__player_chat_filters.sql"),
    )

/** Applies bundled schema migrations once under a PostgreSQL advisory lock. */
class PostgresMigrator(private val database: PostgresDatabase) {
    fun migrate() {
        database.transaction { connection ->
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
        }
    }
}
