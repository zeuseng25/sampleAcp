package dev.acp.completion

import com.fasterxml.jackson.databind.ObjectMapper
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
 * Çok satırlı tamamlama uçtan uca (spec 007 T60, T61).
 *
 * Doğrulanan iki şey: **kipe göre doğru token tavanının gönderildiği** ve
 * **çok satırlı yanıtın bloğun içinde kesildiği**.
 */
class CokSatirliTamamlamaTest : BasePlatformTestCase() {

    private val mapper = ObjectMapper()
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

    private fun hazirla(sunucu: FakeSglangServer, icerik: String) {
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-coder"
        ayarlar.completionFormat = FimBicimi.QWEN.name
        ayarlar.completionDebounceMs = 0
        ayarlar.completionTimeoutMs = 5_000
        myFixture.configureByText("Deneme.txt", icerik)
    }

    /** `runBlocking` KULLANILMAZ — okuma eylemi EDT isteyebilir, test kilitlenir. */
    private fun oneriAl(): InlineCompletionSuggestion {
        val saglayici = AcpInlineCompletionProvider()
        val istek = InlineCompletionIstekYardimcisi.kur(myFixture)
        val is_: Deferred<InlineCompletionSuggestion> =
            CoroutineScope(Dispatchers.Default).async { saglayici.getSuggestionDebounced(istek) }
        PlatformTestUtil.waitWithEventsDispatching("Sağlayıcı yanıt vermedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    private fun gonderilenGovde(sunucu: FakeSglangServer) = mapper.readTree(sunucu.lastRequestBody)

    // ---- T60: kipe göre token tavanı ----

    fun testBlokIcindeBlokTavaniGonderilir() {
        FakeSglangServer(completionText = "\n    int a = 1;").start().use { sunucu ->
            ayarlar.completionBlockMaxTokens = 222
            hazirla(sunucu, "void f() {\n    <caret>\n}")

            oneriAl()

            assertEquals(
                "Blok kipinde blok tavanı gönderilmeli",
                222, gonderilenGovde(sunucu).path("max_tokens").asInt(),
            )
        }
    }

    fun testSatirOrtasindaTekSatirTavaniGonderilir() {
        FakeSglangServer(completionText = "5;").start().use { sunucu ->
            ayarlar.completionMaxTokens = 33
            ayarlar.completionBlockMaxTokens = 222
            hazirla(sunucu, "void f() {\n    int x = <caret>\n}")

            oneriAl()

            // Spec 007 H4: çok satır eklemek tek satırın davranışını değiştirmemeli.
            assertEquals(
                "Satır ortasında tek satır tavanı kalmalı",
                33, gonderilenGovde(sunucu).path("max_tokens").asInt(),
            )
        }
    }

    fun testUyeErisimindenSonraIstekGitmez() {
        FakeSglangServer(completionText = "toString();").start().use { sunucu ->
            hazirla(sunucu, "void f() {\n    int t = 0; t.<caret>\n}")

            assertSame(InlineCompletionSuggestion.Empty, oneriAl())
            assertNull(
                "Üye erişiminden sonra sunucuya istek gitti: ${sunucu.lastRequestBody}",
                sunucu.lastRequestBody,
            )
        }
    }

    // ---- T61: çok satırlı yanıt uçtan uca kesiliyor ----

    fun testCokSatirliYanitBlokIcindeKesilir() {
        // Model bloktan taşıyor: kapanış } ve yeni bir fonksiyon yazmış.
        val yanit = "\n    int a = 1;\n    int b = 2;\n}\nvoid digeri() {"
        FakeSglangServer(completionText = yanit).start().use { sunucu ->
            hazirla(sunucu, "void f() {\n    <caret>\n}")

            val oneri = oneriAl()
            assertNotSame("Öneri üretilmedi", InlineCompletionSuggestion.Empty, oneri)

            val metin = oneriMetni(oneri)
            assertTrue("Çok satır gelmeli: $metin", metin.contains("\n"))
            assertTrue("İlk satır yok: $metin", metin.contains("int a = 1;"))
            assertTrue("İkinci satır yok: $metin", metin.contains("int b = 2;"))
            assertFalse("Kapanış parantezi kesilmemiş: $metin", metin.contains("}"))
            assertFalse("Sonraki fonksiyon kesilmemiş: $metin", metin.contains("digeri"))
        }
    }

    fun testBastakiYeniSatirVeGirintiAtilir() {
        // İmleç zaten 4 girintide; model kendi satırını açıyor. Atılmazsa
        // öneri 8 boşlukla girer.
        FakeSglangServer(completionText = "\n    int a = 1;").start().use { sunucu ->
            hazirla(sunucu, "void f() {\n    <caret>\n}")

            val metin = oneriMetni(oneriAl())
            assertEquals("int a = 1;", metin)
        }
    }

    fun testSatirOrtasindaHalaTekSatir() {
        FakeSglangServer(completionText = "5;\n    int y = 6;").start().use { sunucu ->
            hazirla(sunucu, "void f() {\n    int x = <caret>\n}")

            val metin = oneriMetni(oneriAl())
            assertEquals("Satır ortasında çok satır gelmemeli", "5;", metin)
        }
    }

    fun testSatirUstSinirUygulanir() {
        val yanit = "\n" + (1..30).joinToString("\n") { "    x$it();" }
        FakeSglangServer(completionText = yanit).start().use { sunucu ->
            ayarlar.completionMaxLines = 3
            hazirla(sunucu, "void f() {\n    <caret>\n}")

            val metin = oneriMetni(oneriAl())
            assertEquals("Üst sınır uygulanmamış: $metin", 3, metin.lines().size)
        }
    }

    private fun oneriMetni(oneri: InlineCompletionSuggestion): String {
        val toplayici = StringBuilder()
        val is_ = CoroutineScope(Dispatchers.Default).async {
            oneri.getVariants().firstOrNull()?.elements?.collect { toplayici.append(it.text) }
        }
        PlatformTestUtil.waitWithEventsDispatching("Öneri okunamadı", { is_.isCompleted }, 20)
        is_.getCompleted()
        return toplayici.toString()
    }
}
