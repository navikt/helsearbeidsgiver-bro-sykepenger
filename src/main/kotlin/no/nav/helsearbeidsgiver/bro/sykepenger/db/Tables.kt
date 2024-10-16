package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotlinx.serialization.builtins.MapSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object ForespoerselTable : Table("forespoersel") {
    val id =
        long("id").autoIncrement(
            idSeqName = "forespoersel_id_seq",
        )
    val forespoerselId = uuid("forespoersel_id")

    // Denne er varchar av udefinert lengde i databasen
    val type = text("type")
    val status = varchar("status", 50)
    val orgnr = varchar("orgnr", 50)
    val fnr = varchar("fnr", 50)
    val vedtaksperiodeId = uuid("vedtaksperiode_id")
    val egenmeldingsperioder = jsonb("egenmeldingsperioder", jsonConfig, Periode.serializer().list())
    val sykmeldingsperioder = jsonb("sykmeldingsperioder", jsonConfig, Periode.serializer().list())
    val bestemmendeFravaersdager = jsonb("bestemmende_fravaersdager", jsonConfig, bestemmendeFravaersdagerSerializer)
    val forespurtData = jsonb("forespurt_data", jsonConfig, SpleisForespurtDataDto.serializer().list())
    val opprettet = datetime("opprettet")
    val oppdatert = datetime("oppdatert")
    val kastetTilInfotrygd = datetime("kastet_til_infotrygd").nullable()

    override val primaryKey = PrimaryKey(id)
}

object BesvarelseTable : Table("besvarelse_metadata") {
    private val id =
        integer("id").autoIncrement(
            idSeqName = "besvarelse_metadata_id_seq",
        )
    val fkForespoerselId = long("fk_forespoersel_id") references ForespoerselTable.id
    val besvart = datetime("forespoersel_besvart")
    val inntektsmeldingId = uuid("inntektsmelding_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

val bestemmendeFravaersdagerSerializer = MapSerializer(Orgnr.serializer(), LocalDateSerializer)
