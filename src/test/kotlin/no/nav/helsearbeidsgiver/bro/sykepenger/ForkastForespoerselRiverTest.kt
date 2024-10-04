package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid.vedtaksperiodeId
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForkastForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)
    val orgnummer = Orgnr("123456789")

    ForkastForespoerselRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao,
        priProducer = mockPriProducer,
    )

    fun mockForkastForespoerselMelding(
        orgnummer: Orgnr,
        vedtaksperiodeId: UUID,
    ) {
        testRapid.sendJson(
            Spleis.Key.TYPE to Spleis.Event.TRENGER_IKKE_OPPLYSNINGER_FRA_ARBEIDSGIVER.toJson(Spleis.Event.serializer()),
            Spleis.Key.ORGANISASJONSNUMMER to orgnummer.toJson(Orgnr.serializer()),
            Spleis.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
        )
    }

    beforeEach {
        clearAllMocks()
    }

    test("Innkommende event oppdaterer aktive forespørsler som forkastet") {
        mockForkastForespoerselMelding(orgnummer, vedtaksperiodeId)

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)
            mockForespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId)
        }
    }

    test("Sier ifra til Simba om at forespørsel er forkastet") {
        val forespoersel = mockForespoerselDto()

        every {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)
        } returns forespoersel

        mockForkastForespoerselMelding(orgnummer, vedtaksperiodeId)

        verifySequence {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
            )
        }
    }

    test("Sier _ikke_ ifra til Simba om at forespørsel er forkastet dersom vi ikke finner en aktiv forespørsel") {

        every {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)
        } returns null

        mockForkastForespoerselMelding(orgnummer, vedtaksperiodeId)

        verify(exactly = 0) {
            mockPriProducer.send(*anyVararg())
        }
    }
})
