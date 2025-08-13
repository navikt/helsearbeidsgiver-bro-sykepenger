package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotlinx.serialization.builtins.MapSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object ForespoerselTable : Table("forespoersel") {
    val id =
        long("id").autoIncrement(
            idSeqName = "forespoersel_id_seq",
        )
    val forespoerselId = uuid("forespoersel_id")
    val eksponertForespoerselId = uuid("eksponert_forespoersel_id")
    val type = text("type")
    val status = text("status")
    val orgnr = varchar("orgnr", 9)
    val fnr = varchar("fnr", 11)
    val vedtaksperiodeId = uuid("vedtaksperiode_id")
    val egenmeldingsperioder = jsonb("egenmeldingsperioder", jsonConfig, Periode.serializer().list())
    val sykmeldingsperioder = jsonb("sykmeldingsperioder", jsonConfig, Periode.serializer().list())
    val bestemmendeFravaersdager = jsonb("bestemmende_fravaersdager", jsonConfig, bestemmendeFravaersdagerSerializer)
    val forespurtData = jsonb("forespurt_data", jsonConfig, SpleisForespurtDataDto.serializer().list())
    val opprettet = datetime("opprettet")
    val oppdatert = datetime("oppdatert")
    val kastetTilInfotrygd = datetime("kastet_til_infotrygd").nullable()
}

object BesvarelseTable : Table("besvarelse_metadata") {
    val fkForespoerselId = long("fk_forespoersel_id") references ForespoerselTable.id
    val besvart = datetime("forespoersel_besvart")
    val inntektsmeldingId = uuid("inntektsmelding_id").nullable()
}

val bestemmendeFravaersdagerSerializer = MapSerializer(Orgnr.serializer(), LocalDateSerializer)
