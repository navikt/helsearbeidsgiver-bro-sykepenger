package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.execute
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

abstract class AbstractDatabaseFunSpec(body: FunSpec.(DataSource) -> Unit) : FunSpec({
    val dataSource = customDataSource()

    beforeEach {
        sessionOf(dataSource).use {
            "SELECT truncate_tables()".execute(
                params = emptyMap<String, Nothing>(),
                session = it,
            )
        }
    }

    body(dataSource)
})

private fun customDataSource(): HikariDataSource {
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
        .also {
            it.configureFlyway()
            it.createTruncateFunction()
        }
}

private fun postgres(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
        withReuse(true)
        withLabel("app-navn", "helsearbeidsgiver-bro-sykepenger")
        start()
        println(
            "🎩 Databasen er startet opp, portnummer: $firstMappedPort, " +
                "jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test",
        )
    }

private fun DataSource.configureFlyway() {
    Flyway.configure()
        .dataSource(this)
        .failOnMissingLocations(true)
        .cleanDisabled(false)
        .load()
        .also { it.clean() }
        .migrate()
}

private fun DataSource.createTruncateFunction() {
    @Language("PostgreSQL")
    val query = """
            CREATE OR REPLACE FUNCTION truncate_tables() RETURNS void AS $$
            DECLARE
            truncate_statement text;
            BEGIN
                SELECT 'TRUNCATE ' || string_agg(format('%I.%I', schemaname, tablename), ',') || ' RESTART IDENTITY CASCADE' 
                    INTO truncate_statement
                FROM pg_tables
                WHERE schemaname='public';

                EXECUTE truncate_statement;
            END;
            $$ LANGUAGE plpgsql;
        """

    sessionOf(this).use {
        query.execute(
            params = emptyMap<String, Nothing>(),
            session = it,
        )
    }
}
