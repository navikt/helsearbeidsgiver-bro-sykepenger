package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
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
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.listResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.nullableResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.postgresql.util.PSQLException
import java.time.LocalDateTime
import java.util.UUID
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
                .lagreNotNull()

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

    context(ForespoerselDao::hentForespoerselForForespoerselId.name) {
        test("Hent ønsket forespørsel") {
            val expected = mockForespoerselDto()

            mockForespoerselDto().lagreNotNull()
            expected.lagreNotNull()
            mockForespoerselDto().lagreNotNull()

            val actual = forespoerselDao.hentForespoerselForForespoerselId(expected.forespoerselId)

            actual.shouldNotBeNull()
            actual.shouldBeEqualToIgnoringFields(expected, ForespoerselDto::status, ForespoerselDto::oppdatert)
            actual.status shouldBe Status.FORKASTET
        }

        test("Gi 'null' dersom ingen forespørsel finnes") {
            forespoerselDao.hentForespoerselForForespoerselId(randomUuid()) shouldBe null
        }
    }

    context(ForespoerselDao::hentNyesteForespoerselForForespoerselId.name) {
        test("Henter eneste forespørsel med ønsket status i databasen knyttet til en forespoerselId") {
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
                .lagreNotNull()

            val actualForespoersel =
                forespoerselDao.hentNyesteForespoerselForForespoerselId(
                    forespoerselId = forkastetForespoersel.forespoerselId,
                    statuser = setOf(Status.AKTIV),
                )
                    .shouldNotBeNull()

            actualForespoersel shouldBe aktivForespoersel
        }

        context("Returnerer nyeste forespørsel med ønsket status dersom det er flere") {
            // Forespørselen før den besvarte forblir aktiv, selv når neste forespørsel settes til besvart.
            // Skal ikke skje i den virkelige verden.
            test("inneholder forkastet, aktiv, besvart_spleis - ønsker aktiv") {
                val foersteForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                            opprettet = 2.timerSiden(),
                        )
                        .also(ForespoerselDto::lagreNotNull)

                val aktivForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                            opprettet = 1.timerSiden(),
                        )

                val aktivForespoerselId = aktivForespoersel.lagreNotNull()

                mockForespoerselDto()
                    .copy(
                        status = Status.BESVART_SPLEIS,
                        sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                        opprettet = now(),
                    )
                    .lagreNotNull()

                dataSource.oppdaterStatus(aktivForespoerselId, Status.AKTIV)

                // Verifiser status på lagrede forespørsler
                dataSource.hentAlleForespoerslerKnyttetTil(foersteForespoersel.vedtaksperiodeId)
                    .sortedBy { it.opprettet }
                    .map { it.status }
                    .shouldContainExactly(
                        Status.FORKASTET,
                        Status.AKTIV,
                        Status.BESVART_SPLEIS,
                    )

                val actualForespoersel =
                    forespoerselDao.hentNyesteForespoerselForForespoerselId(
                        forespoerselId = foersteForespoersel.forespoerselId,
                        statuser = setOf(Status.AKTIV),
                    )
                        .shouldNotBeNull()

                actualForespoersel.shouldBeEqualToIgnoringFields(aktivForespoersel, ForespoerselDto::oppdatert)
            }

            // Forespørselen før den besvarte forblir aktiv, selv når neste forespørsel settes til besvart.
            // Skal ikke skje i den virkelige verden.
            test("inneholder forkastet, aktiv, besvart_spleis - ønsker aktiv eller besvart_spleis") {
                val foersteForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                            opprettet = 2.timerSiden(),
                        )
                        .also(ForespoerselDto::lagreNotNull)

                val aktivForespoerselId =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                            opprettet = 1.timerSiden(),
                        )
                        .lagreNotNull()

                val besvartForespoersel =
                    mockForespoerselDto()
                        .copy(
                            status = Status.BESVART_SPLEIS,
                            sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                            opprettet = now(),
                        )
                        .also(ForespoerselDto::lagreNotNull)

                dataSource.oppdaterStatus(aktivForespoerselId, Status.AKTIV)

                // Verifiser status på lagrede forespørsler
                dataSource.hentAlleForespoerslerKnyttetTil(foersteForespoersel.vedtaksperiodeId)
                    .sortedBy { it.opprettet }
                    .map { it.status }
                    .shouldContainExactly(
                        Status.FORKASTET,
                        Status.AKTIV,
                        Status.BESVART_SPLEIS,
                    )

                val actualForespoersel =
                    forespoerselDao.hentNyesteForespoerselForForespoerselId(
                        forespoerselId = foersteForespoersel.forespoerselId,
                        statuser = setOf(Status.AKTIV, Status.BESVART_SPLEIS),
                    )
                        .shouldNotBeNull()

                actualForespoersel shouldBe besvartForespoersel
            }

            test("inneholder forkastet, besvart_simba, besvart_spleis, aktiv - ønsker aktiv eller besvart_spleis") {
                val foersteForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                            opprettet = 2.timerSiden(),
                        )
                        .also(ForespoerselDto::lagreNotNull)

                mockForespoerselDto()
                    .copy(
                        status = Status.BESVART_SIMBA,
                        sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                        opprettet = 1.timerSiden(),
                    )
                    .lagreNotNull()

                mockForespoerselDto()
                    .copy(
                        status = Status.BESVART_SPLEIS,
                        sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                        opprettet = 1.timerSiden(),
                    )
                    .lagreNotNull()

                val aktivForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                            opprettet = now(),
                        )
                        .also(ForespoerselDto::lagreNotNull)

                // Verifiser status på lagrede forespørsler
                dataSource.hentAlleForespoerslerKnyttetTil(foersteForespoersel.vedtaksperiodeId)
                    .sortedBy { it.opprettet }
                    .map { it.status }
                    .shouldContainExactly(
                        Status.FORKASTET,
                        Status.BESVART_SIMBA,
                        Status.BESVART_SPLEIS,
                        Status.AKTIV,
                    )

                val actualForespoersel =
                    forespoerselDao.hentNyesteForespoerselForForespoerselId(
                        forespoerselId = foersteForespoersel.forespoerselId,
                        statuser = setOf(Status.AKTIV, Status.BESVART_SPLEIS),
                    )
                        .shouldNotBeNull()

                actualForespoersel shouldBe aktivForespoersel
            }

            test("inneholder 2 aktive (skal ikke skje) - henter nyeste aktive") {
                val gammelForespoersel =
                    mockForespoerselDto()
                        .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))

                val gammelForespoerselId = gammelForespoersel.lagreNotNull()

                val nyForespoersel =
                    mockForespoerselDto()
                        .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                        .also(ForespoerselDto::lagreNotNull)

                dataSource.oppdaterStatus(gammelForespoerselId, Status.AKTIV)

                // Verifiser status på lagrede forespørsler
                dataSource.hentAlleForespoerslerKnyttetTil(gammelForespoersel.vedtaksperiodeId)
                    .sortedBy { it.opprettet }
                    .map { it.status }
                    .shouldContainExactly(
                        Status.AKTIV,
                        Status.AKTIV,
                    )

                val actualForespoersel =
                    forespoerselDao.hentNyesteForespoerselForForespoerselId(
                        forespoerselId = gammelForespoersel.forespoerselId,
                        statuser = setOf(Status.AKTIV),
                    )
                        .shouldNotBeNull()

                actualForespoersel shouldBe nyForespoersel
            }
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            mockForespoerselDto()
                .copy(forespoerselId = randomUuid())
                .lagreNotNull()

            dataSource.antallForespoersler() shouldBeExactly 1

            forespoerselDao.hentNyesteForespoerselForForespoerselId(MockUuid.forespoerselId, setOf(Status.AKTIV))
                .shouldBeNull()
        }

        test("Skal returnere 'null' dersom ingen av forespørslene har ønsket status") {
            mockForespoerselDto()
                .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                .lagreNotNull()

            mockForespoerselDto()
                .copy(
                    status = Status.BESVART_SPLEIS,
                    sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                )
                .lagreNotNull()

            dataSource.antallForespoersler() shouldBeExactly 2

            forespoerselDao.hentNyesteForespoerselForForespoerselId(MockUuid.forespoerselId, setOf(Status.AKTIV))
                .shouldBeNull()
        }
    }

    context(ForespoerselDao::hentAktivForespoerselForVedtaksperiodeId.name) {
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
                .lagreNotNull()

            val actualForespoersel =
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(forkastetForespoersel.vedtaksperiodeId)
                    .shouldNotBeNull()

            actualForespoersel shouldBe aktivForespoersel
        }

        test("Skal returnere siste aktive forespørsel dersom det er flere (skal ikke skje)") {
            val gammelForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))

            val gammelForespoerselId = gammelForespoersel.lagreNotNull()

            val nyForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                    .also(ForespoerselDto::lagreNotNull)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .lagreNotNull()

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
                .lagreNotNull()

            mockForespoerselDto()
                .copy(
                    status = Status.BESVART_SPLEIS,
                    sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                )
                .lagreNotNull()

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

    context(ForespoerselDao::oppdaterForespoerslerSomBesvartFraSpleis.name) {

        test("Oppdaterer status, inntektsmeldingId og forespørselBesvart for aktive forespørsler") {
            val id1 = mockForespoerselDto().lagreNotNull()
            val id2 = mockForespoerselDto().lagreNotNull()
            val forespoerselBesvart = now()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                forespoerselBesvart,
                MockUuid.inntektsmeldingId,
            )

            val forespoersel1 = dataSource.hentForespoersel(id1)
            val forespoersel2 = dataSource.hentForespoersel(id2)

            forespoersel1?.status shouldBe Status.FORKASTET
            forespoersel1?.besvarelse shouldBe null

            forespoersel2?.status shouldBe Status.BESVART_SPLEIS
            forespoersel2?.besvarelse?.inntektsmeldingId shouldBe MockUuid.inntektsmeldingId
            forespoersel2?.besvarelse?.forespoerselBesvart shouldBe forespoerselBesvart
        }

        test("Oppdaterer status og forespørselBesvart for aktive forespørsel som mangler inntektsmeldingId") {
            val id1 = mockForespoerselDto().lagreNotNull()
            val id2 = mockForespoerselDto().lagreNotNull()
            val forespoerselBesvart = now()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                forespoerselBesvart,
                null,
            )

            val forespoersel1 = dataSource.hentForespoersel(id1)
            val forespoersel2 = dataSource.hentForespoersel(id2)

            forespoersel1?.status shouldBe Status.FORKASTET
            forespoersel1?.besvarelse shouldBe null

            forespoersel2?.status shouldBe Status.BESVART_SPLEIS
            forespoersel2?.besvarelse?.forespoerselBesvart shouldBe forespoerselBesvart
            forespoersel2?.besvarelse?.inntektsmeldingId shouldBe null
        }

        test("Hvis forespørsel er besvart fra Simba skal ny besvarelse overskrive den gamle") {
            val inntektsmeldingId = randomUuid()

            val forespoerselId = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 1.januar.atStartOfDay(),
            )

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 2.januar.atStartOfDay(),
                inntektsmeldingId = inntektsmeldingId,
            )

            val forespoersel = dataSource.hentForespoersel(forespoerselId).shouldNotBeNull()

            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId

            dataSource.antallBesvarelser() shouldBeExactly 1
        }

        test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse overskrive den gamle") {
            val inntektsmeldingId1 = randomUuid()
            val inntektsmeldingId2 = randomUuid()

            val forespoerselId = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                1.januar.atStartOfDay(),
                inntektsmeldingId = inntektsmeldingId1,
            )
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                2.januar.atStartOfDay(),
                inntektsmeldingId = inntektsmeldingId2,
            )

            val forespoersel = dataSource.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SPLEIS
            forespoersel?.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel?.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId2

            dataSource.antallBesvarelser() shouldBeExactly 1
        }

        test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse overskrive den gamle, selv når inntektsmeldingId mangler") {
            val inntektsmeldingId1 = randomUuid()
            val forespoerselId = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                1.januar.atStartOfDay(),
                inntektsmeldingId = inntektsmeldingId1,
            )
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                2.januar.atStartOfDay(),
                inntektsmeldingId = null,
            )

            val forespoersel = dataSource.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SPLEIS
            forespoersel?.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel?.besvarelse?.inntektsmeldingId shouldBe null

            dataSource.antallBesvarelser() shouldBeExactly 1
        }
    }

    context(ForespoerselDao::oppdaterForespoerslerSomBesvartFraSimba.name) {

        test("Oppdaterer status og besvarelse for aktive forespørsler") {
            val id1 = mockForespoerselDto().lagreNotNull()
            val id2 = mockForespoerselDto().lagreNotNull()
            val besvart = now()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = besvart,
            )

            val forespoersel1 = dataSource.hentForespoersel(id1).shouldNotBeNull()
            val forespoersel2 = dataSource.hentForespoersel(id2).shouldNotBeNull()

            forespoersel1.status shouldBe Status.FORKASTET
            forespoersel1.besvarelse shouldBe null

            forespoersel2.status shouldBe Status.BESVART_SIMBA
            forespoersel2.besvarelse?.forespoerselBesvart shouldBe besvart
            forespoersel2.besvarelse?.inntektsmeldingId shouldBe null
        }

        test("Hvis forespørsel allerede er besvart fra Simba skal ny besvarelse overskrive den gamle") {
            val forespoerselId = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 1.januar.atStartOfDay(),
            )

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 23.mars.atStartOfDay(),
            )

            val forespoersel = dataSource.hentForespoersel(forespoerselId).shouldNotBeNull()

            forespoersel.status shouldBe Status.BESVART_SIMBA
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 23.mars.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe null

            dataSource.antallBesvarelser() shouldBeExactly 1
        }

        test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse IKKE overskrive den gamle") {
            val forespoerselId = mockForespoerselDto().lagreNotNull()
            val inntektsmeldingId = randomUuid()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 3.februar.atStartOfDay(),
                inntektsmeldingId = inntektsmeldingId,
            )

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 12.april.atStartOfDay(),
            )

            val forespoersel = dataSource.hentForespoersel(forespoerselId).shouldNotBeNull()

            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 3.februar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId

            dataSource.antallBesvarelser() shouldBeExactly 1
        }
    }

    test("Ved oppdatering settes oppdatert-kolonne automatisk") {
        val id = mockForespoerselDto().lagreNotNull()

        val foerOppdatering = dataSource.hentForespoersel(id).shouldNotBeNull()

        dataSource.oppdaterStatus(id, Status.BESVART_SPLEIS)

        val etterOppdatering = dataSource.hentForespoersel(id).shouldNotBeNull()

        foerOppdatering.status shouldBe Status.AKTIV
        etterOppdatering.status shouldBe Status.BESVART_SPLEIS
        foerOppdatering.oppdatert shouldNotBe etterOppdatering.oppdatert
    }

    context(ForespoerselDao::oppdaterForespoerslerSomForkastet.name) {

        test("Oppdaterer aktiv forespørsel til forkastet") {
            val forespoerselId = mockForespoerselDto().lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = dataSource.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.FORKASTET
            forespoersel?.besvarelse shouldBe null
        }

        test("Oppdaterer ikke besvart fra Simba til forkastet") {
            val forespoerselId = mockForespoerselDto().copy(status = Status.BESVART_SIMBA).lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = dataSource.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SIMBA
        }

        test("Oppdaterer ikke besvart fra Spleis til forkastet") {
            val forespoerselId = mockForespoerselDto().copy(status = Status.BESVART_SPLEIS).lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = dataSource.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SPLEIS
        }
    }

    context(ForespoerselDao::forespoerselIdEksponertTilSimba.name) {

        test("flere besvarte (fra Spleis) forespørsler") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            val idC = mockForespoerselDto().lagreNotNull()
            val idD = mockForespoerselDto().lagreNotNull()
            val idE = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.BESVART_SPLEIS
            dataSource.hentForespoersel(idC)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idD)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idE)?.status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idC)?.forespoerselId
        }

        test("én besvart fra Spleis og én besvart fra Simba") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idC = mockForespoerselDto().lagreNotNull()
            val idD = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.BESVART_SPLEIS
            dataSource.hentForespoersel(idC)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idD)?.status shouldBe Status.BESVART_SIMBA

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idC)?.forespoerselId
        }

        test("én besvart forespørsel og flere forkastede") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()
            val idC = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idC)?.status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("én besvart fra Simba og én aktiv") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            val idB = mockForespoerselDto().lagreNotNull()

            dataSource.hentForespoersel(idA)?.status shouldBe Status.BESVART_SIMBA
            dataSource.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idB)?.forespoerselId
        }

        test("én besvart fra Spleis og én aktiv") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idB = mockForespoerselDto().lagreNotNull()

            dataSource.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS
            dataSource.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idB)?.forespoerselId
        }

        test("ingen besvarte forespørsler") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            dataSource.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            dataSource.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("én aktiv forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()

            dataSource.hentForespoersel(idA)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("kun én besvart (fra Simba) forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            dataSource.hentForespoersel(idA)?.status shouldBe Status.BESVART_SIMBA

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("kun én besvart (fra Spleis) forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            dataSource.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe dataSource.hentForespoersel(idA)?.forespoerselId
        }

        test("Finner ingen forespørselId som vi har sendt portalen") {
            val forespoerselIdEksponertTilSimba =
                forespoerselDao.forespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe null
        }
    }

    context(DataSource::hentAlleForespoerslerKnyttetTil.name) {

        test("Henter alle forespørsler knyttet til en vedtaksperiodeId") {
            val a = mockForespoerselDto()
            val b = mockForespoerselDto()
            val c = mockForespoerselDto()
            val d = mockForespoerselDto()

            a.lagreNotNull()
            b.lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            c.lagreNotNull()
            d.lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            val expected = listOf(a, b, c, d).map { it.forespoerselId }
            val actual = dataSource.hentAlleForespoerslerKnyttetTil(MockUuid.vedtaksperiodeId).map { it.forespoerselId }
            expected shouldBe actual
        }
    }
})

private fun DataSource.hentForespoersel(id: Long): ForespoerselDto? =
    "SELECT * FROM forespoersel f LEFT JOIN besvarelse_metadata b ON f.id=b.fk_forespoersel_id WHERE f.id=:id "
        .nullableResult(
            params = mapOf("id" to id),
            dataSource = this,
            transform = Row::toForespoerselDto,
        )

private fun DataSource.hentAlleForespoerslerKnyttetTil(vedtaksperiodeId: UUID): List<ForespoerselDto> =
    (
        "SELECT * FROM forespoersel f " +
            "LEFT JOIN besvarelse_metadata b ON f.id=b.${Db.FK_FORESPOERSEL_ID} " +
            "WHERE ${Db.VEDTAKSPERIODE_ID}=:vedtaksperiodeId"
    )
        .listResult(
            params = mapOf("vedtaksperiodeId" to vedtaksperiodeId),
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
    id: Long,
    status: Status,
): Boolean =
    sessionOf(this).use {
        "UPDATE forespoersel SET status=:status WHERE id=:id"
            .execute(
                params =
                    mapOf(
                        "id" to id,
                        "status" to status.name,
                    ),
                session = it,
            )
            .shouldNotBeNull()
    }

private fun now(): LocalDateTime = LocalDateTime.now().truncMillis()

private fun Int.timerSiden(): LocalDateTime = LocalDateTime.now().minusHours(this.toLong()).truncMillis()
