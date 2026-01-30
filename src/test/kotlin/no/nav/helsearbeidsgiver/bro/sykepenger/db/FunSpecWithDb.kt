package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

abstract class FunSpecWithDb(
    tables: List<Table>,
    body: FunSpec.(ExposedDatabase) -> Unit,
) : FunSpec({
        val db = ExposedDatabase.connect(dataSource())

        beforeTest {
            transaction(db) {
                tables.forEach { it.deleteAll() }
            }
        }

        body(db)
    })

private fun dataSource(): DataSource {
    val postgres = postgres()
    return HikariConfig()
        .apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 5
            minimumIdle = 1
            idleTimeout = 500001
            connectionTimeout = 10000
            maxLifetime = 600001
            initializationFailTimeout = 5000
        }.let(::HikariDataSource)
        .migrate()
}

private fun postgres(): PostgreSQLContainer =
    PostgreSQLContainer("postgres:14").apply {
        withReuse(true)
        withLabel("app-navn", "helsearbeidsgiver-bro-sykepenger")
        // nødvending for kunne kjøre migreringsscriptene V16-V18
        setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")
        start()
        println(
            "🎩 Databasen er startet opp, portnummer: $firstMappedPort, " +
                "jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test",
        )
    }

private fun DataSource.migrate() =
    also {
        Flyway
            .configure()
            .dataSource(this)
            .failOnMissingLocations(true)
            .cleanDisabled(false)
            .load()
            .also(Flyway::clean)
            .migrate()
    }
