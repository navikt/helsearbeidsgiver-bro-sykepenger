package no.nav.helsearbeidsgiver.bro.sykepenger.db

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
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
    val skjaeringstidspunkt = date("skjaeringstidspunkt").nullable()
    val sykmeldingsperioder = jsonb("sykmeldingsperioder", jsonConfig, Periode.serializer().list())
    val egenmeldingsperioder = jsonb("egenmeldingsperioder", jsonConfig, Periode.serializer().list())
    val forespurtData = jsonb("forespurt_data", jsonConfig, SpleisForespurtDataDto.serializer().list())
    val opprettet = datetime("opprettet")
    val oppdatert = datetime("oppdatert")

    override val primaryKey = PrimaryKey(id)
}

object BesvarelseTable : Table("besvarelse_metadata") {
    val id =
        integer("id").autoIncrement(
            idSeqName = "besvarelse_metadata_id_seq",
        )
    val fkForespoerselId = long("fk_forespoersel_id") references ForespoerselTable.id
    val besvart = datetime("forespoersel_besvart")
    val inntektsmeldingId = uuid("inntektsmelding_id").nullable()

    override val primaryKey = PrimaryKey(id)
}
