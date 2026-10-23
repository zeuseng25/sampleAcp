package dev.acp.completion

import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.completion.FimBicimi
import dev.acp.settings.AcpSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Kötü gün senaryoları (spec 006 T61).
 *
 * Kural: **yazarken hata penceresi açmak kabul edilemez.** Sunucu kapalı,
 * zaman aşımına uğramış veya saçma yanıt vermiş olabilir; kullanıcı hiçbirini
 * fark etmemeli, yazmaya devam etmeli.
 *
 * `LlmClient` bu hataları tipli fırlatıyor (bilinçli); yutma işi burada.
 */
class TamamlamaSessizHataTest : BasePlatformTestCase() {

    private val ayarlar get() = AcpSettings.getInstance().state

    override fun tearDown() {
        try {
            ayarlar.completionFormat = FimBicimi.KAPALI.name
            ayarlar.baseUrl = ""
            ayarlar.model = ""
            ayarlar.completionModel = ""
        } finally {
            super.tearDown()
        }
    }

    private fun hazirla(adres: String) {
        ayarlar.baseUrl = adres
        ayarlar.model = "test-coder"
        ayarlar.completionFormat = FimBicimi.QWEN.name
        ayarlar.completionDebounceMs = 0
        ayarlar.completionTimeoutMs = 1_500
        myFixture.configureByText("Deneme.txt", "fun topla(a: Int, b: Int): Int {\n    return <caret>\n}")
    }

    /**
     * Sağlayıcıyı çağırıp sonucu bekler.
     *
     * `runBlocking` KULLANILMAZ: sağlayıcı belge okumak için okuma eylemi
     * istiyor, o da EDT'ye ihtiyaç duyabiliyor. EDT bloke edilirse test kilitlenir
     * (bu tuzağa spec 001'de yakalandık).
     */
    private fun oneriAl(): InlineCompletionSuggestion {
        val saglayici = AcpInlineCompletionProvider()
        val istek = InlineCompletionIstekYardimcisi.kur(myFixture)

        val is_: Deferred<InlineCompletionSuggestion> =
            CoroutineScope(Dispatchers.Default).async { saglayici.getSuggestionDebounced(istek) }

        PlatformTestUtil.waitWithEventsDispatching(
            "Sağlayıcı yanıt vermedi",
            { is_.isCompleted },
            20,
        )
        return is_.getCompleted()
    }

    fun testSunucuKapaliykenOneriUretilmezVeIstisnaSizmaz() {
        val sunucu = FakeSglangServer().start()
        val adres = sunucu.baseUrl
        sunucu.close() // artık kimse dinlemiyor
        hazirla(adres)

        // Fırlatırsa test hata verir — asıl doğrulanan bu.
        assertSame(
            "Sunucu kapalıyken öneri üretilmemeli",
            InlineCompletionSuggestion.Empty, oneriAl(),
        )
    }

    fun testSunucu500DonerseOneriUretilmez() {
        FakeSglangServer(mode = FakeSglangServer.Mode.SERVER_ERROR).start().use { sunucu ->
            hazirla(sunucu.baseUrl)
            assertSame(InlineCompletionSuggestion.Empty, oneriAl())
        }
    }

    fun testSunucuSusarsaZamanAsimindaVazgecilir() {
        FakeSglangServer(mode = FakeSglangServer.Mode.SESSIZ_KAL).start().use { sunucu ->
            hazirla(sunucu.baseUrl)
            val basla = System.currentTimeMillis()

            assertSame(InlineCompletionSuggestion.Empty, oneriAl())

            val gecen = System.currentTimeMillis() - basla
            assertTrue("Zaman aşımına uyulmadı: $gecen ms", gecen < 10_000)
        }
    }

    fun testAnlamsizYanitOneriUretmez() {
        // Model yalnızca boşluk döndürdü: gösterilecek bir şey yok.
        FakeSglangServer(completionText = "   \n  ").start().use { sunucu ->
            hazirla(sunucu.baseUrl)
            assertSame(InlineCompletionSuggestion.Empty, oneriAl())
        }
    }

    fun testGecerliYanitOneriUretir() {
        // Karşıt durum: yukarıdaki testler her koşulda Empty döndüren bozuk bir
        // uygulamayla da geçerdi. Bu test onu dışlıyor.
        FakeSglangServer(completionText = "a + b").start().use { sunucu ->
            hazirla(sunucu.baseUrl)
            assertNotSame(
                "Geçerli yanıtta da öneri üretilmedi — hata yutma fazla geniş",
                InlineCompletionSuggestion.Empty, oneriAl(),
            )
        }
    }
}
