package no.nav.helsearbeidsgiver.bro.sykepenger.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helsearbeidsgiver.bro.sykepenger.Env
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import org.jetbrains.exposed.sql.Database as ExposedDatabase

object Database {
    val dataSource by lazy { HikariDataSource(dbConfig) }

    val db by lazy { ExposedDatabase.connect(dataSource) }

    fun migrate() {
        HikariDataSource(migrationConfig)
            .also(::runMigration)
            .close()
    }
}

private val dbUrl = "jdbc:postgresql://%s:%s/%s".format(Env.Database.host, Env.Database.port, Env.Database.name)

private val dbConfig by lazy {
    HikariConfig().apply {
        jdbcUrl = dbUrl
        username = Env.Database.username
        password = Env.Database.password
        maximumPoolSize = 1
    }
}

private val migrationConfig by lazy {
    HikariConfig().apply {
        jdbcUrl = dbUrl
        username = Env.Database.username
        password = Env.Database.password
        maximumPoolSize = 3
    }
}

private fun runMigration(dataSource: DataSource) {
    Flyway
        .configure()
        .dataSource(dataSource)
        .lockRetryCount(-1)
        .load()
        .migrate()
}
