package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonArray
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class HentForespoerslerForPersonRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)

        HentForespoerslerForPersonRiver(
            rapid = testRapid,
            forespoerselDao = mockForespoerselDao,
            priProducer = mockPriProducer,
        )
        beforeEach {
            clearAllMocks()
        }

        test("Henter forespørsel for fnr") {
            val fnr = Fnr.genererGyldig()
            val vedtaksperiodeId = UUID.randomUUID()
            val forespoersel1 = mockForespoerselDto().copy(vedtaksperiodeId = vedtaksperiodeId)
            val forespoersel2 = mockForespoerselDto().copy(vedtaksperiodeId = vedtaksperiodeId)

            every {
                mockForespoerselDao.hentEksponerteForespoerslerForPerson(fnr)
            }.returns(
                listOf(
                    forespoersel1,
                    forespoersel2,
                ),
            )
            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_PERSON.toJson(Pri.BehovType.serializer()),
                Pri.Key.FNR to fnr.toJson(),
            )
            verifySequence {
                mockForespoerselDao.hentEksponerteForespoerslerForPerson(fnr)
                mockPriProducer.send(
                    any(),
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_LISTE_FOR_PERSON.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_LISTE to
                        listOf(ForespoerselSimba(forespoersel1), ForespoerselSimba(forespoersel2)).toJson(ForespoerselSimba.serializer()),
                )
            }
        }

        test("Returnerer tom liste hvis ingen fsp eksisterer for et gitt fnr") {
            val fnr = Fnr.genererGyldig()

            every {
                mockForespoerselDao.hentEksponerteForespoerslerForPerson(fnr)
            }.returns(
                emptyList(),
            )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_PERSON.toJson(Pri.BehovType.serializer()),
                Pri.Key.FNR to fnr.toJson(),
            )
            verifySequence {
                mockForespoerselDao.hentEksponerteForespoerslerForPerson(fnr)
                mockPriProducer.send(
                    any(),
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_LISTE_FOR_PERSON.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_LISTE to JsonArray(emptyList()),
                )
            }
        }
    })
