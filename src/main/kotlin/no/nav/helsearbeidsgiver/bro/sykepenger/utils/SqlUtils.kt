package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import kotliquery.Query
import kotliquery.Row
import kotliquery.action.QueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

fun String.updateAndReturnGeneratedKey(params: Map<String, Any?>, dataSource: DataSource): Long? =
    runQuery(params, dataSource, returnGeneratedKey = true) {
        asUpdateAndReturnGeneratedKey
    }

fun String.execute(params: Map<String, Any>, dataSource: DataSource): Boolean =
    runQuery(params, dataSource) {
        asExecute
    }

fun <T : Any> String.listResult(params: Map<String, Any>, dataSource: DataSource, transform: Row.() -> T): List<T> =
    runQuery(params, dataSource) {
        map { transform(it) }
            .asList
    }

fun <T : Any> String.nullableResult(params: Map<String, Any>, dataSource: DataSource, transform: Row.() -> T): T? =
    runQuery(params, dataSource) {
        map { transform(it) }
            .asSingle
    }

private fun <T> String.runQuery(
    params: Map<String, *>,
    dataSource: DataSource,
    returnGeneratedKey: Boolean = false,
    transform: Query.() -> QueryAction<T>
): T =
    sessionOf(
        dataSource = dataSource,
        returnGeneratedKey = returnGeneratedKey
    )
        .use { session ->
            queryOf(this, params)
                .transform()
                .runWithSession(session)
        }
