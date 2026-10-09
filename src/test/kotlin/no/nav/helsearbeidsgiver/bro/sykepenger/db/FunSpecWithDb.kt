package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

abstract class FunSpecWithDb(
    body: FunSpec.(ExposedDatabase) -> Unit,
) : FunSpec({
        val postgres = postgres()
        val dataSource = dataSource(postgres)
        val db = ExposedDatabase.connect(dataSource)

        beforeTest {
            transaction(db) {
                listOf(ForespoerselTable, BesvarelseTable).forEach { it.deleteAll() }
            }
        }

        afterSpec {
            dataSource.close()
            postgres.close()
        }

        body(db)
    })

private fun postgres(): PostgreSQLContainer =
    PostgreSQLContainer("postgres:15")
        .withReuse(true)
        .withLabel("app", "helsearbeidsgiver-bro-sykepenger")
        .also {
            // Nødvendig for å kjøre migrering V16-V18
            it.setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")

            it.start()

            println(
                """
                Databasecontainer er startet opp 🐘
                port='${it.firstMappedPort}'
                jdbcUrl='jdbc:postgresql://localhost:${it.firstMappedPort}/test'
                credentials: 'test' og 'test'
                """.trimIndent(),
            )
        }

private fun dataSource(postgres: PostgreSQLContainer): HikariDataSource =
    HikariConfig()
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
        .also(::migrate)

private fun migrate(dataSource: DataSource) {
    Flyway
        .configure()
        .dataSource(dataSource)
        .failOnMissingLocations(true)
        .cleanDisabled(false)
        .load()
        .also(Flyway::clean)
        .migrate()
}
