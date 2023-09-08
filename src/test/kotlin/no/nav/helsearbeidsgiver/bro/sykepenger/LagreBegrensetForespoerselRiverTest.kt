package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockBegrensetForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.toKeyMap
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson

class LagreBegrensetForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    LagreBegrensetForespoerselRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao,
        priProducer = mockPriProducer
    )

    fun mockInnkommendeMelding(forespoersel: ForespoerselDto) {
        testRapid.sendJson(
            Spleis.Key.TYPE to Spleis.Event.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_BEGRENSET.toJson(Spleis.Event.serializer()),
            Spleis.Key.ORGANISASJONSNUMMER to forespoersel.orgnr.toJson(Orgnr.serializer()),
            Spleis.Key.FØDSELSNUMMER to forespoersel.fnr.toJson(),
            Spleis.Key.VEDTAKSPERIODE_ID to forespoersel.vedtaksperiodeId.toJson(),
            Spleis.Key.SYKMELDINGSPERIODER to forespoersel.sykmeldingsperioder.toJson(Periode.serializer().list()),
            Spleis.Key.FORESPURT_DATA to forespoersel.forespurtData.toJson(SpleisForespurtDataDto.serializer().list())
        )
    }

    beforeEach {
        clearAllMocks()
    }

    test("Innkommende forespørsel blir lagret og sender notifikasjon videre") {
        val forespoersel = mockForespoerselDto().copy(
            type = Type.BEGRENSET,
            skjaeringstidspunkt = null,
            egenmeldingsperioder = emptyList(),
            forespurtData = mockBegrensetForespurtDataListe()
        )

        mockkObject(Env) {
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(forespoersel.vedtaksperiodeId) } returns null

            mockkStatic(::randomUuid) {
                every { randomUuid() } returns forespoersel.forespoerselId

                mockInnkommendeMelding(forespoersel)
            }
        }

        val expectedPublished = ForespoerselMottatt(
            forespoerselId = forespoersel.forespoerselId,
            orgnr = forespoersel.orgnr,
            fnr = forespoersel.fnr
        )
        verifySequence {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(forespoersel.vedtaksperiodeId)
            mockForespoerselDao.lagre(
                withArg {
                    it.shouldBeEqualToIgnoringFields(forespoersel, forespoersel::oppdatert, forespoersel::opprettet)
                }
            )

            mockPriProducer.send(
                *expectedPublished.toKeyMap().toList().toTypedArray()
            )
        }
    }
})
