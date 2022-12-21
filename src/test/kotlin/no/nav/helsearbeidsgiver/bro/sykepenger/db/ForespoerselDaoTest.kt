package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
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

        val forespoerselId = forespoersel.lagreNotNull()
        val lagretForespoersel = forespoerselDao.hentForespoersel(forespoerselId).shouldNotBeNull()

        dataSource.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBeEqualToComparingFields forespoersel
    }

    test("Forkaster alle aktive forespørsler knyttet til en vedtaksperiodeId når ny forespørsel med lik vedtaksperiodeId mottas") {
        val (forespoerselId1, forespoerselId2) = List(2) {
            mockForespoerselDto().lagreNotNull()
        }
        dataSource.oppdaterStatusTilAktiv(forespoerselId1)

        val forespoerselId3 = mockForespoerselDto().lagreNotNull()
        val forespoerselId4 = mockForespoerselDto()
            .copy(vedtaksperiodeId = UUID.randomUUID())
            .let(ForespoerselDto::lagreNotNull)

        val (
            forespoersel1,
            forespoersel2,
            forespoersel3,
            forespoersel4
        ) = listOf(forespoerselId1, forespoerselId2, forespoerselId3, forespoerselId4)
            .map(forespoerselDao::hentForespoersel)
            .map { it.shouldNotBeNull() }

        forespoersel1.status shouldBeEqualComparingTo Status.FORKASTET
        forespoersel2.status shouldBeEqualComparingTo Status.FORKASTET
        forespoersel3.status shouldBeEqualComparingTo Status.AKTIV
        forespoersel4.status shouldBeEqualComparingTo Status.AKTIV
    }

    test("Henter eneste aktive forespørsel i databasen knyttet til en vedtaksperideId") {
        mockForespoerselDto()
            .copy(fom = 1.januar, tom = 31.januar)
            .also(ForespoerselDto::lagreNotNull)

        val expectedForespoersel = mockForespoerselDto()
            .copy(fom = 2.januar, tom = 30.januar)
            .also(ForespoerselDto::lagreNotNull)

        mockForespoerselDto()
            .copy(vedtaksperiodeId = UUID.randomUUID())
            .also(ForespoerselDto::lagreNotNull)

        val actualForespoersel = forespoerselDao.hentAktivForespoerselFor(MockUuid.uuid).shouldNotBeNull()

        actualForespoersel shouldBeEqualToComparingFields expectedForespoersel
    }

    test("Skal returnere siste aktive forespørsel dersom det er flere, og logge error") {
        val forespoerselId1 = mockForespoerselDto()
            .copy(fom = 1.januar, tom = 31.januar)
            .let(ForespoerselDto::lagreNotNull)

        val expectedForespoersel = mockForespoerselDto()
            .copy(fom = 2.januar, tom = 30.januar)
            .also(ForespoerselDto::lagreNotNull)

        dataSource.oppdaterStatusTilAktiv(forespoerselId1)

        val actualForespoersel = forespoerselDao.hentAktivForespoerselFor(MockUuid.uuid).shouldNotBeNull()

        actualForespoersel shouldBeEqualToComparingFields expectedForespoersel
    }
})

private fun DataSource.antallForespoersler(): Int =
    "SELECT COUNT(1) FROM forespoersel"
        .nullableResult(
            params = emptyMap<String, Nothing>(),
            dataSource = this
        ) { int(1) }
        .shouldNotBeNull()

private fun DataSource.oppdaterStatusTilAktiv(forespoerselId: Long): Boolean =
    "UPDATE forespoersel SET status = 'AKTIV' WHERE id=:id"
        .execute(
            params = mapOf("id" to forespoerselId),
            dataSource = this
        )
        .shouldNotBeNull()
