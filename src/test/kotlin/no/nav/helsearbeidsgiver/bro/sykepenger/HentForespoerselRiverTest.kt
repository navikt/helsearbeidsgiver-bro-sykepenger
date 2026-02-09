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
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
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

            val eksponertId1 = UUID.randomUUID()
            val forespoersel1 = mockForespoerselDto().copy(vedtaksperiodeId = vedtaksperiodeId)
            val eksponertId2 = UUID.randomUUID()
            val forespoersel2 = mockForespoerselDto().copy(vedtaksperiodeId = vedtaksperiodeId)

            every {
                mockForespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(vedtaksperiodeId))
            }.returns(
                listOf(
                    eksponertId1 to forespoersel1,
                    eksponertId2 to forespoersel2,
                ),
            )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID.toJson(Pri.BehovType.serializer()),
                Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
            )
            verifySequence {
                mockForespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(vedtaksperiodeId))
                mockPriProducer.send(
                    vedtaksperiodeId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel1.forespoerselId.toJson(),
                    Pri.Key.FORESPOERSEL to ForespoerselSimba(forespoersel1).toJson(ForespoerselSimba.serializer()),
                    Pri.Key.EKSPONERT_FORESPOERSEL_ID to eksponertId1.toJson(),
                    Pri.Key.STATUS to
                        forespoersel1.status
                            .tilForenkletStatus()
                            .toJson(),
                )
                mockPriProducer.send(
                    vedtaksperiodeId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel2.forespoerselId.toJson(),
                    Pri.Key.FORESPOERSEL to ForespoerselSimba(forespoersel2).toJson(ForespoerselSimba.serializer()),
                    Pri.Key.EKSPONERT_FORESPOERSEL_ID to eksponertId2.toJson(),
                    Pri.Key.STATUS to
                        forespoersel2.status
                            .tilForenkletStatus()
                            .toJson(),
                )
            }
        }
    })
