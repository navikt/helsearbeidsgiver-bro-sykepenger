package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.action.QueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

fun String.updateAndReturnGeneratedKey(params: Map<String, Any?>, session: Session): Long? =
    runQuery(params, session) {
        asUpdateAndReturnGeneratedKey
    }

fun String.execute(params: Map<String, Any>, session: Session): Boolean =
    runQuery(params, session) {
        asExecute
    }

fun <T : Any> String.listResult(params: Map<String, Any>, dataSource: DataSource, transform: Row.() -> T): List<T> =
    runQuery(params, dataSource) {
        map(transform).asList
    }

fun <T : Any> String.listResult(params: Map<String, Any>, session: TransactionalSession, transform: Row.() -> T): List<T> =
    runQuery(params, session) {
        map(transform).asList
    }

fun <T : Any> String.nullableResult(params: Map<String, Any>, dataSource: DataSource, transform: Row.() -> T): T? =
    runQuery(params, dataSource) {
        map(transform).asSingle
    }

private fun <T> String.runQuery(
    params: Map<String, *>,
    dataSource: DataSource,
    transform: Query.() -> QueryAction<T>
): T =
    sessionOf(dataSource).use {
        runQuery(params, it, transform)
    }

private fun <T> String.runQuery(
    params: Map<String, *>,
    session: Session,
    transform: Query.() -> QueryAction<T>
): T =
    queryOf(this, params)
        .transform()
        .runWithSession(session)
