package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonArray
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerForVedtaksperiodeIdListeSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class TilgjengeliggjoerForespoerslerForVedtaksperiodeIdListeRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>()
        val mockPriProducer = mockk<PriProducer>(relaxed = true)

        TilgjengeliggjoerForespoerslerForVedtaksperiodeIdListeRiver(testRapid, mockForespoerselDao, mockPriProducer)

        beforeEach {
            clearAllMocks()
            testRapid.reset()
        }

        test("Ved innkommende event, svar ut korrekt HentForespoerslerForVedtaksperiodeIdListeSvar") {
            val vedtaksperiodeIder = setOf(Mock.vedtaksperiodeId1, Mock.vedtaksperiodeId2)

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(vedtaksperiodeIder)
            } returns Mock.forespoersler

            val expectedPublished =
                HentForespoerslerForVedtaksperiodeIdListeSvar(
                    resultat =
                        Mock.forespoersler.map { ForespoerselSimba(it) },
                    boomerang = mockJsonElement(),
                )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIder.toList().toJson(UuidSerializer),
                Pri.Key.BOOMERANG to expectedPublished.boomerang,
            )

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(vedtaksperiodeIder)
                mockPriProducer.send(
                    vedtaksperiodeIder.min(),
                    Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIdListeSvar.behovType.toJson(Pri.BehovType.serializer()),
                    Pri.Key.LØSNING to expectedPublished.toJson(HentForespoerslerForVedtaksperiodeIdListeSvar.serializer()),
                )
            }
        }
        test("Hvis ingen forespørsler finnes, svar med tom liste") {
            val vedtaksperiodeIder = setOf(Mock.vedtaksperiodeId1, Mock.vedtaksperiodeId2)

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(vedtaksperiodeIder)
            } returns emptyList()

            val expectedPublished =
                HentForespoerslerForVedtaksperiodeIdListeSvar(
                    resultat = emptyList(),
                    boomerang = mockJsonElement(),
                )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIder.toList().toJson(UuidSerializer),
                Pri.Key.BOOMERANG to expectedPublished.boomerang,
            )

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(vedtaksperiodeIder)
                mockPriProducer.send(
                    vedtaksperiodeIder.min(),
                    Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIdListeSvar.behovType.toJson(Pri.BehovType.serializer()),
                    Pri.Key.LØSNING to expectedPublished.toJson(HentForespoerslerForVedtaksperiodeIdListeSvar.serializer()),
                )
            }
        }

        test("Ikke spør databasen ved tom liste for vedtaksperiode-ID-er") {
            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.VEDTAKSPERIODE_ID_LISTE to JsonArray(emptyList()),
                Pri.Key.BOOMERANG to mockJsonElement(),
            )

            verify(exactly = 0) {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(any())
            }
            verifySequence {
                mockPriProducer.send(any<UUID>(), *anyVararg())
            }
        }
    }) {
    private object Mock {
        val vedtaksperiodeId1: UUID = UUID.randomUUID()
        val vedtaksperiodeId2: UUID = UUID.randomUUID()
        val forespoerselId1: UUID = UUID.randomUUID()
        val forespoerselId2: UUID = UUID.randomUUID()

        val forespoersler =
            listOf(
                mockForespoerselDto().copy(forespoerselId = forespoerselId1, vedtaksperiodeId = vedtaksperiodeId1),
                mockForespoerselDto().copy(forespoerselId = forespoerselId2, vedtaksperiodeId = vedtaksperiodeId2),
            )
    }
}
