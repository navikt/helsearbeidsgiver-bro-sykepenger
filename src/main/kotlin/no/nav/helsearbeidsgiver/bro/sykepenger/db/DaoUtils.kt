package no.nav.helsearbeidsgiver.bro.sykepenger.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

fun <T> Transaction?.orNew(
    db: Database,
    statement: Transaction.() -> T,
): T =
    if (this != null) {
        this.run(statement)
    } else {
        transaction(db, statement)
    }
