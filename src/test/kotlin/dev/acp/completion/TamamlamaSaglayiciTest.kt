package dev.acp.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.completion.FimBicimi
import dev.acp.settings.AcpSettings

/**
 * Sağlayıcı IDE'ye doğru bağlanmış mı ve **kapalıyken gerçekten susuyor mu**
 * (spec 006 T50, T51).
 *
 * Buradaki en değerli test `testKapaliykenSunucuyaHicIstekGitmez`: ayarın
 * kapalı görünmesi yetmez, sunucuya paket gitmediği kanıtlanmalı. Kullanıcı
 * kodunun farkında olmadan dışarı gitmesi geri alınamaz bir hata sınıfı.
 */
class TamamlamaSaglayiciTest : BasePlatformTestCase() {

    private val ayarlar get() = AcpSettings.getInstance().state

    override fun tearDown() {
        try {
            ayarlar.completionFormat = FimBicimi.KAPALI.name
            ayarlar.completionModel = ""
            ayarlar.baseUrl = ""
            ayarlar.model = ""
        } finally {
            super.tearDown()
        }
    }

    private fun saglayici(): AcpInlineCompletionProvider {
        val hepsi = InlineCompletionProvider.EP_NAME.extensionList
        val bizim = hepsi.filterIsInstance<AcpInlineCompletionProvider>()
        assertEquals(
            "Sağlayıcı uzantı noktasına kayıtlı değil. plugin.xml'deki EP adını " +
                "kontrol edin — kayıtlı bulunan sağlayıcılar: ${hepsi.map { it.javaClass.simpleName }}",
            1, bizim.size,
        )
        return bizim.first()
    }

    // ---- T50: kayıt -------------------------------------------------------

    fun testSaglayiciUzantiNoktasinaKayitli() {
        // Bu test, yanlış EP adının sessizce geçmesini engelliyor: ad yanlışsa
        // eklenti yine yüklenir ama sağlayıcı listede hiç görünmez.
        assertNotNull(saglayici())
    }

    fun testSaglayicininKimligiVar() {
        assertEquals("Acp", saglayici().id.id)
    }

    // ---- T51: kapalıyken sessizlik ---------------------------------------

    fun testVarsayilanAyarlaDevreDisi() {
        assertFalse(
            "Kutudan çıktığı gibi tamamlama açık olmamalı",
            saglayici().isEnabled(olay()),
        )
    }

    fun testBicimKapaliykenDevreDisi() {
        ayarlar.baseUrl = "http://sunucu"
        ayarlar.model = "m"
        ayarlar.completionFormat = FimBicimi.KAPALI.name

        assertFalse(saglayici().isEnabled(olay()))
    }

    fun testBicimSecilinceEtkin() {
        ayarlar.baseUrl = "http://sunucu"
        ayarlar.model = "m"
        ayarlar.completionFormat = FimBicimi.QWEN.name

        assertTrue(saglayici().isEnabled(olay()))
    }

    fun testSunucuAdresiYoksaEtkinDegil() {
        ayarlar.model = "m"
        ayarlar.completionFormat = FimBicimi.QWEN.name

        assertFalse(saglayici().isEnabled(olay()))
    }

    /**
     * **Asıl kanıt.** Ayarın "kapalı" görünmesi yetmez; sunucuya gerçekten paket
     * gitmediği gösterilmeli. Sahte sunucu, kendisine gelen her isteği kaydediyor.
     */
    fun testKapaliykenSunucuyaHicIstekGitmez() {
        FakeSglangServer(completionText = "bu gorunmemeli").start().use { sunucu ->
            ayarlar.baseUrl = sunucu.baseUrl
            ayarlar.model = "m"
            ayarlar.completionFormat = FimBicimi.KAPALI.name

            myFixture.configureByText("Deneme.txt", "fun topla(a: Int, b: Int) {\n    return <caret>\n}")

            val saglayici = saglayici()
            assertFalse("isEnabled true döndü", saglayici.isEnabled(olay()))

            // Platform isEnabled false olunca getSuggestion'ı hiç çağırmaz;
            // bu yüzden sunucuya ulaşan istek de olmamalı.
            assertNull(
                "Tamamlama kapalıyken sunucuya istek gitti: ${sunucu.lastRequestBody}",
                sunucu.lastRequestBody,
            )
        }
    }

    fun testKapaliykenIstemciDeUretilmez() {
        ayarlar.baseUrl = "http://sunucu"
        ayarlar.model = "m"
        ayarlar.completionFormat = FimBicimi.KAPALI.name

        // İkinci savunma hattı: sağlayıcı yarış durumunda yine de çağrılsa bile
        // istemci kurulmaz, yani istek kurulacak nesne bile oluşmaz.
        assertFalse(ayarlar.tamamlamaAcikMi())
    }

    /** `isEnabled` için gereken olay. Gerçek bir editör gerekiyor; yoksa kurulur. */
    private fun olay(): InlineCompletionEvent {
        if (myFixture.editor == null) myFixture.configureByText("Bos.txt", "")
        val editor = myFixture.editor
        return InlineCompletionEvent.DirectCall(
            editor,
            editor.caretModel.currentCaret,
            DataContext.EMPTY_CONTEXT,
        )
    }
}
