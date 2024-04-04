package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotlinx.serialization.builtins.MapSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb
import java.util.UUID

abstract class StatusTable(name: String) : Table(name) {
    abstract val id: Column<Long>
    abstract val forespoerselId: Column<UUID>
    abstract val vedtaksperiodeId: Column<UUID>
    abstract val status: Column<String>
}

object ForespoerselTable : StatusTable("forespoersel") {
    override val id =
        long("id").autoIncrement(
            idSeqName = "forespoersel_id_seq",
        )
    override val forespoerselId = uuid("forespoersel_id")
    override val vedtaksperiodeId = uuid("vedtaksperiode_id")
    override val status = varchar("status", 50)

    val type = text("type")
    val orgnr = varchar("orgnr", 50)
    val fnr = varchar("fnr", 50)
    val egenmeldingsperioder = jsonb("egenmeldingsperioder", jsonConfig, Periode.serializer().list())
    val sykmeldingsperioder = jsonb("sykmeldingsperioder", jsonConfig, Periode.serializer().list())
    val skjaeringstidspunkt = date("skjaeringstidspunkt").nullable()
    val bestemmendeFravaersdager = jsonb("bestemmende_fravaersdager", jsonConfig, bestemmendeFravaersdagerSerializer)
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

val bestemmendeFravaersdagerSerializer = MapSerializer(Orgnr.serializer(), LocalDateSerializer)
