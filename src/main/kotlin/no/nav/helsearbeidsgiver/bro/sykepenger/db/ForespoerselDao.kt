package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.BesvarelseMetadataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.execute
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.listResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.nullableResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.splitOnIndex
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.updateAndReturnGeneratedKey
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.updateResult
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class ForespoerselDao(private val dataSource: DataSource) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lagre(forespoersel: ForespoerselDto): Long? {
        val felter =
            mapOf(
                Db.FORESPOERSEL_ID to forespoersel.forespoerselId,
                Db.TYPE to forespoersel.type.name,
                Db.STATUS to forespoersel.status.name,
                Db.ORGNR to forespoersel.orgnr.verdi,
                Db.FNR to forespoersel.fnr,
                Db.VEDTAKSPERIODE_ID to forespoersel.vedtaksperiodeId,
                Db.SKJAERINGSTIDSPUNKT to forespoersel.skjaeringstidspunkt,
                Db.OPPRETTET to forespoersel.opprettet,
                Db.OPPDATERT to forespoersel.oppdatert,
            )

        val jsonFelter =
            mapOf(
                Db.SYKMELDINGSPERIODER to forespoersel.sykmeldingsperioder.toJsonStr(Periode.serializer().list()),
                Db.EGENMELDINGSPERIODER to forespoersel.egenmeldingsperioder.toJsonStr(Periode.serializer().list()),
                Db.FORESPURT_DATA to forespoersel.forespurtData.toJsonStr(SpleisForespurtDataDto.serializer().list()),
            )

        val kolonnenavn = (felter + jsonFelter).keys.joinToString()
        val noekler =
            listOf(
                felter.keys.joinToString { ":$it" },
                jsonFelter.keys.joinToString { ":$it::json" },
            ).joinToString()

        return sessionOf(
            dataSource = dataSource,
            returnGeneratedKey = true,
        ).useTransaction {
            oppdaterStatuser(
                session = it,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                erstattStatuser = setOf(Status.AKTIV),
                nyStatus = Status.FORKASTET,
            )

            "INSERT INTO forespoersel($kolonnenavn) VALUES ($noekler)"
                .updateAndReturnGeneratedKey(
                    params = felter + jsonFelter,
                    session = it,
                )
        }
    }

    fun oppdaterForespoerslerSomBesvartFraSpleis(
        vedtaksperiodeId: UUID,
        besvart: LocalDateTime,
        inntektsmeldingId: UUID?,
    ): Int =
        sessionOf(dataSource = dataSource).useTransaction { transaction ->
            val oppdaterteForespoersler =
                oppdaterStatuser(
                    session = transaction,
                    vedtaksperiodeId = vedtaksperiodeId,
                    erstattStatuser = setOf(Status.AKTIV, Status.BESVART_SIMBA, Status.BESVART_SPLEIS),
                    nyStatus = Status.BESVART_SPLEIS,
                )

            if (oppdaterteForespoersler.isEmpty()) {
                val msg =
                    "Fant ingen aktive eller besvarte forespørsler for vedtaksperioden $vedtaksperiodeId. " +
                        "Dette skal kun skje for vedtaksperioder som ikke støttes enda (potensielle) eller som stammer fra før pilot."
                logger.warn(msg)
                sikkerLogger.warn(msg)
            }

            oppdaterteForespoersler.forEach { id ->
                insertOrUpdateBesvarelse(transaction, id, besvart, inntektsmeldingId)
            }

            oppdaterteForespoersler.size
        }

    fun oppdaterForespoerslerSomBesvartFraSimba(
        vedtaksperiodeId: UUID,
        besvart: LocalDateTime,
    ): Int =
        sessionOf(dataSource = dataSource).useTransaction { transaction ->
            val oppdaterteForespoersler =
                oppdaterStatuser(
                    session = transaction,
                    vedtaksperiodeId = vedtaksperiodeId,
                    erstattStatuser = setOf(Status.AKTIV, Status.BESVART_SIMBA),
                    nyStatus = Status.BESVART_SIMBA,
                )

            oppdaterteForespoersler.forEach { id ->
                insertOrUpdateBesvarelse(transaction, id, besvart, null)
            }

            oppdaterteForespoersler.size
        }

    fun oppdaterForespoerslerSomForkastet(vedtaksperiodeId: UUID) {
        sessionOf(dataSource = dataSource).use {
            oppdaterStatuser(
                session = it,
                vedtaksperiodeId = vedtaksperiodeId,
                erstattStatuser = setOf(Status.AKTIV),
                nyStatus = Status.FORKASTET,
            )
        }
    }

    fun hentForespoerselForForespoerselId(forespoerselId: UUID): ForespoerselDto? =
        query(
            "SELECT *",
            "FROM forespoersel f",
            "LEFT JOIN besvarelse_metadata b ON f.id=b.${Db.FK_FORESPOERSEL_ID}",
            "WHERE ${Db.FORESPOERSEL_ID}=:forespoerselId",
        )
            .nullableResult(
                params = mapOf("forespoerselId" to forespoerselId),
                dataSource = dataSource,
                transform = Row::toForespoerselDto,
            )

    fun hentNyesteForespoerselForForespoerselId(
        forespoerselId: UUID,
        statuser: Set<Status>,
    ): ForespoerselDto? =
        hentVedtaksperiodeId(forespoerselId)?.let { vedtaksperiodeId ->
            hentForespoerslerForVedtaksperiodeId(
                vedtaksperiodeId = vedtaksperiodeId,
                statuser = statuser,
            )
                .maxByOrNull { it.opprettet }
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

    fun forespoerselIdEksponertTilSimba(vedtaksperiodeId: UUID): UUID? {
        val forespoersler =
            hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId, Status.entries.toSet())
                .sortedBy { it.opprettet }

        val besvarteIndekser =
            forespoersler.mapIndexedNotNull { index, forespoersel ->
                if (forespoersel.status in listOf(Status.BESVART_SIMBA, Status.BESVART_SPLEIS)) {
                    index
                } else {
                    null
                }
            }

        return besvarteIndekser
            .fold(listOf(forespoersler)) { acc, besvartIndex ->
                acc.last().splitOnIndex(besvartIndex + 1).toList()
            }
            .lastOrNull { it.isNotEmpty() }
            ?.firstOrNull()
            ?.forespoerselId
    }

    private fun hentVedtaksperiodeId(forespoerselId: UUID): UUID? =
        query(
            "SELECT ${Db.VEDTAKSPERIODE_ID}",
            "FROM forespoersel",
            "WHERE ${Db.FORESPOERSEL_ID}=:forespoerselId",
        )
            .nullableResult(
                params = mapOf("forespoerselId" to forespoerselId),
                dataSource = dataSource,
                transform = { Db.VEDTAKSPERIODE_ID.let(::uuid) },
            )

    private fun hentForespoerslerForVedtaksperiodeId(
        vedtaksperiodeId: UUID,
        statuser: Set<Status>,
    ): List<ForespoerselDto> =
        query(
            "SELECT * FROM forespoersel f",
            "LEFT JOIN besvarelse_metadata b ON f.id=b.${Db.FK_FORESPOERSEL_ID}",
            "WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId AND ${Db.STATUS} in (${statuser.joinToString { "'$it'" }})",
        )
            .listResult(
                params = mapOf("vedtaksperiodeId" to vedtaksperiodeId),
                dataSource = dataSource,
                transform = Row::toForespoerselDto,
            )

    private fun oppdaterStatuser(
        session: Session,
        vedtaksperiodeId: UUID,
        erstattStatuser: Set<Status>,
        nyStatus: Status,
    ): List<Long> =
        query(
            "UPDATE forespoersel",
            "SET ${Db.STATUS}=:nyStatus",
            "WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId AND ${Db.STATUS} in (${erstattStatuser.joinToString { "'$it'" }})",
            "RETURNING id",
        )
            .updateResult(
                params =
                    mapOf(
                        "vedtaksperiodeId" to vedtaksperiodeId,
                        "nyStatus" to nyStatus.name,
                    ),
                session = session,
                transform = Row::toId,
            )

    private fun insertOrUpdateBesvarelse(
        session: TransactionalSession,
        forespoerselId: Long,
        forespoerselBesvart: LocalDateTime,
        inntektsmeldingId: UUID?,
    ): Boolean =
        query(
            "INSERT INTO besvarelse_metadata(${Db.FK_FORESPOERSEL_ID}, ${Db.FORESPOERSEL_BESVART}, ${Db.INNTEKTSMELDING_ID})",
            "VALUES(:forespoerselId, :forespoerselBesvart, :inntektsmeldingId)",
            "ON CONFLICT (fk_forespoersel_id) DO UPDATE SET " +
                "${Db.FORESPOERSEL_BESVART}=:forespoerselBesvart, ${Db.INNTEKTSMELDING_ID}=:inntektsmeldingId",
        )
            .execute(
                params =
                    mapOf(
                        "forespoerselId" to forespoerselId,
                        "forespoerselBesvart" to forespoerselBesvart,
                        "inntektsmeldingId" to inntektsmeldingId,
                    )
                        .mapValuesNotNull { it },
                session = session,
            )
}

fun Row.toForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = Db.FORESPOERSEL_ID.let(::uuid),
        type = Db.TYPE.let(::string).let(Type::valueOf),
        status = Db.STATUS.let(::string).let(Status::valueOf),
        orgnr = Db.ORGNR.let(::string).let(::Orgnr),
        fnr = Db.FNR.let(::string),
        vedtaksperiodeId = Db.VEDTAKSPERIODE_ID.let(::uuid),
        skjaeringstidspunkt = Db.SKJAERINGSTIDSPUNKT.let(::localDateOrNull),
        sykmeldingsperioder = Db.SYKMELDINGSPERIODER.let(::string).fromJson(Periode.serializer().list()),
        egenmeldingsperioder = Db.EGENMELDINGSPERIODER.let(::string).fromJson(Periode.serializer().list()),
        forespurtData = Db.FORESPURT_DATA.let(::string).fromJson(SpleisForespurtDataDto.serializer().list()),
        besvarelse = toBesvarelseMetadataDto(),
        opprettet = Db.OPPRETTET.let(::localDateTime),
        oppdatert = Db.OPPDATERT.let(::localDateTime),
    )

private fun Row.toBesvarelseMetadataDto(): BesvarelseMetadataDto? {
    val forespoerselBesvart = Db.FORESPOERSEL_BESVART.let(::localDateTimeOrNull)
    val inntektsmeldingId = Db.INNTEKTSMELDING_ID.let(::uuidOrNull)

    return forespoerselBesvart?.let { BesvarelseMetadataDto(forespoerselBesvart, inntektsmeldingId) }
}

private fun Row.toId(): Long = Db.ID.let(::long)

/** Unngår bruk av ikke-transactional session. */
private fun <T> Session.useTransaction(block: (TransactionalSession) -> T): T =
    use { session ->
        session.transaction(block)
    }

/** Kun brukt for å få penere kodeformat på queries fordelt over flere linjer. */
private fun query(vararg parts: String): String = parts.joinToString(separator = " ")
