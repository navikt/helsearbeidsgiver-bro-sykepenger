package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type.BEGRENSET
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.til
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockBegrensetForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselDaoTest : FunSpecWithDb(listOf(ForespoerselTable, BesvarelseTable), { db ->
    val forespoerselDao = ForespoerselDao(db)

    fun ForespoerselDto.lagreNotNull(): Long = forespoerselDao.lagre(this).shouldNotBeNull()

    test("Lagre forespørsel i databasen") {
        val forespoersel = mockForespoerselDto()

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = db.hentForespoersel(id).shouldNotBeNull()

        db.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBe forespoersel
    }

    test("Forkaster alle aktive forespørsler knyttet til en vedtaksperiodeId når ny forespørsel med lik vedtaksperiodeId mottas") {
        val (id1, id2) =
            List(2) {
                mockForespoerselDto().lagreNotNull()
            }
        db.oppdaterStatus(id1, Status.AKTIV)

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
                .map(db::hentForespoersel)
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
            actual.shouldBeEqualToIgnoringFields(
                expected,
                ForespoerselDto::status,
                ForespoerselDto::skjaeringstidspunkt,
                ForespoerselDto::oppdatert,
            )
            actual.status shouldBe Status.FORKASTET
            actual.skjaeringstidspunkt shouldBe 17.januar
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
                    ?.copy(skjaeringstidspunkt = null)
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

                db.oppdaterStatus(aktivForespoerselId, Status.AKTIV)

                // Verifiser status på lagrede forespørsler
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = foersteForespoersel.vedtaksperiodeId,
                    statuser = Status.entries.toSet(),
                )
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
                        ?.copy(skjaeringstidspunkt = null)
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

                db.oppdaterStatus(aktivForespoerselId, Status.AKTIV)

                // Verifiser status på lagrede forespørsler
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = foersteForespoersel.vedtaksperiodeId,
                    statuser = Status.entries.toSet(),
                )
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
                        ?.copy(skjaeringstidspunkt = null)
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
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = foersteForespoersel.vedtaksperiodeId,
                    statuser = Status.entries.toSet(),
                )
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
                        ?.copy(skjaeringstidspunkt = null)
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

                db.oppdaterStatus(gammelForespoerselId, Status.AKTIV)

                // Verifiser status på lagrede forespørsler
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = gammelForespoersel.vedtaksperiodeId,
                    statuser = Status.entries.toSet(),
                )
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
                        ?.copy(skjaeringstidspunkt = null)
                        .shouldNotBeNull()

                actualForespoersel shouldBe nyForespoersel
            }
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            mockForespoerselDto()
                .copy(forespoerselId = randomUuid())
                .lagreNotNull()

            db.antallForespoersler() shouldBeExactly 1

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

            db.antallForespoersler() shouldBeExactly 2

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
                    ?.copy(skjaeringstidspunkt = null)
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

            db.oppdaterStatus(gammelForespoerselId, Status.AKTIV)

            val actualForespoersel =
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(gammelForespoersel.vedtaksperiodeId)
                    ?.copy(skjaeringstidspunkt = null)
                    .shouldNotBeNull()

            actualForespoersel shouldBe nyForespoersel
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            mockForespoerselDto()
                .copy(vedtaksperiodeId = randomUuid())
                .lagreNotNull()

            db.antallForespoersler() shouldBeExactly 1

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

            db.antallForespoersler() shouldBeExactly 2

            forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
                .shouldBeNull()
        }
    }

    test("Ruller tilbake forkasting av aktive forespørsler når lagring av ny forespørsel feiler") {
        val (id1, id2) =
            List(2) {
                mockForespoerselDto().lagreNotNull()
            }

        shouldThrowAny {
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
                .map(db::hentForespoersel)
                .map { it.shouldNotBeNull() }

        forespoersel1.status shouldBe Status.FORKASTET
        forespoersel2.status shouldBe Status.AKTIV

        val id3 = id2 + 1

        db.hentForespoersel(id3).shouldBeNull()
    }

    test("Lagre forespørsel med begrenset forespurt data i databasen") {
        val forespoersel =
            mockForespoerselDto().copy(
                type = BEGRENSET,
                forespurtData = mockBegrensetForespurtDataListe(),
            )

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = db.hentForespoersel(id).shouldNotBeNull()

        db.antallForespoersler() shouldBeExactly 1
        lagretForespoersel shouldBe forespoersel
    }

    test("Lagre forespørsel uten skjæringstidspunkt i databasen") {
        val forespoersel =
            mockForespoerselDto().copy(
                type = BEGRENSET,
                skjaeringstidspunkt = null,
            )

        val id = forespoersel.lagreNotNull()
        val lagretForespoersel = db.hentForespoersel(id).shouldNotBeNull()

        db.antallForespoersler() shouldBeExactly 1
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

            val forespoersel1 = db.hentForespoersel(id1)
            val forespoersel2 = db.hentForespoersel(id2)

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

            val forespoersel1 = db.hentForespoersel(id1)
            val forespoersel2 = db.hentForespoersel(id2)

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

            val forespoersel = db.hentForespoersel(forespoerselId).shouldNotBeNull()

            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId

            db.antallBesvarelser() shouldBeExactly 1
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

            val forespoersel = db.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SPLEIS
            forespoersel?.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel?.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId2

            db.antallBesvarelser() shouldBeExactly 1
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

            val forespoersel = db.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SPLEIS
            forespoersel?.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel?.besvarelse?.inntektsmeldingId shouldBe null

            db.antallBesvarelser() shouldBeExactly 1
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

            val forespoersel1 = db.hentForespoersel(id1).shouldNotBeNull()
            val forespoersel2 = db.hentForespoersel(id2).shouldNotBeNull()

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

            val forespoersel = db.hentForespoersel(forespoerselId).shouldNotBeNull()

            forespoersel.status shouldBe Status.BESVART_SIMBA
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 23.mars.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe null

            db.antallBesvarelser() shouldBeExactly 1
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

            val forespoersel = db.hentForespoersel(forespoerselId).shouldNotBeNull()

            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 3.februar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId

            db.antallBesvarelser() shouldBeExactly 1
        }
    }

    test("Ved oppdatering settes oppdatert-kolonne automatisk") {
        val id = mockForespoerselDto().lagreNotNull()

        val foerOppdatering = db.hentForespoersel(id).shouldNotBeNull()

        db.oppdaterStatus(id, Status.BESVART_SPLEIS)

        val etterOppdatering = db.hentForespoersel(id).shouldNotBeNull()

        foerOppdatering.status shouldBe Status.AKTIV
        etterOppdatering.status shouldBe Status.BESVART_SPLEIS
        foerOppdatering.oppdatert shouldNotBe etterOppdatering.oppdatert
    }

    context(ForespoerselDao::oppdaterForespoerslerSomForkastet.name) {

        test("Oppdaterer aktiv forespørsel til forkastet") {
            val forespoerselId = mockForespoerselDto().lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = db.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.FORKASTET
            forespoersel?.besvarelse shouldBe null
        }

        test("Oppdaterer ikke besvart fra Simba til forkastet") {
            val forespoerselId = mockForespoerselDto().copy(status = Status.BESVART_SIMBA).lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = db.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SIMBA
        }

        test("Oppdaterer ikke besvart fra Spleis til forkastet") {
            val forespoerselId = mockForespoerselDto().copy(status = Status.BESVART_SPLEIS).lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

            val forespoersel = db.hentForespoersel(forespoerselId)
            forespoersel?.status shouldBe Status.BESVART_SPLEIS
        }
    }

    context(ForespoerselDao::hentForespoerslerEksponertTilSimba.name) {

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

            db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idB)?.status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idC)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idD)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idE)?.status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()
                    ?.forespoerselId

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idC)?.forespoerselId
        }

        test("én besvart fra Spleis og én besvart fra Simba") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idC = mockForespoerselDto().lagreNotNull()
            val idD = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idB)?.status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idC)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idD)?.status shouldBe Status.BESVART_SIMBA

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idC)?.forespoerselId
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

            db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idC)?.status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA)?.forespoerselId
        }

        test("én besvart fra Simba og én aktiv") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            val idB = mockForespoerselDto().lagreNotNull()

            db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SIMBA
            db.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idB)?.forespoerselId
        }

        test("én besvart fra Spleis og én aktiv") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idB = mockForespoerselDto().lagreNotNull()

            db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idB)?.forespoerselId
        }

        test("ingen besvarte forespørsler") {
            val idA = mockForespoerselDto().lagreNotNull()
            val idB = mockForespoerselDto().lagreNotNull()

            db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idB)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA)?.forespoerselId
        }

        test("én aktiv forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()

            db.hentForespoersel(idA)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA)?.forespoerselId
        }

        test("kun én besvart (fra Simba) forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SIMBA

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA)?.forespoerselId
        }

        test("kun én besvart (fra Spleis) forespoersel") {
            val idA = mockForespoerselDto().lagreNotNull()
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA)?.forespoerselId
        }

        test("Finner ingen forespørselId som vi har sendt portalen") {
            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId
            forespoerselIdEksponertTilSimba shouldBe null
        }

        test("tåler mer enn to eksponerte forespørsler (med ujevnt antall forespørsler mellom)") {
            val idA = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idB = mockForespoerselDto().lagreNotNull()
            val idC = mockForespoerselDto().lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idD = mockForespoerselDto().lagreNotNull()

            db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
            db.hentForespoersel(idC)?.status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idD)?.status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerslerEksponertTilSimba(listOf(MockUuid.vedtaksperiodeId))
                    .firstOrNull()?.forespoerselId

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idD)?.forespoerselId
        }
    }

    context(ForespoerselDao::hentForespoerslerForVedtaksperiodeId.name) {

        test("Henter alle forespørsler knyttet til en vedtaksperiodeId") {
            val a = mockForespoerselDto()
            val b = mockForespoerselDto().oekOpprettet(1)
            val c = mockForespoerselDto().oekOpprettet(2)

            a.lagreNotNull()
            b.lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            c.lagreNotNull()

            val actual =
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    statuser = Status.entries.toSet(),
                )

            actual shouldHaveSize 3

            actual[0].status shouldBe Status.FORKASTET
            actual[0].shouldBeEqualToIgnoringFields(
                a,
                ForespoerselDto::status,
                ForespoerselDto::skjaeringstidspunkt,
                ForespoerselDto::besvarelse,
                ForespoerselDto::oppdatert,
            )

            actual[1].status shouldBe Status.BESVART_SPLEIS
            actual[1].shouldBeEqualToIgnoringFields(
                b,
                ForespoerselDto::status,
                ForespoerselDto::skjaeringstidspunkt,
                ForespoerselDto::besvarelse,
                ForespoerselDto::oppdatert,
            )

            actual[2].status shouldBe Status.AKTIV
            actual[2].shouldBeEqualToIgnoringFields(
                c,
                ForespoerselDto::status,
                ForespoerselDto::skjaeringstidspunkt,
                ForespoerselDto::besvarelse,
                ForespoerselDto::oppdatert,
            )
        }

        test("Henter forespørsler med gitt status som er knyttet til en vedtaksperiodeId") {
            val a = mockForespoerselDto()
            val b = mockForespoerselDto().oekOpprettet(1)
            val c = mockForespoerselDto().oekOpprettet(2)
            val d = mockForespoerselDto().oekOpprettet(3)

            a.lagreNotNull()
            b.lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            c.lagreNotNull()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            d.lagreNotNull()

            val actual =
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    statuser = setOf(Status.BESVART_SPLEIS),
                )

            actual shouldHaveSize 2

            actual[0].status shouldBe Status.BESVART_SPLEIS
            actual[0].shouldBeEqualToIgnoringFields(
                b,
                ForespoerselDto::status,
                ForespoerselDto::skjaeringstidspunkt,
                ForespoerselDto::besvarelse,
                ForespoerselDto::oppdatert,
            )

            actual[1].status shouldBe Status.BESVART_SPLEIS
            actual[1].shouldBeEqualToIgnoringFields(
                c,
                ForespoerselDto::status,
                ForespoerselDto::skjaeringstidspunkt,
                ForespoerselDto::besvarelse,
                ForespoerselDto::oppdatert,
            )
        }
    }

    context(ForespoerselDao::hentAktiveForespoerslerForOrgnrOgFnr.name) {
        test("Henter kun aktive forespørsler for korrekt orgnr og fnr") {
            val orgnr = "517780391".let(::Orgnr)
            val fnr = Fnr.genererGyldig().verdi
            val vedtaksperiodeId1 = UUID.randomUUID()
            val vedtaksperiodeId2 = UUID.randomUUID()

            val forespoersel1Gruppe1 =
                mockForespoerselDto().copy(
                    orgnr = orgnr,
                    fnr = fnr,
                    vedtaksperiodeId = vedtaksperiodeId1,
                    egenmeldingsperioder = listOf(23.oktober til 23.oktober),
                    sykmeldingsperioder = listOf(24.oktober til 24.oktober),
                )

            val forespoersel2Gruppe1 =
                mockForespoerselDto().copy(
                    orgnr = orgnr,
                    fnr = fnr,
                    vedtaksperiodeId = vedtaksperiodeId1,
                    sykmeldingsperioder = listOf(23.oktober til 24.oktober),
                    opprettet = LocalDateTime.now().plusSeconds(3).truncMillis(),
                )

            val forespoersel3Gruppe1 =
                mockForespoerselDto().copy(
                    orgnr = orgnr,
                    fnr = fnr,
                    vedtaksperiodeId = vedtaksperiodeId1,
                    sykmeldingsperioder = listOf(25.oktober til 25.oktober),
                    opprettet = LocalDateTime.now().plusSeconds(6).truncMillis(),
                )

            val forespoerselGruppe2 =
                mockForespoerselDto().copy(
                    orgnr = orgnr,
                    fnr = fnr,
                    vedtaksperiodeId = vedtaksperiodeId2,
                    sykmeldingsperioder = listOf(3.november til 6.november),
                )

            val forespoerselAnnetOrgnr =
                mockForespoerselDto().copy(
                    orgnr = "999303111".let(::Orgnr),
                    vedtaksperiodeId = UUID.randomUUID(),
                    sykmeldingsperioder = listOf(5.august til 5.august),
                )

            val forespoerselAnnetFnr =
                mockForespoerselDto().copy(
                    fnr = Fnr.genererGyldig().verdi,
                    vedtaksperiodeId = UUID.randomUUID(),
                    sykmeldingsperioder = listOf(5.august til 5.august),
                )

            // Skal ikke hentes (er forkastet)
            forespoerselDao.lagre(forespoersel1Gruppe1)

            // Skal ikke hentes (er besvart)
            forespoerselDao.lagre(forespoersel2Gruppe1)
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId1,
                LocalDateTime.now().plusSeconds(9).truncMillis(),
            )

            forespoerselDao.lagre(forespoersel3Gruppe1)
            forespoerselDao.lagre(forespoerselGruppe2)

            // Skal ikke hentes (annet orgnr)
            forespoerselDao.lagre(forespoerselAnnetOrgnr)

            // Skal ikke hentes (annet fnr)
            forespoerselDao.lagre(forespoerselAnnetFnr)

            val actualForespoersler = forespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(orgnr, fnr)

            actualForespoersler.size shouldBeExactly 2

            actualForespoersler[0].copy(skjaeringstidspunkt = null) shouldBe forespoersel3Gruppe1
            actualForespoersler[1].copy(skjaeringstidspunkt = null) shouldBe forespoerselGruppe2
        }
    }

    context(::tilForespoerselDto.name) {
        test("Sett skjæringstidspunkt til minste bestemmende fraværsdag blant andre arbeidsgivere") {
            val forespoersel = mockForespoerselDto()
            val annetOrgnr = Orgnr("592864023")
            val tredjeOrgnr = Orgnr("046764589")
            val bestemmendeFravaersdager =
                mapOf(
                    forespoersel.orgnr to 3.mars,
                    annetOrgnr to 5.mars,
                    tredjeOrgnr to 7.mars,
                )

            val id =
                forespoersel.copy(
                    skjaeringstidspunkt = null,
                    bestemmendeFravaersdager = bestemmendeFravaersdager,
                )
                    .lagreNotNull()

            val actual = db.hentForespoerselRow(id)?.let(::tilForespoerselDto)

            actual.shouldNotBeNull()
            actual.skjaeringstidspunkt shouldBe 5.mars
            actual.bestemmendeFravaersdager shouldBe bestemmendeFravaersdager
        }

        test("Dersom bestemmende fraværsdager mangler, legg til skjæringstidspunkt som bestemmende fraværsdag med ukjent orgnr") {
            val skjaeringstidspunkt = 6.juni

            val id =
                mockForespoerselDto().copy(
                    bestemmendeFravaersdager = emptyMap(),
                )
                    .lagreNotNull()

            val actual =
                db.hentForespoerselRow(id)
                    ?.also {
                        it[ForespoerselTable.skjaeringstidspunkt] = skjaeringstidspunkt
                    }
                    ?.let(::tilForespoerselDto)

            actual.shouldNotBeNull()
            actual.skjaeringstidspunkt shouldBe skjaeringstidspunkt
            actual.bestemmendeFravaersdager shouldBe mapOf(Orgnr("000000000") to skjaeringstidspunkt)
        }

        test("Dersom både bestemmende fraværsdager og skjæringstidspunkt mangler så er begge tomme") {
            val id =
                mockForespoerselDto().copy(
                    bestemmendeFravaersdager = emptyMap(),
                )
                    .lagreNotNull()

            val actual =
                db.hentForespoerselRow(id)
                    ?.let(::tilForespoerselDto)

            actual.shouldNotBeNull()
            actual.skjaeringstidspunkt.shouldBeNull()
            actual.bestemmendeFravaersdager.shouldBeEmpty()
        }
    }
})

private fun Database.hentForespoersel(id: Long): ForespoerselDto? =
    hentForespoerselRow(id)
        ?.let(::tilForespoerselDto)
        ?.copy(skjaeringstidspunkt = null)

private fun Database.hentForespoerselRow(id: Long): ResultRow? =
    transaction(this) {
        ForespoerselTable.join(
            BesvarelseTable,
            JoinType.LEFT,
            ForespoerselTable.id,
            BesvarelseTable.fkForespoerselId,
        )
            .selectAll()
            .where {
                ForespoerselTable.id eq id
            }
            .firstOrNull()
    }

private fun Database.oppdaterStatus(
    id: Long,
    nyStatus: Status,
) {
    transaction(this) {
        ForespoerselTable.update({
            ForespoerselTable.id eq id
        }) {
            it[status] = nyStatus.name
        }
    }
}

private fun Database.antallForespoersler(): Int =
    transaction(this) {
        ForespoerselTable.selectAll().count()
    }.toInt()

private fun Database.antallBesvarelser(): Int =
    transaction(this) {
        BesvarelseTable.selectAll().count()
    }.toInt()

private fun ForespoerselDto.oekOpprettet(sekunder: Long): ForespoerselDto = copy(opprettet = opprettet.plusSeconds(sekunder))

private fun now(): LocalDateTime = LocalDateTime.now().truncMillis()

private fun Int.timerSiden(): LocalDateTime = LocalDateTime.now().minusHours(this.toLong()).truncMillis()
