package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource
import org.jetbrains.exposed.sql.Database as ExposedDatabase

abstract class FunSpecWithDb(
    tables: List<Table>,
    body: FunSpec.(ExposedDatabase) -> Unit,
) : FunSpec({
        val db = ExposedDatabase.connect(dataSource())

        beforeEach {
            transaction(db) {
                tables.forEach { it.deleteAll() }
            }
        }

        body(db)
    })

private fun dataSource(): DataSource {
    val postgres = postgres()
    return HikariConfig().apply {
        jdbcUrl = postgres.jdbcUrl
        username = postgres.username
        password = postgres.password
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 500001
        connectionTimeout = 10000
        maxLifetime = 600001
        initializationFailTimeout = 5000
    }
        .let(::HikariDataSource)
        .migrate()
}

private fun postgres(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
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
        Flyway.configure()
            .dataSource(this)
            .failOnMissingLocations(true)
            .cleanDisabled(false)
            .load()
            .also(Flyway::clean)
            .migrate()
    }
