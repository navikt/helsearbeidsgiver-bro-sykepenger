package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerForFnrOgOrgnrSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Suksess
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson

class TilgjengeliggjoerForespoerslerForPersonOgOrgnrRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    TilgjengeliggjoerForespoerslerForFnrOgOrgnrRiver(testRapid, mockForespoerselDao, mockPriProducer)

    beforeEach {
        clearAllMocks()
    }

    test("Ved innkommende event, svar ut korrekt HentForespoerslerSvar") {
        val forespoersel = mockForespoerselDto()

        every {
            mockForespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(
                forespoersel.orgnr,
                forespoersel.fnr,
            )
        } returns listOf(forespoersel)

        val expectedPublished =
            HentForespoerslerForFnrOgOrgnrSvar(
                orgnr = forespoersel.orgnr,
                fnr = forespoersel.fnr,
                resultat =
                    listOf(
                        Suksess(forespoersel),
                    ),
                boomerang = mockJsonElement(),
            )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_FNR_OG_ORGNR.toJson(Pri.BehovType.serializer()),
            Pri.Key.ORGNR to expectedPublished.orgnr.verdi.toJson(),
            Pri.Key.FNR to expectedPublished.fnr.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang,
        )

        verifySequence {
            mockForespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(forespoersel.orgnr, forespoersel.fnr)
            mockPriProducer.send(
                Pri.Key.BEHOV to HentForespoerslerForFnrOgOrgnrSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(HentForespoerslerForFnrOgOrgnrSvar.serializer()),
            )
        }
    }
    test("Hvis ingen forespørsler finnes, svar med tom liste") {
        val forespoersel = mockForespoerselDto()

        every {
            mockForespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(
                forespoersel.orgnr,
                forespoersel.fnr,
            )
        } returns emptyList()

        val expectedPublished =
            HentForespoerslerForFnrOgOrgnrSvar(
                orgnr = forespoersel.orgnr,
                fnr = forespoersel.fnr,
                resultat = emptyList(),
                boomerang = mockJsonElement(),
            )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_FNR_OG_ORGNR.toJson(Pri.BehovType.serializer()),
            Pri.Key.ORGNR to expectedPublished.orgnr.verdi.toJson(),
            Pri.Key.FNR to expectedPublished.fnr.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang,
        )

        verifySequence {
            mockForespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(forespoersel.orgnr, forespoersel.fnr)
            mockPriProducer.send(
                Pri.Key.BEHOV to HentForespoerslerForFnrOgOrgnrSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(HentForespoerslerForFnrOgOrgnrSvar.serializer()),
            )
        }
    }
})
