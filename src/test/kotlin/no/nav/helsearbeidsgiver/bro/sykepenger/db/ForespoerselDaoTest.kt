package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.Row
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.januar
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.execute
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.nullableResult
import java.util.UUID
import javax.sql.DataSource

class ForespoerselDaoTest : AbstractDatabaseFunSpec({ dataSource ->
    val forespoerselDao = ForespoerselDao(dataSource)

    fun ForespoerselDto.lagreNotNull(): Long =
        forespoerselDao.lagre(this).shouldNotBeNull()

    test("Lagre forespørsel i databasen") {
        val forespoersel = mockForespoerselDto()

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = dataSource.hentForespoersel(id).shouldNotBeNull()

        dataSource.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBeEqualToComparingFields forespoersel
    }

    test("Forkaster alle aktive forespørsler knyttet til en vedtaksperiodeId når ny forespørsel med lik vedtaksperiodeId mottas") {
        val (id1, id2) = List(2) {
            mockForespoerselDto().lagreNotNull()
        }
        dataSource.oppdaterStatus(id1, Status.AKTIV)

        val id3 = mockForespoerselDto().lagreNotNull()
        val id4 = mockForespoerselDto()
            .copy(vedtaksperiodeId = UUID.randomUUID())
            .let(ForespoerselDto::lagreNotNull)

        val (
            forespoersel1,
            forespoersel2,
            forespoersel3,
            forespoersel4
        ) = listOf(id1, id2, id3, id4)
            .map(dataSource::hentForespoersel)
            .map { it.shouldNotBeNull() }

        forespoersel1.status shouldBe Status.FORKASTET
        forespoersel2.status shouldBe Status.FORKASTET
        forespoersel3.status shouldBe Status.AKTIV
        forespoersel4.status shouldBe Status.AKTIV
    }

    test("Henter eneste aktive forespørsel i databasen knyttet til en forespoerselId") {
        mockForespoerselDto()
            .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
            .also(ForespoerselDto::lagreNotNull)

        val expectedForespoersel = mockForespoerselDto()
            .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
            .also(ForespoerselDto::lagreNotNull)

        mockForespoerselDto()
            .copy(vedtaksperiodeId = UUID.randomUUID())
            .also(ForespoerselDto::lagreNotNull)

        val actualForespoersel = forespoerselDao.hentAktivForespoerselFor(MockUuid.forespoerselId).shouldNotBeNull()

        actualForespoersel shouldBeEqualToComparingFields expectedForespoersel
    }

    test("Skal returnere siste aktive forespørsel dersom det er flere, og logge error") {
        val id1 = mockForespoerselDto()
            .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
            .let(ForespoerselDto::lagreNotNull)

        val expectedForespoersel = mockForespoerselDto()
            .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
            .also(ForespoerselDto::lagreNotNull)

        dataSource.oppdaterStatus(id1, Status.AKTIV)

        val actualForespoersel = forespoerselDao.hentAktivForespoerselFor(MockUuid.forespoerselId).shouldNotBeNull()

        actualForespoersel shouldBeEqualToComparingFields expectedForespoersel
    }

    test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
        mockForespoerselDto()
            .copy(forespoerselId = UUID.randomUUID())
            .lagreNotNull()

        dataSource.antallForespoersler() shouldBeExactly 1

        forespoerselDao.hentAktivForespoerselFor(MockUuid.forespoerselId)
            .shouldBeNull()
    }

    test("Skal returnere 'null' dersom ingen av forespørslene er aktive") {
        mockForespoerselDto()
            .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
            .let(ForespoerselDto::lagreNotNull)

        val id = mockForespoerselDto()
            .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
            .let(ForespoerselDto::lagreNotNull)

        dataSource.oppdaterStatus(id, Status.BESVART)

        dataSource.antallForespoersler() shouldBeExactly 2

        forespoerselDao.hentAktivForespoerselFor(MockUuid.forespoerselId)
            .shouldBeNull()
    }
})

private fun DataSource.hentForespoersel(id: Long): ForespoerselDto? =
    "SELECT * FROM forespoersel WHERE id=:id"
        .nullableResult(
            params = mapOf("id" to id),
            dataSource = this,
            transform = Row::toForespoerselDto
        )

private fun DataSource.antallForespoersler(): Int =
    "SELECT COUNT(1) FROM forespoersel"
        .nullableResult(
            params = emptyMap<String, Nothing>(),
            dataSource = this
        ) { int(1) }
        .shouldNotBeNull()

private fun DataSource.oppdaterStatus(forespoerselId: Long, status: Status): Boolean =
    "UPDATE forespoersel SET status=:status WHERE id=:id"
        .execute(
            params = mapOf(
                "id" to forespoerselId,
                "status" to status.name
            ),
            dataSource = this
        )
        .shouldNotBeNull()
