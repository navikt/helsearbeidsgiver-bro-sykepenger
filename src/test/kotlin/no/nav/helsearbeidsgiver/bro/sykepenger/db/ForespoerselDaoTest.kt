package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.Row
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type.BEGRENSET
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockBegrensetForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.execute
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.nullableResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.postgresql.util.PSQLException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

class ForespoerselDaoTest : AbstractDatabaseFunSpec({ dataSource ->
    val forespoerselDao = ForespoerselDao(dataSource)

    fun ForespoerselDto.lagreNotNull(): Long = forespoerselDao.lagre(this).shouldNotBeNull()

    test("Lagre forespørsel i databasen") {
        val forespoersel = mockForespoerselDto()

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = dataSource.hentForespoersel(id).shouldNotBeNull()

        dataSource.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBe forespoersel
    }

    test("Forkaster alle aktive forespørsler knyttet til en vedtaksperiodeId når ny forespørsel med lik vedtaksperiodeId mottas") {
        val (id1, id2) =
            List(2) {
                mockForespoerselDto().lagreNotNull()
            }
        dataSource.oppdaterStatus(id1, Status.AKTIV)

        val id3 = mockForespoerselDto().lagreNotNull()
        val id4 =
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .let(ForespoerselDto::lagreNotNull)

        val (
            forespoersel1,
            forespoersel2,
            forespoersel3,
            forespoersel4,
        ) =
            listOf(id1, id2, id3, id4)
                .map(dataSource::hentForespoersel)
                .map { it.shouldNotBeNull() }

        forespoersel1.status shouldBe Status.FORKASTET
        forespoersel2.status shouldBe Status.FORKASTET
        forespoersel3.status shouldBe Status.AKTIV
        forespoersel4.status shouldBe Status.AKTIV
    }

    context("hentAktivForespoerselForForespoerselId") {
        test("Henter eneste aktive forespørsel i databasen knyttet til en forespoerselId") {
            val forkastetForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            val aktivForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .also(ForespoerselDto::lagreNotNull)

            val actualForespoersel =
                forespoerselDao.hentAktivForespoerselForForespoerselId(forkastetForespoersel.forespoerselId)
                    .shouldNotBeNull()

            actualForespoersel shouldBe aktivForespoersel
        }

        test("Skal returnere siste aktive forespørsel dersom det er flere (skal ikke skje)") {
            val gammelForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))

            val gammelForespoerselId = gammelForespoersel.let(ForespoerselDto::lagreNotNull)

            val nyForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .also(ForespoerselDto::lagreNotNull)

            dataSource.oppdaterStatus(gammelForespoerselId, Status.AKTIV)

            val actualForespoersel =
                forespoerselDao.hentAktivForespoerselForForespoerselId(gammelForespoersel.forespoerselId)
                    .shouldNotBeNull()

            actualForespoersel shouldBe nyForespoersel
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            mockForespoerselDto()
                .copy(forespoerselId = randomUuid())
                .lagreNotNull()

            dataSource.antallForespoersler() shouldBeExactly 1

            forespoerselDao.hentAktivForespoerselForForespoerselId(MockUuid.forespoerselId)
                .shouldBeNull()
        }

        test("Skal returnere 'null' dersom ingen av forespørslene er aktive") {
            mockForespoerselDto()
                .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                .let(ForespoerselDto::lagreNotNull)

            val id =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                    .let(ForespoerselDto::lagreNotNull)

            dataSource.oppdaterStatus(id, Status.BESVART)

            dataSource.antallForespoersler() shouldBeExactly 2

            forespoerselDao.hentAktivForespoerselForForespoerselId(MockUuid.forespoerselId)
                .shouldBeNull()
        }
    }

    context("hentAktivForespoerselForVedtaksperiodeId") {
        test("Henter eneste aktive forespørsel i databasen knyttet til en vedtaksperiodeId") {
            val forkastetForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            val aktivForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .also(ForespoerselDto::lagreNotNull)

            val actualForespoersel =
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(forkastetForespoersel.vedtaksperiodeId)
                    .shouldNotBeNull()

            actualForespoersel shouldBe aktivForespoersel
        }

        test("Skal returnere siste aktive forespørsel dersom det er flere (skal ikke skje)") {
            val gammelForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))

            val gammelForespoerselId = gammelForespoersel.let(ForespoerselDto::lagreNotNull)

            val nyForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .also(ForespoerselDto::lagreNotNull)

            dataSource.oppdaterStatus(gammelForespoerselId, Status.AKTIV)

            val actualForespoersel =
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(gammelForespoersel.vedtaksperiodeId)
                    .shouldNotBeNull()

            actualForespoersel shouldBe nyForespoersel
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .lagreNotNull()

            dataSource.antallForespoersler() shouldBeExactly 1

            forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
                .shouldBeNull()
        }

        test("Skal returnere 'null' dersom ingen av forespørslene er aktive") {
            mockForespoerselDto()
                .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                .let(ForespoerselDto::lagreNotNull)

            val id =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                    .let(ForespoerselDto::lagreNotNull)

            dataSource.oppdaterStatus(id, Status.BESVART)

            dataSource.antallForespoersler() shouldBeExactly 2

            forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
                .shouldBeNull()
        }
    }

    test("Ruller tilbake forkasting av aktive forespørsler når lagring av ny forespørsel feiler") {
        val (id1, id2) =
            List(2) {
                mockForespoerselDto().lagreNotNull()
            }

        shouldThrowExactly<PSQLException> {
            mockForespoerselDto()
                // Er lavere enn hva databasen takler, krasjer lagringen
                .copy(opprettet = LocalDateTime.MIN)
                .lagreNotNull()
        }

        val (
            forespoersel1,
            forespoersel2,
        ) =
            listOf(id1, id2)
                .map(dataSource::hentForespoersel)
                .map { it.shouldNotBeNull() }

        forespoersel1.status shouldBe Status.FORKASTET
        forespoersel2.status shouldBe Status.AKTIV

        val id3 = id2 + 1

        dataSource.hentForespoersel(id3).shouldBeNull()
    }

    test("Lagre forespørsel med begrenset forespurt data i databasen") {
        val forespoersel =
            mockForespoerselDto().copy(
                type = BEGRENSET,
                forespurtData = mockBegrensetForespurtDataListe(),
            )

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = dataSource.hentForespoersel(id).shouldNotBeNull()

        dataSource.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBe forespoersel
    }

    test("Lagre forespørsel uten skjæringstidspunkt i databasen") {
        val forespoersel =
            mockForespoerselDto().copy(
                type = BEGRENSET,
                skjaeringstidspunkt = null,
            )

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = dataSource.hentForespoersel(id).shouldNotBeNull()

        dataSource.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBe forespoersel
    }

    test("Oppdaterer status, inntektsmeldingId og forespørselBesvart for aktive forespørsler") {
        val id1 = mockForespoerselDto().lagreNotNull()
        val id2 = mockForespoerselDto().lagreNotNull()
        val forespoerselBesvart = LocalDateTime.now()

        forespoerselDao.oppdaterForespoerslerSomBesvart(
            MockUuid.vedtaksperiodeId,
            forespoerselBesvart,
            MockUuid.inntektsmeldingId,
        )

        val forespoersel1 = dataSource.hentForespoersel(id1)
        val forespoersel2 = dataSource.hentForespoersel(id2)

        forespoersel1?.status shouldBe Status.FORKASTET
        forespoersel1?.besvarelse shouldBe null

        forespoersel2?.status shouldBe Status.BESVART
        forespoersel2?.besvarelse?.inntektsmeldingId shouldBe MockUuid.inntektsmeldingId
        forespoersel2?.besvarelse?.forespoerselBesvart?.truncatedTo(ChronoUnit.MILLIS) shouldBe
            forespoerselBesvart.truncatedTo(
                ChronoUnit.MILLIS,
            )
    }

    test("Oppdaterer status og forespørselBesvart for aktive forespørsel som mangler inntektsmeldingId") {
        val id1 = mockForespoerselDto().lagreNotNull()
        val id2 = mockForespoerselDto().lagreNotNull()
        val forespoerselBesvart = LocalDateTime.now()

        forespoerselDao.oppdaterForespoerslerSomBesvart(MockUuid.vedtaksperiodeId, forespoerselBesvart, null)

        val forespoersel1 = dataSource.hentForespoersel(id1)
        val forespoersel2 = dataSource.hentForespoersel(id2)

        forespoersel1?.status shouldBe Status.FORKASTET
        forespoersel1?.besvarelse shouldBe null

        forespoersel2?.status shouldBe Status.BESVART
        forespoersel2?.besvarelse?.forespoerselBesvart?.truncatedTo(ChronoUnit.MILLIS) shouldBe
            forespoerselBesvart.truncatedTo(
                ChronoUnit.MILLIS,
            )
        forespoersel2?.besvarelse?.inntektsmeldingId shouldBe null
    }

    test("Hvis forespørsel er besvart skal ny besvarelse overskrive den gamle") {
        val inntektsmeldingId1 = randomUuid()
        val inntektsmeldingId2 = randomUuid()

        val forespoerselId = mockForespoerselDto().lagreNotNull()

        forespoerselDao.oppdaterForespoerslerSomBesvart(
            MockUuid.vedtaksperiodeId,
            1.januar.atStartOfDay(),
            inntektsmeldingId = inntektsmeldingId1,
        )
        forespoerselDao.oppdaterForespoerslerSomBesvart(
            MockUuid.vedtaksperiodeId,
            2.januar.atStartOfDay(),
            inntektsmeldingId = inntektsmeldingId2,
        )

        val forespoersel = dataSource.hentForespoersel(forespoerselId)
        forespoersel?.status shouldBe Status.BESVART
        forespoersel?.besvarelse?.forespoerselBesvart?.truncatedTo(ChronoUnit.MILLIS) shouldBe
            2.januar.atStartOfDay()
                .truncatedTo(ChronoUnit.MILLIS)
        forespoersel?.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId2

        dataSource.antallBesvarelser() shouldBeExactly 1
    }

    test("Hvis forespørsel er besvart skal ny besvarelse overskrive den gamle, selv når inntektsmeldingId mangler") {
        val inntektsmeldingId1 = randomUuid()
        val forespoerselId = mockForespoerselDto().lagreNotNull()

        forespoerselDao.oppdaterForespoerslerSomBesvart(
            MockUuid.vedtaksperiodeId,
            1.januar.atStartOfDay(),
            inntektsmeldingId = inntektsmeldingId1,
        )
        forespoerselDao.oppdaterForespoerslerSomBesvart(
            MockUuid.vedtaksperiodeId,
            2.januar.atStartOfDay(),
            inntektsmeldingId = null,
        )

        val forespoersel = dataSource.hentForespoersel(forespoerselId)
        forespoersel?.status shouldBe Status.BESVART
        forespoersel?.besvarelse?.forespoerselBesvart?.truncatedTo(ChronoUnit.MILLIS) shouldBe
            2.januar.atStartOfDay()
                .truncatedTo(ChronoUnit.MILLIS)
        forespoersel?.besvarelse?.inntektsmeldingId shouldBe null

        dataSource.antallBesvarelser() shouldBeExactly 1
    }

    test("Ved oppdatering settes oppdatert-kolonne automatisk") {
        val id = mockForespoerselDto().lagreNotNull()

        val foerOppdatering = dataSource.hentForespoersel(id).shouldNotBeNull()

        dataSource.oppdaterStatus(id, Status.BESVART)

        val etterOppdatering = dataSource.hentForespoersel(id).shouldNotBeNull()

        foerOppdatering.status shouldBe Status.AKTIV
        etterOppdatering.status shouldBe Status.BESVART
        foerOppdatering.oppdatert shouldNotBe etterOppdatering.oppdatert
    }

    test("Oppdaterer aktiv forespørsel til forkastet") {
        val forespoerselId = mockForespoerselDto().lagreNotNull()
        forespoerselDao.oppdaterForespoerselSomForkastet(MockUuid.vedtaksperiodeId)

        val forespoersel = dataSource.hentForespoersel(forespoerselId)
        forespoersel?.status shouldBe Status.FORKASTET
        forespoersel?.besvarelse shouldBe null
    }

    test("Oppdaterer ikke besvart forespørsel til forkastet") {
        val forespoerselId = mockForespoerselDto().copy(status = Status.BESVART).lagreNotNull()
        forespoerselDao.oppdaterForespoerselSomForkastet(MockUuid.vedtaksperiodeId)

        val forespoersel = dataSource.hentForespoersel(forespoerselId)
        forespoersel?.status shouldBe Status.BESVART
    }

    context("Henter siste forespørselId som er sendt til portalen") {

        test("flere besvarte forespørsler") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvart(
                MockUuid.vedtaksperiodeId,
                LocalDateTime.now(),
                randomUuid(),
            )

            val idC = mockForespoerselDto().lagreNotNull()
            val idD = mockForespoerselDto().lagreNotNull()
            val idE = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvart(
                MockUuid.vedtaksperiodeId,
                LocalDateTime.now(),
                randomUuid(),
            )

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.BESVART
            dataSource.hentForespoersel(idC)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idD)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idE)?.status shouldBe Status.BESVART

            val forespoerselIdKnyttetTilOppgaveIPortalen =
                forespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(MockUuid.vedtaksperiodeId)
            forespoerselIdKnyttetTilOppgaveIPortalen shouldBe dataSource.hentForespoersel(idC)?.forespoerselId
        }

        test("en besvart forespørsel og flere forkastede") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()
            val idC = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvart(
                MockUuid.vedtaksperiodeId,
                LocalDateTime.now(),
                randomUuid(),
            )

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idC)?.status shouldBe Status.BESVART

            val forespoerselIdKnyttetTilOppgaveIPortalen =
                forespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(MockUuid.vedtaksperiodeId)
            forespoerselIdKnyttetTilOppgaveIPortalen shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("ingen besvarte forespørsler") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdKnyttetTilOppgaveIPortalen =
                forespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(MockUuid.vedtaksperiodeId)
            forespoerselIdKnyttetTilOppgaveIPortalen shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("én aktiv forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()

            dataSource.hentForespoersel(idA)?.status shouldBe Status.AKTIV

            val forespoerselIdKnyttetTilOppgaveIPortalen =
                forespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(MockUuid.vedtaksperiodeId)
            forespoerselIdKnyttetTilOppgaveIPortalen shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("kun én besvart forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomBesvart(
                MockUuid.vedtaksperiodeId,
                LocalDateTime.now(),
                randomUuid(),
            )

            dataSource.hentForespoersel(idA)?.status shouldBe Status.BESVART

            val forespoerselIdKnyttetTilOppgaveIPortalen =
                forespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(MockUuid.vedtaksperiodeId)
            forespoerselIdKnyttetTilOppgaveIPortalen shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("Finner ingen forespørselId som vi har sendt portalen") {
            val forespoerselIdKnyttetTilOppgaveIPortalen =
                forespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(MockUuid.vedtaksperiodeId)
            forespoerselIdKnyttetTilOppgaveIPortalen shouldBe null
        }
    }

    test("Henter alle forespørsler knyttet til en vedtaksperiodeId") {
        val a = mockForespoerselDto()
        val b = mockForespoerselDto()
        val c = mockForespoerselDto()
        val d = mockForespoerselDto()

        a.lagreNotNull()
        b.lagreNotNull()

        forespoerselDao.oppdaterForespoerslerSomBesvart(MockUuid.vedtaksperiodeId, LocalDateTime.now(), randomUuid())

        c.lagreNotNull()
        d.lagreNotNull()

        forespoerselDao.oppdaterForespoerslerSomBesvart(MockUuid.vedtaksperiodeId, LocalDateTime.now(), randomUuid())

        val expected = listOf(a, b, c, d).map { it.forespoerselId }
        val actual =
            forespoerselDao.hentAlleForespoerslerKnyttetTil(MockUuid.vedtaksperiodeId).map { it.forespoerselId }
        expected shouldBe actual
    }
})

private fun DataSource.hentForespoersel(id: Long): ForespoerselDto? =
    "SELECT * FROM forespoersel f LEFT JOIN besvarelse_metadata b ON f.id=b.fk_forespoersel_id WHERE f.id=:id "
        .nullableResult(
            params = mapOf("id" to id),
            dataSource = this,
            transform = Row::toForespoerselDto,
        )

private fun DataSource.antallBesvarelser(): Int =
    "SELECT COUNT(1) FROM besvarelse_metadata"
        .nullableResult(
            params = emptyMap<String, Nothing>(),
            dataSource = this,
        ) { int(1) }
        .shouldNotBeNull()

private fun DataSource.antallForespoersler(): Int =
    "SELECT COUNT(1) FROM forespoersel"
        .nullableResult(
            params = emptyMap<String, Nothing>(),
            dataSource = this,
        ) { int(1) }
        .shouldNotBeNull()

private fun DataSource.oppdaterStatus(
    forespoerselId: Long,
    status: Status,
): Boolean =
    sessionOf(this).use {
        "UPDATE forespoersel SET status=:status WHERE id=:id"
            .execute(
                params =
                    mapOf(
                        "id" to forespoerselId,
                        "status" to status.name,
                    ),
                session = it,
            )
            .shouldNotBeNull()
    }
