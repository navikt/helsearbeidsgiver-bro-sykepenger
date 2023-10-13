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
        ).use { session ->
            session.transaction {
                forkastAlleAktiveForespoerslerFor(forespoersel.vedtaksperiodeId, it)

                "INSERT INTO forespoersel($kolonnenavn) VALUES ($noekler)"
                    .updateAndReturnGeneratedKey(
                        params = felter + jsonFelter,
                        session = it,
                    )
            }
        }
    }

    private fun forkastAlleAktiveForespoerslerFor(
        vedtaksperiodeId: UUID,
        session: TransactionalSession,
    ): Boolean =
        "UPDATE forespoersel SET ${Db.STATUS}=:nyStatus WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId AND ${Db.STATUS}=:gammelStatus"
            .execute(
                params =
                    mapOf(
                        "vedtaksperiodeId" to vedtaksperiodeId,
                        "nyStatus" to Status.FORKASTET.name,
                        "gammelStatus" to Status.AKTIV.name,
                    ),
                session = session,
            )

    fun oppdaterForespoerslerSomBesvart(
        vedtaksperiodeId: UUID,
        besvart: LocalDateTime,
        inntektsmeldingId: UUID?,
    ) = sessionOf(dataSource = dataSource).use { session ->
        session.transaction { transaction ->
            val oppdaterteForespoersler = oppdaterAktiveOgBesvarteStatuser(transaction, vedtaksperiodeId, Status.BESVART)
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
        }
    }

    fun oppdaterForespoerselSomForkastet(vedtaksperiodeId: UUID) =
        sessionOf(dataSource = dataSource).use { oppdaterAktivStatus(it, vedtaksperiodeId, Status.FORKASTET) }

    private fun oppdaterAktiveOgBesvarteStatuser(
        session: TransactionalSession,
        vedtaksperiodeId: UUID,
        status: Status,
    ) = query(
        "UPDATE forespoersel",
        "SET ${Db.STATUS}=:nyStatus",
        "WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId AND ${Db.STATUS} in ('${Status.AKTIV}', '${Status.BESVART}')",
        "RETURNING id",
    )
        .listResult(
            params =
                mutableMapOf(
                    "vedtaksperiodeId" to vedtaksperiodeId,
                    "nyStatus" to status.name,
                ),
            session = session,
            transform = Row::toId,
        )

    private fun oppdaterAktivStatus(
        session: Session,
        vedtaksperiodeId: UUID,
        status: Status,
    ) = query(
        "UPDATE forespoersel",
        "SET ${Db.STATUS}=:nyStatus",
        "WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId AND ${Db.STATUS}='${Status.AKTIV}'",
    ).execute(
        params =
            mutableMapOf(
                "vedtaksperiodeId" to vedtaksperiodeId,
                "nyStatus" to status.name,
            ),
        session = session,
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

    fun hentForespoerselForForespoerselId(
        forespoerselId: UUID,
        statuser: Set<Status>,
    ): ForespoerselDto? =
        hentVedtaksperiodeId(forespoerselId)?.let { vedtaksperiodeId ->
            hentForespoerselForVedtaksperiodeId(
                vedtaksperiodeId = vedtaksperiodeId,
                statuser = statuser,
            )
                .maxByOrNull { it.opprettet }
        }

    fun hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId: UUID): ForespoerselDto? =
        hentForespoerselForVedtaksperiodeId(vedtaksperiodeId, setOf(Status.AKTIV))
            .also { forespoersler ->
                if (forespoersler.size > 1) {
                    "Fant flere aktive forespørsler for vedtaksperiode: $vedtaksperiodeId".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
            }
            .maxByOrNull { it.opprettet }

    private fun hentForespoerselForVedtaksperiodeId(
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

    fun forespoerselIdKnyttetTilOppgaveIPortalen(vedtaksperiodeId: UUID): UUID? {
        val forespoersler = hentAlleForespoerslerKnyttetTil(vedtaksperiodeId).sortedBy { it.opprettet }
        val besvarteIndekser =
            forespoersler.mapIndexedNotNull { index, forespoersel ->
                if (forespoersel.status == Status.BESVART) {
                    index
                } else {
                    null
                }
            }
        return besvarteIndekser
            .fold(listOf(forespoersler)) { acc, cur ->
                acc.last().splitOnIndex(cur + 1).toList()
            }
            .lastOrNull { it.isNotEmpty() }
            ?.firstOrNull()
            ?.forespoerselId
    }

    fun hentAlleForespoerslerKnyttetTil(vedtaksperiodeId: UUID): List<ForespoerselDto> =
        query(
            "SELECT * FROM forespoersel f",
            "LEFT JOIN besvarelse_metadata b ON f.id=b.${Db.FK_FORESPOERSEL_ID}",
            "WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId",
        )
            .listResult(
                params = mapOf("vedtaksperiodeId" to vedtaksperiodeId),
                dataSource = dataSource,
                transform = Row::toForespoerselDto,
            )

    private fun hentVedtaksperiodeId(forespoerselId: UUID): UUID? =
        "SELECT ${Db.VEDTAKSPERIODE_ID} FROM forespoersel WHERE ${Db.FORESPOERSEL_ID}=:forespoerselId"
            .nullableResult(
                params = mapOf("forespoerselId" to forespoerselId),
                dataSource = dataSource,
                transform = { Db.VEDTAKSPERIODE_ID.let(::uuid) },
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

/** Kun brukt for å få penere kodeformat på queries fordelt over flere linjer. */
private fun query(vararg parts: String): String = parts.joinToString(separator = " ")
