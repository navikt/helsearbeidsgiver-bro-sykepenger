package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.mai
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object TestTable : StatusTable("status") {
    override val id =
        long("id").autoIncrement(
            idSeqName = "status_id_seq",
        )
    override val forespoerselId = uuid("forespoersel_id")
    override val vedtaksperiodeId = uuid("vedtaksperiode_id")
    override val status = text("status")
    val opprettet = datetime("opprettet")
}

class TestDao(override val db: Database) : StatusDao() {
    override val statusTable = TestTable

    override fun tilForespoerselDto(row: ResultRow): ForespoerselDto =
        mockForespoerselDto().copy(
            forespoerselId = row[TestTable.forespoerselId],
            vedtaksperiodeId = row[TestTable.vedtaksperiodeId],
            status = row[TestTable.status].let(Status::valueOf),
            opprettet = row[TestTable.opprettet],
        )

    fun lagre(forespoersel: ForespoerselDto): Long =
        transaction(db) {
            TestTable.insert {
                it[forespoerselId] = forespoersel.forespoerselId
                it[vedtaksperiodeId] = forespoersel.vedtaksperiodeId
                it[status] = forespoersel.status.name
                it[opprettet] = forespoersel.opprettet
            }
                .let {
                    it[TestTable.id]
                }
        }

    fun hentForespoersel(id: Long): ForespoerselDto =
        transaction(db) {
            TestTable
                .selectAll()
                .where {
                    TestTable.id eq id
                }
                .firstOrNull()
        }
            .shouldNotBeNull()
            .let(::tilForespoerselDto)
}

class GenericDaoTest : FunSpecWithDb(listOf(TestTable), { db ->
    val testDao = TestDao(db)

    context(StatusDao::hentForespoerselForForespoerselId.name) {
        test("Hent ønsket forespørsel") {
            val expected = mockForespoerselDto()

            testDao.lagre(mockForespoerselDto())
            testDao.lagre(expected)
            testDao.lagre(mockForespoerselDto())

            val actual = testDao.hentForespoerselForForespoerselId(expected.forespoerselId)

            actual.shouldNotBeNull()
            actual shouldBe expected
        }

        test("Gi 'null' dersom ingen forespørsel finnes") {
            testDao.hentForespoerselForForespoerselId(randomUuid()) shouldBe null
        }
    }

    context(StatusDao::hentAktivForespoerselForVedtaksperiodeId.name) {
        test("Henter eneste aktive forespørsel i databasen knyttet til en vedtaksperiodeId") {
            val forkastetForespoersel =
                mockForespoerselDto()
                    .copy(status = Status.FORKASTET)
                    .also(testDao::lagre)

            val aktivForespoersel = mockForespoerselDto().also(testDao::lagre)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .let(testDao::lagre)

            val actualForespoersel = testDao.hentAktivForespoerselForVedtaksperiodeId(forkastetForespoersel.vedtaksperiodeId)

            actualForespoersel.shouldNotBeNull()
            actualForespoersel shouldBe aktivForespoersel
        }

        test("Skal returnere siste aktive forespørsel dersom det er flere (skal ikke skje)") {
            val gammelForespoersel = mockForespoerselDto().also(testDao::lagre)
            val nyForespoersel = mockForespoerselDto().oekOpprettet(1).also(testDao::lagre)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .let(testDao::lagre)

            val actualForespoersel = testDao.hentAktivForespoerselForVedtaksperiodeId(gammelForespoersel.vedtaksperiodeId)

            actualForespoersel.shouldNotBeNull()
            actualForespoersel shouldBe nyForespoersel
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            testDao.lagre(
                mockForespoerselDto().copy(vedtaksperiodeId = randomUuid()),
            )

            db.antallForespoersler() shouldBeExactly 1

            testDao.hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
                .shouldBeNull()
        }

        test("Skal returnere 'null' dersom ingen av forespørslene er aktive") {
            testDao.lagre(
                mockForespoerselDto().copy(status = Status.FORKASTET),
            )

            testDao.lagre(
                mockForespoerselDto().copy(status = Status.BESVART_SPLEIS),
            )

            db.antallForespoersler() shouldBeExactly 2

            testDao.hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
                .shouldBeNull()
        }
    }

    context(StatusDao::hentForespoerslerForVedtaksperiodeId.name) {

        test("Henter alle forespørsler knyttet til en vedtaksperiodeId") {
            val a = mockForespoerselDto().copy(status = Status.FORKASTET)
            val b = mockForespoerselDto().copy(status = Status.BESVART_SPLEIS).oekOpprettet(1)
            val c = mockForespoerselDto().oekOpprettet(2)

            listOf(a, b, c).forEach(testDao::lagre)

            val actual =
                testDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    statuser = Status.entries.toSet(),
                )

            actual shouldHaveSize 3

            actual[0] shouldBe a
            actual[1] shouldBe b
            actual[2] shouldBe c
        }

        test("Henter forespørsler med gitt status som er knyttet til en vedtaksperiodeId") {
            val a = mockForespoerselDto().copy(status = Status.FORKASTET)
            val b = mockForespoerselDto().copy(status = Status.BESVART_SPLEIS).oekOpprettet(1)
            val c = mockForespoerselDto().copy(status = Status.BESVART_SPLEIS).oekOpprettet(2)
            val d = mockForespoerselDto().oekOpprettet(3)

            listOf(a, b, c, d).forEach(testDao::lagre)

            val actual =
                testDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    statuser = setOf(Status.BESVART_SPLEIS),
                )

            actual shouldHaveSize 2

            actual[0] shouldBe b
            actual[1] shouldBe c
        }
    }

    context(StatusDao::oppdaterForespoerslerSomForkastet.name) {

        test("Oppdaterer aktiv forespørsel til forkastet") {
            val id = testDao.lagre(mockForespoerselDto())

            testDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = testDao.hentForespoersel(id)

            forespoersel.status shouldBe Status.FORKASTET
        }

        test("Oppdaterer ikke besvart fra Simba til forkastet") {
            val id =
                testDao.lagre(
                    mockForespoerselDto().copy(status = Status.BESVART_SIMBA),
                )

            testDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = testDao.hentForespoersel(id)

            forespoersel.status shouldBe Status.BESVART_SIMBA
        }

        test("Oppdaterer ikke besvart fra Spleis til forkastet") {
            val id =
                testDao.lagre(
                    mockForespoerselDto().copy(status = Status.BESVART_SPLEIS),
                )

            testDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = testDao.hentForespoersel(id)

            forespoersel.status shouldBe Status.BESVART_SPLEIS
        }
    }
})

private fun Database.antallForespoersler(): Int =
    transaction(this) {
        TestTable.selectAll().count()
    }.toInt()

private fun ForespoerselDto.oekOpprettet(sekunder: Long): ForespoerselDto = copy(opprettet = opprettet.plusSeconds(sekunder))

private fun mockForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = UUID.randomUUID(),
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
        type = Type.KOMPLETT,
        status = Status.AKTIV,
        orgnr = "888444888".let(::Orgnr),
        fnr = "fnr",
        egenmeldingsperioder = emptyList(),
        sykmeldingsperioder = emptyList(),
        skjaeringstidspunkt = null,
        bestemmendeFravaersdager = emptyMap(),
        forespurtData = emptyList(),
        besvarelse = null,
        opprettet = 6.mai.kl(16, 43, 15, 0),
        oppdatert = 6.mai.kl(17, 44, 16, 1),
    )
