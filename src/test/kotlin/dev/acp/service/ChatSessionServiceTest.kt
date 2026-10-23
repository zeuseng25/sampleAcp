package dev.acp.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.settings.AcpSettings
import com.intellij.testFramework.PlatformTestUtil

/**
 * 002/T11–T15.
 *
 * Sahte sunucuya karşı **gerçek istemci katmanından geçerek** koşar: servis,
 * `LlmClientService` → `HttpClientFactory` → `OpenAiLlmClient` zincirini
 * kullanıyor. Yani bu testler sadece durum makinesini değil, bütün yolu sınıyor.
 */
class ChatSessionServiceTest : BasePlatformTestCase() {

    private lateinit var sunucu: FakeSglangServer
    private var eskiUrl: String? = null
    private var eskiModel: String? = null

    private val servis get() = ChatSessionService.getInstance(project)
    private val ayarlar get() = AcpSettings.getInstance().state

    override fun setUp() {
        super.setUp()
        eskiUrl = ayarlar.baseUrl
        eskiModel = ayarlar.model
    }

    override fun tearDown() {
        try {
            servis.clear()
            if (this::sunucu.isInitialized) sunucu.close()
            ayarlar.baseUrl = eskiUrl
            ayarlar.model = eskiModel
        } finally {
            super.tearDown()
        }
    }

    private fun sunucuyuKur(
        tokens: List<String> = listOf("Mer", "haba"),
        mode: FakeSglangServer.Mode = FakeSglangServer.Mode.NORMAL,
        tokenDelayMs: Long = 0,
    ) {
        sunucu = FakeSglangServer(tokens = tokens, mode = mode, tokenDelayMs = tokenDelayMs).start()
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-model"
    }

    /**
     * Akış bitene kadar bekler — **EDT olay kuyruğunu döndürerek**.
     *
     * `runBlocking` kullanılamaz: bağlam toplama editörü okumak için EDT'ye
     * geçiyor, `runBlocking` ise EDT'yi bloke ediyor ve iki taraf birbirini
     * bekliyor. Üretimde böyle bir sorun yok (servis arka planda çalışıp EDT'ye
     * normal şekilde geçer); sorun yalnızca testin EDT üzerinde koşmasından.
     */
    private fun kosulBekle(aciklama: String, kosul: () -> Boolean): ChatUiState {
        PlatformTestUtil.waitWithEventsDispatching(aciklama, kosul, 30)
        return servis.state.value
    }

    private fun akisiBekle(mesajSayisi: Int): ChatUiState =
        kosulBekle("Akış tamamlanmadı (beklenen $mesajSayisi mesaj)") {
            val d = servis.state.value
            !d.streaming && d.messages.size == mesajSayisi
        }

    // ---- T11 --------------------------------------------------------------

    fun testSoruGonderilinceYanitAkarVeTamamlanir() {
        sunucuyuKur(tokens = listOf("Mer", "haba", " dünya"))

        servis.send("selam")
        val durum = akisiBekle(2)

        assertEquals(2, durum.messages.size)
        assertEquals(ChatRole.USER, durum.messages[0].role)
        assertEquals("selam", durum.messages[0].text)
        assertEquals(ChatRole.ASSISTANT, durum.messages[1].role)
        assertEquals("Merhaba dünya", durum.messages[1].text)
        assertFalse(durum.messages[1].streaming)
        assertNull(durum.error)
    }

    fun testBosSoruYokSayilir() {
        sunucuyuKur()
        servis.send("   ")
        assertTrue(servis.state.value.messages.isEmpty())
    }

    // ---- T14 --------------------------------------------------------------

    fun testAyarlarEksikkenIstekGonderilmez() {
        // Sunucu hiç kurulmadı; baseUrl ve model boş.
        ayarlar.baseUrl = ""
        ayarlar.model = ""

        servis.send("selam")

        val durum = servis.state.value
        assertTrue("mesaj eklenmiş olmamalı", durum.messages.isEmpty())
        assertNotNull("ayar hatası bekleniyordu", durum.error)
        // Ayarlar düzelmeden tekrar denemek aynı sonucu verir.
        assertFalse(durum.error!!.yenidenDenenebilir)
    }

    // ---- T13 --------------------------------------------------------------

    fun testIkinciSoruOncekiTuruGorur() {
        sunucuyuKur(tokens = listOf("bir"))

        servis.send("ilk soru")
        akisiBekle(2)

        servis.send("ikinci soru")
        akisiBekle(4)

        val govde = sunucu.lastRequestBody!!
        assertTrue("ilk soru gönderilmemiş: $govde", govde.contains("ilk soru"))
        assertTrue("ilk yanıt gönderilmemiş: $govde", govde.contains("bir"))
        assertTrue("ikinci soru gönderilmemiş: $govde", govde.contains("ikinci soru"))
    }

    // ---- T12 --------------------------------------------------------------

    fun testIptalAkisiDururVeGelenMetinKalir() {
        sunucuyuKur(tokens = List(300) { "token " }, tokenDelayMs = 10)

        servis.send("uzun yanıt ver")

        // Akış gerçekten başlasın.
        kosulBekle("Akış hiç başlamadı") {
            servis.state.value.messages.getOrNull(1)?.text?.isNotEmpty() == true
        }
        servis.cancel()

        val durum = kosulBekle("Akış durmadı") { !servis.state.value.streaming }
        assertTrue("gelen metin silinmiş", durum.messages[1].text.isNotEmpty())
        assertNull("iptal hata olarak gösterilmemeli", durum.error)
    }

    // ---- T15 --------------------------------------------------------------

    fun testYenidenDeneSonSoruyuBastanSorar() {
        sunucuyuKur(tokens = listOf("yanıt"))

        servis.send("tek soru")
        akisiBekle(2)

        servis.retry()
        val durum = akisiBekle(2)

        // Mesaj sayısı artmaz: yarım/eski yanıt silinip yenisi kondu.
        assertEquals(2, durum.messages.size)
        assertEquals("tek soru", durum.messages[0].text)
        assertEquals("yanıt", durum.messages[1].text)
        // Sunucuya aynı soru ikinci kez gitti.
        assertTrue(sunucu.lastRequestBody!!.contains("tek soru"))
    }

    fun testYanitYokkenYenidenDeneHicbirSeyYapmaz() {
        sunucuyuKur()
        servis.retry()
        assertTrue(servis.state.value.messages.isEmpty())
    }

    // ---- hata yolu --------------------------------------------------------

    fun testSunucuHatasiKullaniciyaBildirilir() {
        sunucuyuKur(mode = FakeSglangServer.Mode.SERVER_ERROR)

        servis.send("selam")
        val durum = akisiBekle(2)

        assertNotNull("hata bildirilmemiş", durum.error)
        assertTrue("yeniden denenebilir olmalı", durum.error!!.yenidenDenenebilir)
        assertFalse(durum.streaming)
    }

    fun testYaridaKesilenAkistaMetinKorunur() {
        sunucuyuKur(
            tokens = listOf("bir", "iki", "üç", "dört"),
            mode = FakeSglangServer.Mode.TRUNCATE,
        )

        servis.send("selam")
        val durum = akisiBekle(2)

        assertTrue("gelen metin kaybolmuş", durum.messages[1].text.isNotEmpty())
        assertNotNull("hata bildirilmemiş", durum.error)
    }

    // ---- bağlam (T72–T75) --------------------------------------------------

    /**
     * REGRESYON. Bu tam olarak kullanıcının bulduğu hatadır: uzantı noktası adı
     * yanlış olduğu için bağlam hiç toplanmıyordu ve model kodu göremiyordu.
     * Üstelik "bağlam bir kolaylıktır" yakalayıcısı istisnayı yuttuğu için
     * hiçbir test kırmızıya dönmüyordu.
     */
    fun testAcikDosyaVeSecimModeleGider() {
        sunucuyuKur(tokens = listOf("tamam"))
        myFixture.configureByText(
            "Ornek.kt",
            "fun topla(a: Int, b: Int) = a + b\nfun cikar(a: Int, b: Int) = a - b",
        )
        myFixture.editor.selectionModel.setSelection(0, 33)

        servis.send("bu ne işe yarıyor?")
        val durum = akisiBekle(2)

        val govde = sunucu.lastRequestBody!!
        assertTrue("seçili kod modele gitmemiş", govde.contains("fun topla"))
        assertTrue("açık dosyanın tamamı gitmemiş", govde.contains("fun cikar"))
        assertTrue("dosya adı gitmemiş", govde.contains("Ornek.kt"))

        // Kullanıcı panelde yalnızca kendi sorusunu görür; bağlam gövdeye eklenir.
        assertEquals("bu ne işe yarıyor?", durum.messages[0].text)
        assertNotNull("gönderilen gövde bağlamla zenginleştirilmemiş", durum.messages[0].gonderilenMetin)
        assertTrue("bağlam rozeti üretilmemiş", durum.messages[0].context.isNotEmpty())
    }

    fun testDosyaAcikDegilkenSoruYineGider() {
        sunucuyuKur(tokens = listOf("tamam"))

        servis.send("genel bir soru")
        val durum = akisiBekle(2)

        assertEquals("tamam", durum.messages[1].text)
        assertNull(durum.error)
    }

    // ---- T31 ---------------------------------------------------------------

    fun testPanelKapanincaKonusmaKorunurDinleyiciDurur() {
        sunucuyuKur(tokens = listOf("yanıt"))
        servis.send("ilk soru")
        akisiBekle(2)

        val sayac = java.util.concurrent.atomic.AtomicInteger()
        val sahtePanel = com.intellij.openapi.util.Disposer.newDisposable("acp-test-panel")
        servis.observe(sahtePanel) { sayac.incrementAndGet() }

        // İlk çizim gelsin.
        kosulBekle("Dinleyici hiç çağrılmadı") { sayac.get() > 0 }
        com.intellij.openapi.util.Disposer.dispose(sahtePanel)
        val disposeAnindaki = sayac.get()

        // Panel kapandıktan sonra konuşma devam edebilmeli.
        servis.send("ikinci soru")
        val durum = akisiBekle(4)

        assertEquals(
            "dispose edilmiş panelin dinleyicisi hâlâ çağrılıyor — sızıntı",
            disposeAnindaki,
            sayac.get(),
        )
        // Konuşma serviste yaşıyor: panel kapansa da kaybolmadı (H1).
        assertEquals(4, durum.messages.size)
        assertEquals("ilk soru", durum.messages[0].text)
    }

    fun testDusunmeBolumuAyriTutulur() {
        sunucuyuKur(tokens = listOf("cevap"), mode = FakeSglangServer.Mode.REASONING)

        servis.send("selam")
        val durum = akisiBekle(2)

        assertEquals("cevap", durum.messages[1].text)
        assertTrue("düşünme bölümü boş", durum.messages[1].reasoning.isNotEmpty())
    }
}
