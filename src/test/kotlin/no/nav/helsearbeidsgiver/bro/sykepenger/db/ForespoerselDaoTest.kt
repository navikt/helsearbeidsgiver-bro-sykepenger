package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactly
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
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockBegrensetForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class ForespoerselDaoTest : FunSpecWithDb(listOf(ForespoerselTable, BesvarelseTable), { db ->
    val forespoerselDao = ForespoerselDao(db)

    fun Database.hentForespoersel(id: Long): ForespoerselDto =
        hentForespoerselRow(id)
            .let(forespoerselDao::tilForespoerselDto)
            .copy(skjaeringstidspunkt = null)

    context(ForespoerselDao::lagre.name) {
        test("Lagre forespørsel i databasen") {
            db.antallForespoersler() shouldBeExactly 0

            val forespoersel = mockForespoerselDto()

            val id = forespoerselDao.lagre(forespoersel)

            val lagretForespoersel = db.hentForespoersel(id)

            db.antallForespoersler() shouldBeExactly 1
            lagretForespoersel shouldBe forespoersel
        }

        test("Forkaster alle aktive forespørsler knyttet til en vedtaksperiodeId når ny forespørsel med lik vedtaksperiodeId mottas") {
            val id1 = forespoerselDao.lagre(mockForespoerselDto())
            val id2 = forespoerselDao.lagre(mockForespoerselDto())

            db.oppdaterStatus(id1, Status.AKTIV)

            val id3 = forespoerselDao.lagre(mockForespoerselDto())
            val id4 =
                forespoerselDao.lagre(
                    mockForespoerselDto().copy(vedtaksperiodeId = randomUuid()),
                )

            val (
                forespoersel1,
                forespoersel2,
                forespoersel3,
                forespoersel4,
            ) =
                listOf(id1, id2, id3, id4)
                    .map(db::hentForespoersel)

            forespoersel1.status shouldBe Status.FORKASTET
            forespoersel2.status shouldBe Status.FORKASTET
            forespoersel3.status shouldBe Status.AKTIV
            forespoersel4.status shouldBe Status.AKTIV
        }

        test("Ruller tilbake forkasting av aktive forespørsler når lagring av ny forespørsel feiler") {
            val id1 = forespoerselDao.lagre(mockForespoerselDto())
            val id2 = forespoerselDao.lagre(mockForespoerselDto())

            shouldThrowAny {
                forespoerselDao.lagre(
                    // Er lavere enn hva databasen takler, krasjer lagringen
                    mockForespoerselDto().copy(opprettet = LocalDateTime.MIN),
                )
            }

            val (
                forespoersel1,
                forespoersel2,
            ) =
                listOf(id1, id2)
                    .map(db::hentForespoersel)

            forespoersel1.status shouldBe Status.FORKASTET
            forespoersel2.status shouldBe Status.AKTIV

            db.antallForespoersler() shouldBeExactly 2
        }

        test("Lagre forespørsel med begrenset forespurt data i databasen") {
            val forespoersel =
                mockForespoerselDto().copy(
                    type = BEGRENSET,
                    forespurtData = mockBegrensetForespurtDataListe(),
                )

            val id = forespoerselDao.lagre(forespoersel)
            val lagretForespoersel = db.hentForespoersel(id)

            db.antallForespoersler() shouldBeExactly 1
            lagretForespoersel shouldBe forespoersel
        }

        test("Lagre forespørsel uten skjæringstidspunkt i databasen") {
            val forespoersel =
                mockForespoerselDto().copy(
                    type = BEGRENSET,
                    skjaeringstidspunkt = null,
                )

            val id = forespoerselDao.lagre(forespoersel)
            val lagretForespoersel = db.hentForespoersel(id)

            db.antallForespoersler() shouldBeExactly 1
            lagretForespoersel shouldBe forespoersel
        }
    }

    context(ForespoerselDao::hentNyesteForespoerselMedBesvarelseForForespoerselId.name) {
        test("Henter eneste forespørsel med ønsket status i databasen knyttet til en forespoerselId") {
            val forkastetForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)))
                    .also(forespoerselDao::lagre)

            val aktivForespoersel =
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                    .also(forespoerselDao::lagre)

            // Skal ikke bli plukket opp pga. annerledes vedtaksperiode-ID
            forespoerselDao.lagre(
                mockForespoerselDto().copy(vedtaksperiodeId = randomUuid()),
            )

            val actualForespoersel =
                forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
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
                        .also(forespoerselDao::lagre)

                val aktivForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                            opprettet = 1.timerSiden(),
                        )

                val aktivForespoerselId = forespoerselDao.lagre(aktivForespoersel)

                forespoerselDao.lagre(
                    mockForespoerselDto()
                        .copy(
                            status = Status.BESVART_SPLEIS,
                            sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                            opprettet = now(),
                        ),
                )

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
                    forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
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
                        .also(forespoerselDao::lagre)

                val aktivForespoerselId =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                            opprettet = 1.timerSiden(),
                        )
                        .let(forespoerselDao::lagre)

                val besvartForespoersel =
                    mockForespoerselDto()
                        .copy(
                            status = Status.BESVART_SPLEIS,
                            sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                            opprettet = now(),
                        )
                        .also(forespoerselDao::lagre)

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
                    forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
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
                        .also(forespoerselDao::lagre)

                mockForespoerselDto()
                    .copy(
                        status = Status.BESVART_SIMBA,
                        sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                        opprettet = 1.timerSiden(),
                    )
                    .let(forespoerselDao::lagre)

                mockForespoerselDto()
                    .copy(
                        status = Status.BESVART_SPLEIS,
                        sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)),
                        opprettet = 1.timerSiden(),
                    )
                    .let(forespoerselDao::lagre)

                val aktivForespoersel =
                    mockForespoerselDto()
                        .copy(
                            sykmeldingsperioder = listOf(Periode(3.januar, 29.januar)),
                            opprettet = now(),
                        )
                        .also(forespoerselDao::lagre)

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
                    forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
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

                val gammelForespoerselId = forespoerselDao.lagre(gammelForespoersel)

                val nyForespoersel =
                    mockForespoerselDto()
                        .copy(sykmeldingsperioder = listOf(Periode(2.januar, 30.januar)))
                        .also(forespoerselDao::lagre)

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
                    forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
                        forespoerselId = gammelForespoersel.forespoerselId,
                        statuser = setOf(Status.AKTIV),
                    )
                        ?.copy(skjaeringstidspunkt = null)
                        .shouldNotBeNull()

                actualForespoersel shouldBe nyForespoersel
            }
        }

        test("Skal returnere 'null' dersom ingen matchende forespørsler finnes") {
            forespoerselDao.lagre(
                mockForespoerselDto().copy(forespoerselId = randomUuid()),
            )

            db.antallForespoersler() shouldBeExactly 1

            forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
                MockUuid.forespoerselId,
                setOf(Status.AKTIV),
            )
                .shouldBeNull()
        }

        test("Skal returnere 'null' dersom ingen av forespørslene har ønsket status") {
            forespoerselDao.lagre(
                mockForespoerselDto()
                    .copy(sykmeldingsperioder = listOf(Periode(1.januar, 31.januar))),
            )

            forespoerselDao.lagre(
                mockForespoerselDto()
                    .copy(
                        status = Status.BESVART_SPLEIS,
                        sykmeldingsperioder = listOf(Periode(1.januar, 31.januar)),
                    ),
            )

            db.antallForespoersler() shouldBeExactly 2

            forespoerselDao.hentNyesteForespoerselMedBesvarelseForForespoerselId(
                MockUuid.forespoerselId,
                setOf(Status.AKTIV),
            )
                .shouldBeNull()
        }
    }

    context(ForespoerselDao::oppdaterForespoerslerSomBesvartFraSpleis.name) {

        test("Oppdaterer status, inntektsmeldingId og forespørselBesvart for aktive forespørsler") {
            val id1 = forespoerselDao.lagre(mockForespoerselDto())
            val id2 = forespoerselDao.lagre(mockForespoerselDto())
            val forespoerselBesvart = now()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                forespoerselBesvart,
                MockUuid.inntektsmeldingId,
            )

            val forespoersel1 = db.hentForespoersel(id1)
            val forespoersel2 = db.hentForespoersel(id2)

            forespoersel1.status shouldBe Status.FORKASTET
            forespoersel1.besvarelse shouldBe null

            forespoersel2.status shouldBe Status.BESVART_SPLEIS
            forespoersel2.besvarelse?.inntektsmeldingId shouldBe MockUuid.inntektsmeldingId
            forespoersel2.besvarelse?.forespoerselBesvart shouldBe forespoerselBesvart
        }

        test("Oppdaterer status og forespørselBesvart for aktive forespørsel som mangler inntektsmeldingId") {
            val id1 = forespoerselDao.lagre(mockForespoerselDto())
            val id2 = forespoerselDao.lagre(mockForespoerselDto())
            val forespoerselBesvart = now()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                forespoerselBesvart,
                null,
            )

            val forespoersel1 = db.hentForespoersel(id1)
            val forespoersel2 = db.hentForespoersel(id2)

            forespoersel1.status shouldBe Status.FORKASTET
            forespoersel1.besvarelse shouldBe null

            forespoersel2.status shouldBe Status.BESVART_SPLEIS
            forespoersel2.besvarelse?.forespoerselBesvart shouldBe forespoerselBesvart
            forespoersel2.besvarelse?.inntektsmeldingId shouldBe null
        }

        test("Hvis forespørsel er besvart fra Simba skal ny besvarelse overskrive den gamle") {
            val inntektsmeldingId = randomUuid()

            val forespoerselId = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 1.januar.atStartOfDay(),
            )

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 2.januar.atStartOfDay(),
                inntektsmeldingId = inntektsmeldingId,
            )

            val forespoersel = db.hentForespoersel(forespoerselId)

            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId

            db.antallBesvarelser() shouldBeExactly 1
        }

        test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse overskrive den gamle") {
            val inntektsmeldingId1 = randomUuid()
            val inntektsmeldingId2 = randomUuid()

            val forespoerselId = forespoerselDao.lagre(mockForespoerselDto())

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
            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId2

            db.antallBesvarelser() shouldBeExactly 1
        }

        test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse overskrive den gamle, selv når inntektsmeldingId mangler") {
            val inntektsmeldingId1 = randomUuid()
            val forespoerselId = forespoerselDao.lagre(mockForespoerselDto())

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
            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 2.januar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe null

            db.antallBesvarelser() shouldBeExactly 1
        }
    }

    context(ForespoerselDao::oppdaterForespoerslerSomBesvartFraSimba.name) {

        test("Oppdaterer status og besvarelse for aktive forespørsler") {
            val id1 = forespoerselDao.lagre(mockForespoerselDto())
            val id2 = forespoerselDao.lagre(mockForespoerselDto())
            val besvart = now()

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = besvart,
            )

            val forespoersel1 = db.hentForespoersel(id1)
            val forespoersel2 = db.hentForespoersel(id2)

            forespoersel1.status shouldBe Status.FORKASTET
            forespoersel1.besvarelse shouldBe null

            forespoersel2.status shouldBe Status.BESVART_SIMBA
            forespoersel2.besvarelse?.forespoerselBesvart shouldBe besvart
            forespoersel2.besvarelse?.inntektsmeldingId shouldBe null
        }

        test("Hvis forespørsel allerede er besvart fra Simba skal ny besvarelse overskrive den gamle") {
            val forespoerselId = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 1.januar.atStartOfDay(),
            )

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                vedtaksperiodeId = MockUuid.vedtaksperiodeId,
                besvart = 23.mars.atStartOfDay(),
            )

            val forespoersel = db.hentForespoersel(forespoerselId)

            forespoersel.status shouldBe Status.BESVART_SIMBA
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 23.mars.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe null

            db.antallBesvarelser() shouldBeExactly 1
        }

        test("Hvis forespørsel er besvart fra Spleis skal ny besvarelse IKKE overskrive den gamle") {
            val forespoerselId = forespoerselDao.lagre(mockForespoerselDto())
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

            val forespoersel = db.hentForespoersel(forespoerselId)

            forespoersel.status shouldBe Status.BESVART_SPLEIS
            forespoersel.besvarelse?.forespoerselBesvart shouldBe 3.februar.atStartOfDay()
            forespoersel.besvarelse?.inntektsmeldingId shouldBe inntektsmeldingId

            db.antallBesvarelser() shouldBeExactly 1
        }
    }

    context(ForespoerselDao::hentForespoerselIdEksponertTilSimba.name) {

        test("flere besvarte (fra Spleis) forespørsler") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())
            val idB = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            val idC = forespoerselDao.lagre(mockForespoerselDto())
            val idD = forespoerselDao.lagre(mockForespoerselDto())
            val idE = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            db.hentForespoersel(idA).status shouldBe Status.FORKASTET
            db.hentForespoersel(idB).status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idC).status shouldBe Status.FORKASTET
            db.hentForespoersel(idD).status shouldBe Status.FORKASTET
            db.hentForespoersel(idE).status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idC).forespoerselId
        }

        test("én besvart fra Spleis og én besvart fra Simba") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())
            val idB = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idC = forespoerselDao.lagre(mockForespoerselDto())
            val idD = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            db.hentForespoersel(idA).status shouldBe Status.FORKASTET
            db.hentForespoersel(idB).status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idC).status shouldBe Status.FORKASTET
            db.hentForespoersel(idD).status shouldBe Status.BESVART_SIMBA

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idC).forespoerselId
        }

        test("én besvart forespørsel og flere forkastede") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())
            val idB = forespoerselDao.lagre(mockForespoerselDto())
            val idC = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            db.hentForespoersel(idA).status shouldBe Status.FORKASTET
            db.hentForespoersel(idB).status shouldBe Status.FORKASTET
            db.hentForespoersel(idC).status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA).forespoerselId
        }

        test("én besvart fra Simba og én aktiv") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            val idB = forespoerselDao.lagre(mockForespoerselDto())

            db.hentForespoersel(idA).status shouldBe Status.BESVART_SIMBA
            db.hentForespoersel(idB).status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idB).forespoerselId
        }

        test("én besvart fra Spleis og én aktiv") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idB = forespoerselDao.lagre(mockForespoerselDto())

            db.hentForespoersel(idA).status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idB).status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idB).forespoerselId
        }

        test("ingen besvarte forespørsler") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())
            val idB = forespoerselDao.lagre(mockForespoerselDto())

            db.hentForespoersel(idA).status shouldBe Status.FORKASTET
            db.hentForespoersel(idB).status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA).forespoerselId
        }

        test("én aktiv forespoersel") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())

            db.hentForespoersel(idA).status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA).forespoerselId
        }

        test("kun én besvart (fra Simba) forespoersel") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(MockUuid.vedtaksperiodeId, now())

            db.hentForespoersel(idA).status shouldBe Status.BESVART_SIMBA

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA).forespoerselId
        }

        test("kun én besvart (fra Spleis) forespoersel") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                MockUuid.vedtaksperiodeId,
                now(),
                randomUuid(),
            )

            db.hentForespoersel(idA).status shouldBe Status.BESVART_SPLEIS

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idA).forespoerselId
        }

        test("Finner ingen forespørselId som vi har sendt portalen") {
            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)
            forespoerselIdEksponertTilSimba shouldBe null
        }

        test("tåler mer enn to eksponerte forespørsler (med ujevnt antall forespørsler mellom)") {
            val idA = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idB = forespoerselDao.lagre(mockForespoerselDto())
            val idC = forespoerselDao.lagre(mockForespoerselDto())

            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(MockUuid.vedtaksperiodeId, now(), randomUuid())

            val idD = forespoerselDao.lagre(mockForespoerselDto())

            db.hentForespoersel(idA).status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idB).status shouldBe Status.FORKASTET
            db.hentForespoersel(idC).status shouldBe Status.BESVART_SPLEIS
            db.hentForespoersel(idD).status shouldBe Status.AKTIV

            val forespoerselIdEksponertTilSimba =
                forespoerselDao.hentForespoerselIdEksponertTilSimba(MockUuid.vedtaksperiodeId)

            forespoerselIdEksponertTilSimba shouldBe db.hentForespoersel(idD).forespoerselId
        }
    }

    context(ForespoerselDao::tilForespoerselDto.name) {
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
                    .let(forespoerselDao::lagre)

            val actual = db.hentForespoerselRow(id).let(forespoerselDao::tilForespoerselDto)

            actual.skjaeringstidspunkt shouldBe 5.mars
            actual.bestemmendeFravaersdager shouldBe bestemmendeFravaersdager
        }

        test("Dersom bestemmende fraværsdager mangler, bruk skjæringstidspunkt") {
            val skjaeringstidspunkt = 6.juni

            val id =
                mockForespoerselDto().copy(
                    bestemmendeFravaersdager = emptyMap(),
                )
                    .let(forespoerselDao::lagre)

            val actual =
                db.hentForespoerselRow(id)
                    .also {
                        it[ForespoerselTable.skjaeringstidspunkt] = skjaeringstidspunkt
                    }
                    .let(forespoerselDao::tilForespoerselDto)

            actual.skjaeringstidspunkt shouldBe skjaeringstidspunkt
            actual.bestemmendeFravaersdager.shouldBeEmpty()
        }
    }

    test("Ved oppdatering settes oppdatert-kolonne automatisk") {
        val id = forespoerselDao.lagre(mockForespoerselDto())

        val foerOppdatering = db.hentForespoersel(id)

        db.oppdaterStatus(id, Status.BESVART_SPLEIS)

        val etterOppdatering = db.hentForespoersel(id)

        foerOppdatering.status shouldBe Status.AKTIV
        etterOppdatering.status shouldBe Status.BESVART_SPLEIS
        foerOppdatering.oppdatert shouldNotBe etterOppdatering.oppdatert
    }
})

private fun Database.hentForespoerselRow(id: Long): ResultRow =
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
        .shouldNotBeNull()

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

private fun now(): LocalDateTime = LocalDateTime.now().truncMillis()

private fun Int.timerSiden(): LocalDateTime = LocalDateTime.now().minusHours(this.toLong()).truncMillis()
