package dev.acp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.prompt.PromptSelector
import dev.acp.settings.AcpSettings

/**
 * Sohbet ve Uzman kipinde kodu araştırma (spec 010 T40-T45).
 *
 * Sahte sunucuya karşı gerçek istemci katmanından geçer (ChatSessionServiceTest
 * gibi). Araç çağıran bir model ve araçları reddeden bir sunucu taklit ediliyor.
 */
class SohbetArastirmaTest : BasePlatformTestCase() {

    private lateinit var sunucu: FakeSglangServer
    private val mapper = ObjectMapper()
    private val servis get() = ChatSessionService.getInstance(project)
    private val ayarlar get() = AcpSettings.getInstance().state
    private var eski = AcpSettings.State()

    override fun setUp() {
        super.setUp()
        eski = AcpSettings.State().also { it.copyFrom(ayarlar) }
    }

    override fun tearDown() {
        try {
            servis.clear()
            if (this::sunucu.isInitialized) sunucu.close()
            ayarlar.copyFrom(eski)
        } finally {
            super.tearDown()
        }
    }

    private fun kur(
        mode: FakeSglangServer.Mode = FakeSglangServer.Mode.NORMAL,
        gecikme: Long = 0,
        tokens: List<String> = listOf("Mer", "haba"),
    ) {
        sunucu = FakeSglangServer(tokens = tokens, mode = mode, tokenDelayMs = gecikme).start()
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-model"
    }

    private fun bekle(): ChatUiState {
        PlatformTestUtil.waitWithEventsDispatching(
            "Akış bitmedi",
            { servis.state.value.let { !it.streaming && it.messages.size >= 2 } },
            30,
        )
        return servis.state.value
    }

    private fun govde(i: Int) = mapper.readTree(sunucu.chatRequestBodies[i])
    private fun aracAdlari(i: Int) = govde(i).path("tools").map { it.path("function").path("name").asText() }

    // ---- T40 ----

    fun testVarsayilanlar() {
        val d = AcpSettings.State()
        assertTrue(d.sohbetArastirma)
        assertEquals(6, d.sohbetMaxAdim)
        assertEquals(15, d.uzmanMaxAdim)
    }

    // ---- T41: araştırma kapalı → 002'deki gövde ----

    fun testArastirmaKapaliykenGovdeEskisiGibi() {
        kur()
        ayarlar.sohbetArastirma = false
        servis.send("selam")
        val d = bekle()

        assertEquals("Merhaba", d.messages.last().text)
        val g = govde(0)
        assertTrue("tools gönderildi", g.path("tools").isMissingNode)
        assertEquals("sistem mesajı eklendi", "user", g.path("messages")[0].path("role").asText())
        assertEquals(1, sunucu.chatRequestBodies.size)
    }

    // ---- T42 ----

    fun testArastirmaAcikkenSaltOkunurAraclarVeSohbetYonergesi() {
        kur()
        servis.send("selam")
        bekle()

        val araclar = aracAdlari(0)
        assertTrue(araclar.toString(), "kullanimlari_bul" in araclar)
        assertFalse("sohbete düzenleme aracı verildi", "dosya_duzenle" in araclar)
        val sistem = govde(0).path("messages")[0]
        assertEquals("system", sistem.path("role").asText())
        assertEquals(PromptSelector.yonerge("sohbet-arastirma"), sistem.path("content").asText())
    }

    fun testUzmanKipiUzmanYonergesiyle() {
        kur()
        servis.send("bunu açıkla", SohbetKipi.UZMAN)
        bekle()
        assertEquals(PromptSelector.yonerge("uzman"), govde(0).path("messages")[0].path("content").asText())
        assertFalse("dosya_duzenle" in aracAdlari(0))
    }

    fun testUzmanKipiArastirmaKapaliykenDeArastirir() {
        // Ayar yalnızca Sohbet içindir; Uzman'ın işi araştırmak (spec 010).
        kur()
        ayarlar.sohbetArastirma = false
        servis.send("bunu açıkla", SohbetKipi.UZMAN)
        bekle()
        assertTrue(aracAdlari(0).isNotEmpty())
    }

    fun testAracTuruAdimOlarakGorunurAraMetinCevabaKarismaz() {
        kur(FakeSglangServer.Mode.ARAC_CAGIR)
        servis.send("hedef nerede?")
        val cevap = bekle().messages.last()

        assertEquals("Merhaba", cevap.text)
        assertTrue(cevap.reasoning, "Önce arayayım." in cevap.reasoning)
        assertEquals(1, cevap.adimlar.size)
        assertTrue(cevap.adimlar.single(), cevap.adimlar.single().startsWith("dosya_ara"))
        // Araç sonucu ikinci isteğe gitti.
        assertTrue("\"role\":\"tool\"" in sunucu.chatRequestBodies[1])
    }

    fun testOncekiTurlarGecmisOlarakGider() {
        kur()
        servis.send("ilk soru")
        bekle()
        servis.send("ikinci soru")
        PlatformTestUtil.waitWithEventsDispatching("ikinci tur", { servis.state.value.messages.size == 4 && !servis.state.value.streaming }, 30)

        val mesajlar = govde(1).path("messages").map { it.path("role").asText() to it.path("content").asText() }
        assertEquals("system", mesajlar[0].first)
        assertEquals("assistant" to "Merhaba", mesajlar[2])
        assertEquals("user", mesajlar.last().first)
        assertTrue(mesajlar.last().second.contains("ikinci soru"))
    }

    fun testOncekiSorularinBaglamiTekrarGitmez() {
        // Gözden geçirme bulgusu 7: her önceki soru açık dosyayla (8000 token'a kadar)
        // tekrar gidiyor, birkaç takip sorusunda bağlam sınırı aşılıyordu.
        myFixture.configureByText("Acik.kt", "fun acikDosyaIcerigi() = 42")
        kur()
        servis.send("ilk soru")
        bekle()
        assertTrue("bağlam eklenmemiş, test anlamsız", "acikDosyaIcerigi" in sunucu.chatRequestBodies[0])

        servis.send("ikinci soru")
        PlatformTestUtil.waitWithEventsDispatching("ikinci tur", { servis.state.value.messages.size == 4 && !servis.state.value.streaming }, 30)

        val mesajlar = govde(1).path("messages")
        assertEquals("ilk soru", mesajlar[1].path("content").asText())
        // Güncel soru kendi bağlamıyla gider.
        assertTrue("acikDosyaIcerigi" in mesajlar.last().path("content").asText())
    }

    fun testYeniIstekEskisininKapanisiylaBozulmaz() {
        // Gözden geçirme bulgusu 8: iptal edilen eski işin finally'si paylaşılan
        // durumu "bitti" yapıyordu — yeni istek sürerken durdur düğmesi kayboluyordu.
        kur(gecikme = 50, tokens = List(40) { "t$it " })
        servis.send("a")
        PlatformTestUtil.waitWithEventsDispatching("ilk akış başlamadı", { servis.state.value.messages.lastOrNull()?.text?.isNotEmpty() == true }, 30)

        servis.send("b")
        // Eski işin kapanışı bu sürede biter; yeni istek hâlâ akıyor olmalı.
        val bitis = System.currentTimeMillis() + 400
        PlatformTestUtil.waitWithEventsDispatching("bekleme", { System.currentTimeMillis() > bitis }, 5)
        assertTrue("yeni istek sürerken panel 'bitti' oldu", servis.state.value.streaming)

        PlatformTestUtil.waitWithEventsDispatching("ikinci bitmedi", { !servis.state.value.streaming }, 30)
        val m = servis.state.value.messages
        assertFalse("eski cevap akıyor kaldı", m[1].streaming)
        assertTrue(m.last().text, m.last().text.startsWith("t0 "))
    }

    // ---- T44 ----

    fun testSunucuAraclariReddederseAracsizCevaplanir() {
        kur(FakeSglangServer.Mode.ARACLARI_REDDET)
        servis.send("selam")
        val d = bekle()

        assertNull("hata gösterildi", d.error)
        assertEquals("Merhaba", d.messages.last().text)
        assertTrue(d.messages.last().adimlar.toString(), d.messages.last().adimlar.any { "desteklemiyor" in it })
        assertTrue(govde(sunucu.chatRequestBodies.size - 1).path("tools").isMissingNode)

        // Oturumun geri kalanında araç denenmez, bildirim tekrarlanmaz.
        servis.send("ikinci")
        PlatformTestUtil.waitWithEventsDispatching("ikinci", { servis.state.value.messages.size == 4 && !servis.state.value.streaming }, 30)
        assertTrue(govde(sunucu.chatRequestBodies.size - 1).path("tools").isMissingNode)
        assertTrue(servis.state.value.messages.last().adimlar.isEmpty())
    }

    fun testAracDesteginiGosterenBaskaBir400ArastirmayiKapatmaz() {
        // Gözden geçirme bulgusu 5: araç ZATEN çalıştıysa sunucu araçları destekliyor;
        // 400 başka bir şey (bozuk argüman vb.). Hata gösterilmeli, oturum araçsıza düşmemeli.
        kur(FakeSglangServer.Mode.ARAC_SONRA_400)
        servis.send("x")
        val d = bekle()
        assertNotNull("hata gizlendi", d.error)
        assertFalse(d.messages.last().adimlar.any { "desteklemiyor" in it })

        // clear() bayrağı sıfırlar — kullanılmaz; aynı oturumda ikinci soru.
        val once = sunucu.chatRequestBodies.size
        servis.send("y")
        PlatformTestUtil.waitWithEventsDispatching("ikinci", { sunucu.chatRequestBodies.size > once && !servis.state.value.streaming }, 30)
        assertFalse("araştırma kapatılmış", govde(once).path("tools").isMissingNode)
    }

    // ---- T45 ----

    fun testAdimSiniriCevaptaSoylenir() {
        kur(FakeSglangServer.Mode.ARAC_HEP_CAGIR)
        ayarlar.sohbetMaxAdim = 2
        servis.send("x")
        val cevap = bekle().messages.last()
        // 2 araçlı tur + araçsız son tur: bulgular atılmaz, kısmi cevap verilir.
        assertEquals(3, sunucu.chatRequestBodies.size)
        assertTrue(cevap.text, cevap.text.startsWith("Merhaba"))
        assertTrue(cevap.text, "adım sınırı" in cevap.text)
    }

    fun testDurdurulursaCevaptaSoylenir() {
        // Çok token: birleştirme ilk parçayı geciktirir; akış bitmeden durdurulmalı.
        kur(gecikme = 100, tokens = List(40) { "t$it " })
        servis.send("x")
        PlatformTestUtil.waitWithEventsDispatching("akış başlamadı", { servis.state.value.messages.lastOrNull()?.text?.isNotEmpty() == true }, 30)
        servis.cancel()
        PlatformTestUtil.waitWithEventsDispatching("durmadı", { !servis.state.value.streaming }, 30)
        assertTrue(servis.state.value.messages.last().text, servis.state.value.messages.last().text.endsWith("— durduruldu"))
    }
}
