package emu.persistence.postgres

import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresMigrator
import emu.persistence.postgres.database.PostgresPoolConfig
import java.sql.SQLException
import org.junit.jupiter.api.Assumptions.assumeTrue

internal fun migratedTestDatabase(): PostgresDatabase {
    val environment = System.getenv()
    val database =
        PostgresDatabase(
            PostgresConfig(
                jdbcUrl = environment["OSRS_DATABASE_URL"] ?: "jdbc:postgresql://127.0.0.1:54330/osrsemu",
                username = environment["OSRS_DATABASE_USER"] ?: "osrsemu",
                password = environment["OSRS_DATABASE_PASSWORD"] ?: "osrsemu-dev",
                pool = PostgresPoolConfig(maximumSize = 2, minimumIdle = 0),
            ),
        )
    val reachable =
        try {
            database.connection { it.isValid(2) }
        } catch (_: SQLException) {
            false
        }
    if (!reachable) database.close()
    assumeTrue(reachable, "PostgreSQL is unreachable; run docker compose up -d postgres")
    PostgresMigrator(database).migrate()
    return database
}
