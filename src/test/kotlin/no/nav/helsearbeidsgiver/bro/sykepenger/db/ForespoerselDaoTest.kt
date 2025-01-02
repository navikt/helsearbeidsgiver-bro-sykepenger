package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.ints.shouldBeExactly
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
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselDaoTest :
    FunSpecWithDb(listOf(ForespoerselTable, BesvarelseTable), { db ->
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
                    ForespoerselDto::oppdatert,
                )
                actual.status shouldBe Status.FORKASTET
            }

            test("Gi 'null' dersom ingen forespørsel finnes") {
                forespoerselDao.hentForespoerselForForespoerselId(randomUuid()) shouldBe null
            }
        }

        context(ForespoerselDao::hentNyesteForespoerselForForespoerselId.name) {
            test("Henter eneste forespørsel med ønsket status i databasen knyttet til en forespoerselId") {
                val eksponertId = UUID.randomUUID()

                val forkastetForespoersel =
                    mockForespoerselDto()
                        .copy(
                            forespoerselId = eksponertId,
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                        ).also(ForespoerselDto::lagreNotNull)

                val aktivForespoersel =
                    mockForespoerselDto()
                        .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                        .also(ForespoerselDto::lagreNotNull)

                // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
                mockForespoerselDto()
                    .copy(vedtaksperiodeId = randomUuid())
                    .lagreNotNull()

                val actualForespoersel =
                    forespoerselDao
                        .hentNyesteForespoerselForForespoerselId(
                            forespoerselId = forkastetForespoersel.forespoerselId,
                            statuser = setOf(Status.AKTIV),
                        ).shouldNotBeNull()

                actualForespoersel shouldBe aktivForespoersel.copy(forespoerselId = eksponertId)
            }

            context("Returnerer nyeste forespørsel med ønsket status dersom det er flere") {
                // Forespørselen før den besvarte forblir aktiv, selv når neste forespørsel settes til besvart.
                // Skal ikke skje i den virkelige verden.
                test("inneholder forkastet, aktiv, besvart_spleis - ønsker aktiv") {
                    val eksponertId = UUID.randomUUID()

                    val foersteForespoersel =
                        mockForespoerselDto()
                            .copy(
                                forespoerselId = eksponertId,
                                sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                                opprettet = 2.timerSiden(),
                            ).also(ForespoerselDto::lagreNotNull)

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
                        ).lagreNotNull()

                    db.oppdaterStatus(aktivForespoerselId, Status.AKTIV)

                    // Verifiser status på lagrede forespørsler
                    forespoerselDao
                        .hentForespoerslerForVedtaksperiodeIdListe(setOf(foersteForespoersel.vedtaksperiodeId))
                        .sortedBy { it.opprettet }
                        .map { it.status }
                        .shouldContainExactly(
                            Status.FORKASTET,
                            Status.AKTIV,
                            Status.BESVART_SPLEIS,
                        )

                    val actualForespoersel =
                        forespoerselDao
                            .hentNyesteForespoerselForForespoerselId(
                                forespoerselId = foersteForespoersel.forespoerselId,
                                statuser = setOf(Status.AKTIV),
                            ).shouldNotBeNull()

                    actualForespoersel shouldBe
                        aktivForespoersel.copy(
                            forespoerselId = eksponertId,
                            oppdatert = actualForespoersel.oppdatert, // ignorer oppdatert-felt
                        )
                }

                // Forespørselen før den besvarte forblir aktiv, selv når neste forespørsel settes til besvart.
                // Skal ikke skje i den virkelige verden.
                test("inneholder forkastet, aktiv, besvart_spleis - ønsker aktiv eller besvart_spleis") {
                    val eksponertId = UUID.randomUUID()

                    val foersteForespoersel =
                        mockForespoerselDto()
                            .copy(
                                forespoerselId = eksponertId,
                                sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                                opprettet = 2.timerSiden(),
                            ).also(ForespoerselDto::lagreNotNull)

                    val aktivForespoerselId =
                        mockForespoerselDto()
                            .copy(
                                sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                                opprettet = 1.timerSiden(),
                            ).lagreNotNull()

                    val besvartForespoersel =
                        mockForespoerselDto()
                            .copy(
                                status = Status.BESVART_SPLEIS,
                                sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                                opprettet = now(),
                            ).also(ForespoerselDto::lagreNotNull)

                    db.oppdaterStatus(aktivForespoerselId, Status.AKTIV)

                    // Verifiser status på lagrede forespørsler
                    forespoerselDao
                        .hentForespoerslerForVedtaksperiodeIdListe(setOf(foersteForespoersel.vedtaksperiodeId))
                        .sortedBy { it.opprettet }
                        .map { it.status }
                        .shouldContainExactly(
                            Status.FORKASTET,
                            Status.AKTIV,
                            Status.BESVART_SPLEIS,
                        )

                    val actualForespoersel =
                        forespoerselDao
                            .hentNyesteForespoerselForForespoerselId(
                                forespoerselId = foersteForespoersel.forespoerselId,
                                statuser = setOf(Status.AKTIV, Status.BESVART_SPLEIS),
                            ).shouldNotBeNull()

                    actualForespoersel shouldBe besvartForespoersel.copy(forespoerselId = eksponertId)
                }

                test("inneholder forkastet, besvart_simba, besvart_spleis, aktiv - ønsker aktiv eller besvart_spleis") {
                    val foersteForespoersel =
                        mockForespoerselDto()
                            .copy(
                                sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                                opprettet = 2.timerSiden(),
                            ).also(ForespoerselDto::lagreNotNull)

                    mockForespoerselDto()
                        .copy(
                            status = Status.BESVART_SIMBA,
                            sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                            opprettet = 1.timerSiden(),
                        ).lagreNotNull()

                    mockForespoerselDto()
                        .copy(
                            status = Status.BESVART_SPLEIS,
                            sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                            opprettet = 1.timerSiden(),
                        ).lagreNotNull()

                    val aktivForespoersel =
                        mockForespoerselDto()
                            .copy(
                                sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                                opprettet = now(),
                            ).also(ForespoerselDto::lagreNotNull)

                    // Verifiser status på lagrede forespørsler
                    forespoerselDao
                        .hentForespoerslerForVedtaksperiodeIdListe(setOf(foersteForespoersel.vedtaksperiodeId))
                        .sortedBy { it.opprettet }
                        .map { it.status }
                        .shouldContainExactly(
                            Status.FORKASTET,
                            Status.BESVART_SIMBA,
                            Status.BESVART_SPLEIS,
                            Status.AKTIV,
                        )

                    val actualForespoersel =
                        forespoerselDao
                            .hentNyesteForespoerselForForespoerselId(
                                forespoerselId = foersteForespoersel.forespoerselId,
                                statuser = setOf(Status.AKTIV, Status.BESVART_SPLEIS),
                            ).shouldNotBeNull()

                    // Siden den aktive kommer etter en besvart så er den også den eksponerte forespørselen
                    actualForespoersel shouldBe aktivForespoersel
                }

                test("inneholder 2 aktive (skal ikke skje) - henter nyeste aktive") {
                    val eksponertId = UUID.randomUUID()

                    val gammelForespoersel =
                        mockForespoerselDto()
                            .copy(
                                forespoerselId = eksponertId,
                                sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                            )

                    val gammelForespoerselId = gammelForespoersel.lagreNotNull()

                    val nyForespoersel =
                        mockForespoerselDto()
                            .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                            .also(ForespoerselDto::lagreNotNull)

                    db.oppdaterStatus(gammelForespoerselId, Status.AKTIV)

                    // Verifiser status på lagrede forespørsler
                    forespoerselDao
                        .hentForespoerslerForVedtaksperiodeIdListe(setOf(gammelForespoersel.vedtaksperiodeId))
                        .sortedBy { it.opprettet }
                        .map { it.status }
                        .shouldContainExactly(
                            Status.AKTIV,
                            Status.AKTIV,
                        )

                    val actualForespoersel =
                        forespoerselDao
                            .hentNyesteForespoerselForForespoerselId(
                                forespoerselId = gammelForespoersel.forespoerselId,
                                statuser = setOf(Status.AKTIV),
                            ).shouldNotBeNull()

                    actualForespoersel shouldBe nyForespoersel.copy(forespoerselId = eksponertId)
                }
            }

            context("Henter nyeste forespørsel med korrekt forespørsel-ID") {
                withData(
                    mapOf(
                        "når tidligere forespørsel er besvart fra Simba" to Status.BESVART_SIMBA,
                        "når tidligere forespørsel er besvart fra Spleis" to Status.BESVART_SPLEIS,
                    ),
                ) { besvartStatus ->
                    mockForespoerselDto()
                        .copy(
                            status = besvartStatus,
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                            opprettet = 2.timerSiden(),
                        ).lagreNotNull()

                    val eksponertForespoersel =
                        mockForespoerselDto()
                            .copy(
                                sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                                opprettet = 1.timerSiden(),
                            ).also(ForespoerselDto::lagreNotNull)

                    val aktivForespoersel =
                        mockForespoerselDto()
                            .copy(
                                sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                                opprettet = now(),
                            ).also(ForespoerselDto::lagreNotNull)

                    // Verifiser status på lagrede forespørsler
                    forespoerselDao
                        .hentForespoerslerForVedtaksperiodeIdListe(setOf(aktivForespoersel.vedtaksperiodeId))
                        .sortedBy { it.opprettet }
                        .map { it.status }
                        .shouldContainExactly(
                            besvartStatus,
                            Status.FORKASTET,
                            Status.AKTIV,
                        )

                    val actualForespoersel =
                        forespoerselDao
                            .hentNyesteForespoerselForForespoerselId(
                                forespoerselId = aktivForespoersel.forespoerselId,
                                statuser = setOf(Status.AKTIV),
                            ).shouldNotBeNull()

                    actualForespoersel shouldBe aktivForespoersel.copy(forespoerselId = eksponertForespoersel.forespoerselId)
                }
            }

            test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
                mockForespoerselDto()
                    .copy(forespoerselId = randomUuid())
                    .lagreNotNull()

                db.antallForespoersler() shouldBeExactly 1

                forespoerselDao
                    .hentNyesteForespoerselForForespoerselId(MockUuid.forespoerselId, setOf(Status.AKTIV))
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
                    ).lagreNotNull()

                db.antallForespoersler() shouldBeExactly 2

                forespoerselDao
                    .hentNyesteForespoerselForForespoerselId(MockUuid.forespoerselId, setOf(Status.AKTIV))
                    .shouldBeNull()
            }
        }

        context(ForespoerselDao::hentAktivForespoerselForVedtaksperiodeId.name) {
            test("Henter eneste aktive forespørsel i databasen knyttet til en vedtaksperiodeId") {
                val eksponertId = UUID.randomUUID()

                val forkastetForespoersel =
                    mockForespoerselDto()
                        .copy(
                            forespoerselId = eksponertId,
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                        ).also(ForespoerselDto::lagreNotNull)

                val aktivForespoersel =
                    mockForespoerselDto()
                        .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                        .also(ForespoerselDto::lagreNotNull)

                // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
                mockForespoerselDto()
                    .copy(vedtaksperiodeId = randomUuid())
                    .lagreNotNull()

                val actualForespoersel =
                    forespoerselDao
                        .hentAktivForespoerselForVedtaksperiodeId(forkastetForespoersel.vedtaksperiodeId)
                        .shouldNotBeNull()

                actualForespoersel shouldBe aktivForespoersel.copy(forespoerselId = eksponertId)
            }

            test("Skal returnere siste aktive forespørsel dersom det er flere (skal ikke skje)") {
                val eksponertId = UUID.randomUUID()

                val gammelForespoersel =
                    mockForespoerselDto()
                        .copy(
                            forespoerselId = eksponertId,
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                        )

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
                    forespoerselDao
                        .hentAktivForespoerselForVedtaksperiodeId(gammelForespoersel.vedtaksperiodeId)
                        .shouldNotBeNull()

                actualForespoersel shouldBe nyForespoersel.copy(forespoerselId = eksponertId)
            }

            context("Henter aktiv forespørsel med korrekt forespørsel-ID") {
                withData(
                    mapOf(
                        "når tidligere forespørsel er besvart fra Simba" to Status.BESVART_SIMBA,
                        "når tidligere forespørsel er besvart fra Spleis" to Status.BESVART_SPLEIS,
                    ),
                ) { besvartStatus ->
                    mockForespoerselDto()
                        .copy(
                            status = besvartStatus,
                            sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                        ).lagreNotNull()

                    val eksponertForespoersel =
                        mockForespoerselDto()
                            .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                            .also(ForespoerselDto::lagreNotNull)

                    val aktivForespoersel =
                        mockForespoerselDto()
                            .copy(sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)))
                            .also(ForespoerselDto::lagreNotNull)

                    val actualForespoersel =
                        forespoerselDao
                            .hentAktivForespoerselForVedtaksperiodeId(aktivForespoersel.vedtaksperiodeId)
                            .shouldNotBeNull()

                    actualForespoersel shouldBe aktivForespoersel.copy(forespoerselId = eksponertForespoersel.forespoerselId)
                }
            }

            test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
                mockForespoerselDto()
                    .copy(vedtaksperiodeId = randomUuid())
                    .lagreNotNull()

                db.antallForespoersler() shouldBeExactly 1

                forespoerselDao
                    .hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
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
                    ).lagreNotNull()

                db.antallForespoersler() shouldBeExactly 2

                forespoerselDao
                    .hentAktivForespoerselForVedtaksperiodeId(MockUuid.vedtaksperiodeId)
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

                val forespoersel1 = db.hentForespoersel(id1).shouldNotBeNull()
                val forespoersel2 = db.hentForespoersel(id2).shouldNotBeNull()
                val besvarelse1 = db.hentBesvarelse(id1)
                val besvarelse2 = db.hentBesvarelse(id2).shouldNotBeNull()

                forespoersel1.status shouldBe Status.FORKASTET
                besvarelse1 shouldBe null

                forespoersel2.status shouldBe Status.BESVART_SPLEIS
                besvarelse2.inntektsmeldingId shouldBe MockUuid.inntektsmeldingId
                besvarelse2.forespoerselBesvart shouldBe forespoerselBesvart
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

                val forespoersel1 = db.hentForespoersel(id1).shouldNotBeNull()
                val forespoersel2 = db.hentForespoersel(id2).shouldNotBeNull()
                val besvarelse1 = db.hentBesvarelse(id1)
                val besvarelse2 = db.hentBesvarelse(id2).shouldNotBeNull()

                forespoersel1.status shouldBe Status.FORKASTET
                besvarelse1 shouldBe null

                forespoersel2.status shouldBe Status.BESVART_SPLEIS
                besvarelse2.forespoerselBesvart shouldBe forespoerselBesvart
                besvarelse2.inntektsmeldingId shouldBe null
            }

            test("Hvis forespørsel er besvart fra Simba skal ny besvarelse overskrive den gamle") {
                val inntektsmeldingId = randomUuid()

                val id = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    besvart = 1.januar.atStartOfDay(),
                )

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    besvart = 2.januar.atStartOfDay(),
                    inntektsmeldingId = inntektsmeldingId,
                )

                val forespoersel = db.hentForespoersel(id).shouldNotBeNull()
                val besvarelse = db.hentBesvarelse(id).shouldNotBeNull()

                forespoersel.status shouldBe Status.BESVART_SPLEIS
                besvarelse.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
                besvarelse.inntektsmeldingId shouldBe inntektsmeldingId

                db.antallBesvarelser() shouldBeExactly 1
            }

            test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse overskrive den gamle") {
                val inntektsmeldingId1 = randomUuid()
                val inntektsmeldingId2 = randomUuid()

                val id = mockForespoerselDto().lagreNotNull()

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

                val forespoersel = db.hentForespoersel(id).shouldNotBeNull()
                val besvarelse = db.hentBesvarelse(id).shouldNotBeNull()

                forespoersel.status shouldBe Status.BESVART_SPLEIS
                besvarelse.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
                besvarelse.inntektsmeldingId shouldBe inntektsmeldingId2

                db.antallBesvarelser() shouldBeExactly 1
            }

            test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse overskrive den gamle, selv når inntektsmeldingId mangler") {
                val inntektsmeldingId1 = randomUuid()
                val id = mockForespoerselDto().lagreNotNull()

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

                val forespoersel = db.hentForespoersel(id).shouldNotBeNull()
                val besvarelse = db.hentBesvarelse(id).shouldNotBeNull()

                forespoersel.status shouldBe Status.BESVART_SPLEIS
                besvarelse.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
                besvarelse.inntektsmeldingId shouldBe null

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
                val besvarelse1 = db.hentBesvarelse(id1)
                val besvarelse2 = db.hentBesvarelse(id2).shouldNotBeNull()

                forespoersel1.status shouldBe Status.FORKASTET
                besvarelse1 shouldBe null

                forespoersel2.status shouldBe Status.BESVART_SIMBA
                besvarelse2.forespoerselBesvart shouldBe besvart
                besvarelse2.inntektsmeldingId shouldBe null
            }

            test("Hvis forespørsel allerede er besvart fra Simba skal ny besvarelse overskrive den gamle") {
                val id = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    besvart = 1.januar.atStartOfDay(),
                )

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                    vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                    besvart = 23.mars.atStartOfDay(),
                )

                val forespoersel = db.hentForespoersel(id).shouldNotBeNull()
                val besvarelse = db.hentBesvarelse(id).shouldNotBeNull()

                forespoersel.status shouldBe Status.BESVART_SIMBA
                besvarelse.forespoerselBesvart shouldBe 23.mars.atStartOfDay()
                besvarelse.inntektsmeldingId shouldBe null

                db.antallBesvarelser() shouldBeExactly 1
            }

            test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse IKKE overskrive den gamle") {
                val id = mockForespoerselDto().lagreNotNull()
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

                val forespoersel = db.hentForespoersel(id).shouldNotBeNull()
                val besvarelse = db.hentBesvarelse(id).shouldNotBeNull()

                forespoersel.status shouldBe Status.BESVART_SPLEIS
                besvarelse.forespoerselBesvart shouldBe 3.februar.atStartOfDay()
                besvarelse.inntektsmeldingId shouldBe inntektsmeldingId

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
                val id = mockForespoerselDto().lagreNotNull()
                forespoerselDao.oppdaterForespoerslerSomForkastet(MockUuid.vedtaksperiodeId)

                val forespoersel = db.hentForespoersel(id).shouldNotBeNull()
                val besvarelse = db.hentBesvarelse(id)

                forespoersel.status shouldBe Status.FORKASTET
                besvarelse shouldBe null
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

                val c = db.hentForespoersel(idC).shouldNotBeNull()
                val e = db.hentForespoersel(idE).shouldNotBeNull()

                db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
                db.hentForespoersel(idB)?.status shouldBe Status.BESVART_SPLEIS
                c.status shouldBe Status.FORKASTET
                db.hentForespoersel(idD)?.status shouldBe Status.FORKASTET
                e.status shouldBe Status.BESVART_SPLEIS

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(
                    e.copy(forespoerselId = c.forespoerselId),
                )
            }

            test("én besvart fra Spleis og én besvart fra Simba") {
                val idA = mockForespoerselDto().lagreNotNull()
                val idB = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

                val idC = mockForespoerselDto().lagreNotNull()
                val idD = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

                val c = db.hentForespoersel(idC).shouldNotBeNull()
                val d = db.hentForespoersel(idD).shouldNotBeNull()

                db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
                db.hentForespoersel(idB)?.status shouldBe Status.BESVART_SPLEIS
                c.status shouldBe Status.FORKASTET
                d.status shouldBe Status.BESVART_SIMBA

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(
                    d.copy(forespoerselId = c.forespoerselId),
                )
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

                val a = db.hentForespoersel(idA).shouldNotBeNull()
                val c = db.hentForespoersel(idC).shouldNotBeNull()

                a.status shouldBe Status.FORKASTET
                db.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
                c.status shouldBe Status.BESVART_SPLEIS

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(
                    c.copy(forespoerselId = a.forespoerselId),
                )
            }

            test("én besvart fra Simba og én aktiv") {
                val idA = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

                val idB = mockForespoerselDto().lagreNotNull()

                val b = db.hentForespoersel(idB).shouldNotBeNull()

                db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SIMBA
                b.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(b)
            }

            test("én besvart fra Spleis og én aktiv") {
                val idA = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

                val idB = mockForespoerselDto().lagreNotNull()

                val b = db.hentForespoersel(idB).shouldNotBeNull()

                db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS
                b.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(b)
            }

            test("ingen besvarte forespørsler") {
                val idA = mockForespoerselDto().lagreNotNull()
                val idB = mockForespoerselDto().lagreNotNull()

                val a = db.hentForespoersel(idA).shouldNotBeNull()
                val b = db.hentForespoersel(idB).shouldNotBeNull()

                a.status shouldBe Status.FORKASTET
                b.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(
                    b.copy(forespoerselId = a.forespoerselId),
                )
            }

            test("én aktiv forespoersel") {
                val idA = mockForespoerselDto().lagreNotNull()

                val a = db.hentForespoersel(idA).shouldNotBeNull()

                a.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(a)
            }

            test("kun én besvart (fra Simba) forespoersel") {
                val idA = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

                val a = db.hentForespoersel(idA).shouldNotBeNull()

                a.status shouldBe Status.BESVART_SIMBA

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(a)
            }

            test("kun én besvart (fra Spleis) forespoersel") {
                val idA = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    MockUuid.vedtaksperiodeId,
                    now(),
                    randomUuid(),
                )

                val a = db.hentForespoersel(idA).shouldNotBeNull()

                a.status shouldBe Status.BESVART_SPLEIS

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(a)
            }

            test("tåler ingen forespørsler") {
                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldBeEmpty()
            }

            test("tåler mer enn to eksponerte forespørsler (med ujevnt antall forespørsler mellom)") {
                val idA = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

                val idB = mockForespoerselDto().lagreNotNull()
                val idC = mockForespoerselDto().lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

                val idD = mockForespoerselDto().lagreNotNull()

                val d = db.hentForespoersel(idD).shouldNotBeNull()

                db.hentForespoersel(idA)?.status shouldBe Status.BESVART_SPLEIS
                db.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
                db.hentForespoersel(idC)?.status shouldBe Status.BESVART_SPLEIS
                d.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(MockUuid.vedtaksperiodeId))

                forespoerselIdEksponertTilSimba.shouldContainExactly(d)
            }

            test("henter ikke forespørsler som kun er forkastede") {
                val vid1 = UUID.randomUUID()
                val vid2 = UUID.randomUUID()
                val vid3 = UUID.randomUUID()

                val idA = mockForespoerselDto().copy(vedtaksperiodeId = vid1).lagreNotNull()
                db.oppdaterStatus(idA, Status.FORKASTET)

                val idB = mockForespoerselDto().copy(vedtaksperiodeId = vid2).lagreNotNull()
                val idC = mockForespoerselDto().copy(vedtaksperiodeId = vid2).lagreNotNull()
                db.oppdaterStatus(idC, Status.FORKASTET)

                val idD = mockForespoerselDto().copy(vedtaksperiodeId = vid3).lagreNotNull()
                val idE = mockForespoerselDto().copy(vedtaksperiodeId = vid3).lagreNotNull()

                val d = db.hentForespoersel(idD).shouldNotBeNull()
                val e = db.hentForespoersel(idE).shouldNotBeNull()

                db.hentForespoersel(idA)?.status shouldBe Status.FORKASTET
                db.hentForespoersel(idB)?.status shouldBe Status.FORKASTET
                db.hentForespoersel(idC)?.status shouldBe Status.FORKASTET
                d.status shouldBe Status.FORKASTET
                e.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vid1, vid2, vid3))

                forespoerselIdEksponertTilSimba.shouldContainExactly(
                    e.copy(forespoerselId = d.forespoerselId),
                )
            }

            test("henter forespørsler for flere vedtaksperioder") {
                val vid1 = UUID.randomUUID()
                val vid2 = UUID.randomUUID()
                val vid3 = UUID.randomUUID()

                val idA = mockForespoerselDto().copy(vedtaksperiodeId = vid1).lagreNotNull()

                val idB = mockForespoerselDto().copy(vedtaksperiodeId = vid2).lagreNotNull()
                val idC = mockForespoerselDto().copy(vedtaksperiodeId = vid2).lagreNotNull()

                val idD = mockForespoerselDto().copy(vedtaksperiodeId = vid3).lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(vid1, now())

                val a = db.hentForespoersel(idA).shouldNotBeNull()
                val b = db.hentForespoersel(idB).shouldNotBeNull()
                val c = db.hentForespoersel(idC).shouldNotBeNull()

                a.status shouldBe Status.BESVART_SIMBA
                b.status shouldBe Status.FORKASTET
                c.status shouldBe Status.AKTIV
                db.hentForespoersel(idD)?.status shouldBe Status.AKTIV

                val forespoerselIdEksponertTilSimba =
                    forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vid1, vid2))

                forespoerselIdEksponertTilSimba.shouldContainExactly(
                    a,
                    c.copy(forespoerselId = b.forespoerselId),
                )
            }
        }

        context(ForespoerselDao::hentForespoerslerForVedtaksperiodeIdListe.name) {

            test("Henter alle forespørsler knyttet til én vedtaksperiodeId") {
                val a = mockForespoerselDto()
                val b = mockForespoerselDto().oekOpprettet(1)
                val c = mockForespoerselDto().oekOpprettet(2)
                val d = mockForespoerselDto().copy(vedtaksperiodeId = UUID.randomUUID())

                a.lagreNotNull()
                b.lagreNotNull()

                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    MockUuid.vedtaksperiodeId,
                    now(),
                    randomUuid(),
                )

                c.lagreNotNull()
                d.lagreNotNull()

                val actual = forespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(MockUuid.vedtaksperiodeId))

                actual shouldHaveSize 3

                actual[0].status shouldBe Status.FORKASTET
                actual[0].shouldBeEqualToIgnoringFields(
                    a,
                    ForespoerselDto::status,
                    ForespoerselDto::oppdatert,
                )

                actual[1].status shouldBe Status.BESVART_SPLEIS
                actual[1].shouldBeEqualToIgnoringFields(
                    b,
                    ForespoerselDto::status,
                    ForespoerselDto::oppdatert,
                )

                actual[2].status shouldBe Status.AKTIV
                actual[2].shouldBeEqualToIgnoringFields(
                    c,
                    ForespoerselDto::status,
                    ForespoerselDto::oppdatert,
                )
            }

            test("Henter alle forespørsler knyttet til flere vedtaksperiodeId") {
                val a = mockForespoerselDto().copy(vedtaksperiodeId = UUID.randomUUID())
                val b = mockForespoerselDto().copy(vedtaksperiodeId = UUID.randomUUID())
                val c = mockForespoerselDto().copy(vedtaksperiodeId = b.vedtaksperiodeId)
                val d = mockForespoerselDto().copy(vedtaksperiodeId = UUID.randomUUID())

                a.lagreNotNull()
                b.lagreNotNull()
                c.lagreNotNull()
                d.lagreNotNull()

                val actual =
                    forespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(
                        setOf(a.vedtaksperiodeId, b.vedtaksperiodeId),
                    )

                actual shouldHaveSize 3

                actual[0].status shouldBe Status.AKTIV
                actual[0].shouldBeEqualToIgnoringFields(
                    a,
                    ForespoerselDto::status,
                    ForespoerselDto::oppdatert,
                )

                actual[1].status shouldBe Status.FORKASTET
                actual[1].shouldBeEqualToIgnoringFields(
                    b,
                    ForespoerselDto::status,
                    ForespoerselDto::oppdatert,
                )

                actual[2].status shouldBe Status.AKTIV
                actual[2].shouldBeEqualToIgnoringFields(
                    c,
                    ForespoerselDto::status,
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

                actualForespoersler[0] shouldBe forespoersel3Gruppe1
                actualForespoersler[1] shouldBe forespoerselGruppe2
            }
        }

        test(
            "Oppdaterer alle forespørsler knyttet til en vedtaksperiodeId med tidspunkt for når vedtaksperioden kastes til infotrygd",
        ) {
            val vid2 = UUID.randomUUID()
            val id1 = mockForespoerselDto().lagreNotNull()
            val id2 = mockForespoerselDto().lagreNotNull()
            val id3 = mockForespoerselDto().copy(vedtaksperiodeId = vid2).lagreNotNull()

            val (
                forespoersel1Foer,
                forespoersel2Foer,
                forespoersel3Foer,
            ) =
                listOf(id1, id2, id3)
                    .map(db::hentForespoersel)
                    .map { it.shouldNotBeNull() }

            forespoersel1Foer.kastetTilInfotrygd.shouldBeNull()
            forespoersel2Foer.kastetTilInfotrygd.shouldBeNull()
            forespoersel3Foer.kastetTilInfotrygd.shouldBeNull()

            forespoerselDao.markerKastetTilInfotrygd(MockUuid.vedtaksperiodeId)

            val (
                forespoersel1Etter,
                forespoersel2Etter,
                forespoersel3Etter,
            ) =
                listOf(id1, id2, id3)
                    .map(db::hentForespoersel)
                    .map { it.shouldNotBeNull() }

            forespoersel1Etter.kastetTilInfotrygd.shouldNotBeNull()
            forespoersel2Etter.kastetTilInfotrygd.shouldNotBeNull()
            forespoersel3Etter.kastetTilInfotrygd.shouldBeNull()
            forespoersel1Etter.kastetTilInfotrygd shouldBe forespoersel2Etter.kastetTilInfotrygd
        }
    })

private data class Besvarelse(
    val forespoerselBesvart: LocalDateTime,
    val inntektsmeldingId: UUID?,
)

private fun Database.hentForespoersel(id: Long): ForespoerselDto? =
    hentForespoerselRow(id)
        ?.let(::tilForespoerselDto)

private fun Database.hentForespoerselRow(id: Long): ResultRow? =
    transaction(this) {
        ForespoerselTable
            .selectAll()
            .where {
                ForespoerselTable.id eq id
            }.firstOrNull()
    }

private fun Database.hentBesvarelse(fkId: Long): Besvarelse? =
    transaction(this) {
        BesvarelseTable
            .selectAll()
            .where {
                BesvarelseTable.fkForespoerselId eq fkId
            }.firstOrNull()
            ?.let {
                Besvarelse(
                    forespoerselBesvart = it[BesvarelseTable.besvart],
                    inntektsmeldingId = it[BesvarelseTable.inntektsmeldingId],
                )
            }
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
