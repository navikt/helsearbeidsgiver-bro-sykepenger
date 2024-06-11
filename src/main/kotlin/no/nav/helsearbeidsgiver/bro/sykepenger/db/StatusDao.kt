package no.nav.helsearbeidsgiver.bro.sykepenger.db

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

abstract class StatusDao {
    abstract val db: Database
    abstract val statusTable: StatusTable

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract fun tilForespoerselDto(row: ResultRow): ForespoerselDto

    fun hentForespoerselForForespoerselId(forespoerselId: UUID): ForespoerselDto? =
        transaction(db) {
            statusTable
                .selectAll()
                .where {
                    statusTable.forespoerselId eq forespoerselId
                }
                .map(::tilForespoerselDto)
                .firstOrNull()
        }

    fun hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId: UUID): ForespoerselDto? =
        hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId, setOf(Status.AKTIV))
            .also { forespoersler ->
                if (forespoersler.size > 1) {
                    "Fant flere aktive forespørsler for vedtaksperiode: $vedtaksperiodeId".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
            }
            .maxByOrNull { it.opprettet }

    fun hentForespoerslerForVedtaksperiodeId(
        vedtaksperiodeId: UUID,
        statuser: Set<Status>,
    ): List<ForespoerselDto> =
        transaction(db) {
            statusTable
                .selectAll()
                .where {
                    (statusTable.vedtaksperiodeId eq vedtaksperiodeId) and
                        (statusTable.status inList statuser.map { it.name })
                }
                .map(::tilForespoerselDto)
                .sortedBy { it.opprettet }
        }

    protected fun hentVedtaksperiodeIdQuery(forespoerselId: UUID): Query =
        statusTable
            .select(statusTable.vedtaksperiodeId)
            .where {
                statusTable.forespoerselId eq forespoerselId
            }

    fun oppdaterForespoerslerSomForkastet(vedtaksperiodeId: UUID) {
        oppdaterStatuser(
            vedtaksperiodeId = vedtaksperiodeId,
            erstattStatuser = setOf(Status.AKTIV),
            nyStatus = Status.FORKASTET,
        )
    }

    protected fun oppdaterStatuser(
        vedtaksperiodeId: UUID,
        erstattStatuser: Set<Status>,
        nyStatus: Status,
        activeTransaction: Transaction? = null,
    ): List<Long> {
        val whereStatement: SqlExpressionBuilder.() -> Op<Boolean> = {
            (statusTable.vedtaksperiodeId eq vedtaksperiodeId) and
                (statusTable.status inList erstattStatuser.map { it.name })
        }

        return activeTransaction.orNew(db) {
            // Exposed støtter ikke Postgres sin RETURNING: https://github.com/JetBrains/Exposed/issues/1271
            val updated =
                statusTable
                    .selectAll()
                    .where(whereStatement)
                    .map {
                        it[statusTable.id]
                    }

            statusTable.update(whereStatement) {
                it[status] = nyStatus.name
            }

            updated
        }
            .also {
                val msg = "Oppdaterte ${it.size} rader med ny status '$nyStatus'. ids=$it"
                logger.info(msg)
                sikkerLogger.info(msg)
            }
    }
}
