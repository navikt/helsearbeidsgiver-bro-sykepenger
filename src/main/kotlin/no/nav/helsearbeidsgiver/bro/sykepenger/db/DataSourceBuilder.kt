package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helsearbeidsgiver.bro.sykepenger.Env
import org.flywaydb.core.Flyway
import java.time.Duration
import javax.sql.DataSource

internal class DataSourceBuilder {
    private val dbUrl = "jdbc:postgresql://%s:%s/%s".format(Env.Database.host, Env.Database.port, Env.Database.name)

    private val hikariConfig =
        HikariConfig().apply {
            jdbcUrl = dbUrl
            username = Env.Database.username
            password = Env.Database.password
            idleTimeout = Duration.ofMinutes(1).toMillis()
            maxLifetime = idleTimeout * 5
            initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            connectionTimeout = Duration.ofSeconds(30).toMillis()
            maximumPoolSize = 1
        }

    private val hikariMigrationConfig =
        HikariConfig().apply {
            jdbcUrl = dbUrl
            username = Env.Database.username
            password = Env.Database.password
            initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            connectionTimeout = Duration.ofMinutes(1).toMillis()
            maximumPoolSize = 2
        }

    private fun runMigration(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .lockRetryCount(-1)
            .load()
            .migrate()
    }

    internal fun getDataSource(): HikariDataSource = HikariDataSource(hikariConfig)

    internal fun migrate() {
        hikariMigrationConfig
            .let(::HikariDataSource)
            .also(::runMigration)
            .close()
    }
}
