package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerForVedtaksperiodeIderSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.vedtaksperiodeListeSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson

class TilgjengeliggjoerForespoerslerForVedtaksperiodeIderRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    TilgjengeliggjoerForespoerslerForVedtaksperiodeIderRiver(testRapid, mockForespoerselDao, mockPriProducer)

    beforeEach {
        clearAllMocks()
        testRapid.reset()
    }

    test("Ved innkommende event, svar ut korrekt HentForespoerslerForVedtaksperiodeIderSvar") {
        val forespoersel = mockForespoerselDto()

        every {
            mockForespoerselDao.hentForespoerselEksponertTilSimba(
                forespoersel.vedtaksperiodeId,
            )
        } returns forespoersel

        val expectedPublished =
            HentForespoerslerForVedtaksperiodeIderSvar(
                resultat =
                    listOf(
                        ForespoerselSimba(forespoersel),
                    ),
                boomerang = mockJsonElement(),
            )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
            Pri.Key.VEDTAKSPERIODE_ID_LISTE to listOf(forespoersel.vedtaksperiodeId).toJson(vedtaksperiodeListeSerializer),
            Pri.Key.BOOMERANG to expectedPublished.boomerang,
        )

        verifySequence {
            mockForespoerselDao.hentForespoerselEksponertTilSimba(forespoersel.vedtaksperiodeId)
            mockPriProducer.send(
                Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIderSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(HentForespoerslerForVedtaksperiodeIderSvar.serializer()),
            )
        }
    }
    test("Hvis ingen forespørsler finnes, svar med tom liste") {
        val forespoersel = mockForespoerselDto()

        every {
            mockForespoerselDao.hentForespoerselEksponertTilSimba(
                forespoersel.vedtaksperiodeId,
            )
        } returns null

        val expectedPublished =
            HentForespoerslerForVedtaksperiodeIderSvar(
                resultat = emptyList(),
                boomerang = mockJsonElement(),
            )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
            Pri.Key.VEDTAKSPERIODE_ID_LISTE to listOf(forespoersel.vedtaksperiodeId).toJson(vedtaksperiodeListeSerializer),
            Pri.Key.BOOMERANG to expectedPublished.boomerang,
        )

        verifySequence {
            mockForespoerselDao.hentForespoerselEksponertTilSimba(forespoersel.vedtaksperiodeId)
            mockPriProducer.send(
                Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIderSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(HentForespoerslerForVedtaksperiodeIderSvar.serializer()),
            )
        }
    }
})
