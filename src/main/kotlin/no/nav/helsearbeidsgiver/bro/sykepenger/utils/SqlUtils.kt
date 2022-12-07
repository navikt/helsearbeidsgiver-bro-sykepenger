package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

fun updateAndReturnGeneratedKey(dataSource: DataSource, query: String, params: Map<String, *>): Long? {
    return sessionOf(dataSource, returnGeneratedKey = true).use { session ->
        session.run(queryOf(query, params).asUpdateAndReturnGeneratedKey)
    }
}

fun execute(dataSource: DataSource, query: String, params: Map<String, *>): Boolean {
    return sessionOf(dataSource).use { session ->
        session.run(queryOf(query, params).asExecute)
    }
}

fun <T> hentListe(dataSource: DataSource, query: String, params: Map<String, *>, transform: (Row) -> T): List<T> {
    return sessionOf(dataSource).use { session ->
        session.run(
            queryOf(query, params)
                .map { row -> transform(row) }
                .asList
        )
    }
}

fun <T> hent(dataSource: DataSource, query: String, params: Map<String, *>, transform: (Row) -> T): T? {
    return sessionOf(dataSource).use { session ->
        session.run(
            queryOf(query, params)
                .map { row -> transform(row) }
                .asSingle
        )
    }
}
