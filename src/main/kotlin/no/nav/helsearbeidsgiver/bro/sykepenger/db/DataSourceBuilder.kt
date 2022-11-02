package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.time.Duration
import javax.sql.DataSource

object DataSourceConfig {
    private const val prefix: String = "NAIS_DATABASE_HELSEARBEIDSGIVER_BRO_SYKEPENGER_HELSEARBEIDSGIVER_BRO_SYKEPENGER"
    const val HOST = "${prefix}_HOST"
    const val PORT = "${prefix}_PORT"
    const val DATABASE = "${prefix}_DATABASE"
    const val USERNAME = "${prefix}_USERNAME"
    const val PASSWORD = "${prefix}_PASSWORD"
}

internal class DataSourceBuilder(env: Map<String, String>) {
    private val databaseHost: String = requireNotNull(env[DataSourceConfig.HOST]) { "host må settes" }
    private val databasePort: String = requireNotNull(env[DataSourceConfig.PORT]) { "port må settes" }
    private val databaseName: String = requireNotNull(env[DataSourceConfig.DATABASE]) { "databasenavn må settes" }
    private val databaseUsername: String = requireNotNull(env[DataSourceConfig.USERNAME]) { "brukernavn må settes" }
    private val databasePassword: String = requireNotNull(env[DataSourceConfig.PASSWORD]) { "passord må settes" }

    private val dbUrl = String.format("jdbc:postgresql://%s:%s/%s", databaseHost, databasePort, databaseName)

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = databaseUsername
        password = databasePassword
        idleTimeout = Duration.ofMinutes(1).toMillis()
        maxLifetime = idleTimeout * 5
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofSeconds(30).toMillis()
        maximumPoolSize = 1
    }

    private val hikariMigrationConfig = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = databaseUsername
        password = databasePassword
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofMinutes(1).toMillis()
        maximumPoolSize = 2
    }

    private fun runMigration(dataSource: DataSource) =
        Flyway.configure()
            .dataSource(dataSource)
            .lockRetryCount(-1)
            .load()
            .migrate()

    internal fun getDataSource(): HikariDataSource {
        return HikariDataSource(hikariConfig)
    }

    internal fun migrate() {
        val dataSource = HikariDataSource(hikariMigrationConfig)
        runMigration(dataSource)
        dataSource.close()
    }
}
