package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDtoMedEksponertFsp
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentForespoerselRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer =
            mockk<PriProducer>(relaxed = true) {
            }

        HentForespoerselRiver(
            rapid = testRapid,
            forespoerselDao = mockForespoerselDao,
            priProducer = mockPriProducer,
        )
        beforeEach {
            clearAllMocks()
        }

        test("Henter forespørsel for vedtaksperiodeId") {
            val vedtaksperiodeId = UUID.randomUUID()
            println("**** vedtaksperiodeId: $vedtaksperiodeId")
            val forespoersel1 = mockForespoerselDtoMedEksponertFsp(vedtaksperiodeId)
            val forespoersel2 = mockForespoerselDtoMedEksponertFsp(vedtaksperiodeId)

            every {
                mockForespoerselDao.hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId)
            }.returns(
                listOf(
                    forespoersel1,
                    forespoersel2,
                ),
            )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID.toJson(Pri.BehovType.serializer()),
                Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
            )
            verifySequence {
                mockForespoerselDao.hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId)
                mockPriProducer.sendWithKey(
                    vedtaksperiodeId.toString(),
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel1.forespoerselId.toJson(),
                    Pri.Key.FORESPOERSEL to ForespoerselSimba(forespoersel1).toJson(ForespoerselSimba.serializer()),
                    Pri.Key.EKSPONERT_FORESPOERSEL_ID to forespoersel1.finnEksponertForespoerselId().toJson(),
                    Pri.Key.STATUS to forespoersel1.getStatus().toJson(),
                )
                mockPriProducer.sendWithKey(
                    vedtaksperiodeId.toString(),
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel2.forespoerselId.toJson(),
                    Pri.Key.FORESPOERSEL to ForespoerselSimba(forespoersel2).toJson(ForespoerselSimba.serializer()),
                    Pri.Key.EKSPONERT_FORESPOERSEL_ID to forespoersel2.finnEksponertForespoerselId().toJson(),
                    Pri.Key.STATUS to forespoersel2.getStatus().toJson(),
                )
            }
        }
    })
