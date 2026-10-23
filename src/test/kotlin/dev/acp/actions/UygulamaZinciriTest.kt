package dev.acp.actions

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.editor.TargetAnchor
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.prompt.AksiyonTuru
import dev.acp.service.ChatSessionService
import dev.acp.settings.AcpSettings

/**
 * 003/T50–T53 — eylemden uygulamaya uçtan uca.
 *
 * Sahte sunucu kod bloğu içeren bir yanıt döndürüyor; zincirin tamamı
 * (talimat → yanıt → kod bloğu ayıklama → uygulanabilir sonuç → uygulama)
 * gerçek bileşenlerle koşuyor.
 */
class UygulamaZinciriTest : BasePlatformTestCase() {

    private lateinit var sunucu: FakeSglangServer
    private var eskiUrl: String? = null
    private var eskiModel: String? = null

    private val servis get() = ChatSessionService.getInstance(project)
    private val ayarlar get() = AcpSettings.getInstance().state

    private val kaynak = "fun a() = 1\nHEDEF\nfun b() = 2"

    /** Model yanıtı: açıklama + tek kod bloğu. */
    private val yanitParcalari = listOf(
        "İşte düzeltilmiş hâli:\n",
        "```kotlin\n",
        "YENI_KOD\n",
        "```\n",
        "Umarım yardımcı olur.",
    )

    override fun setUp() {
        super.setUp()
        eskiUrl = ayarlar.baseUrl
        eskiModel = ayarlar.model
        sunucu = FakeSglangServer(tokens = yanitParcalari).start()
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-model"
    }

    /** Eylemin açtığı geçici dosyalar; testin temizlemesi gerekir. */
    private fun scratchDosyalari() =
        FileEditorManager.getInstance(project).openFiles.filter { it.name.startsWith("acp-test") }

    override fun tearDown() {
        try {
            // Açık bırakılan editör platformun sızıntı denetimini düşürür.
            val yonetici = FileEditorManager.getInstance(project)
            scratchDosyalari().forEach { yonetici.closeFile(it) }
            servis.clear()
            sunucu.close()
            ayarlar.baseUrl = eskiUrl
            ayarlar.model = eskiModel
        } finally {
            super.tearDown()
        }
    }

    private fun capaKur(): TargetAnchor {
        myFixture.configureByText("Ornek.kt", kaynak)
        val baslangic = kaynak.indexOf("HEDEF")
        return TargetAnchor.yakala(
            document = myFixture.editor.document,
            file = myFixture.file.virtualFile,
            baslangic = baslangic,
            bitis = baslangic + "HEDEF".length,
        )
    }

    private fun bekle(aciklama: String, kosul: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching(aciklama, kosul, 30)

    private fun akisiBekle() = bekle("Akış tamamlanmadı") {
        val d = servis.state.value
        !d.streaming && d.messages.size == 2
    }

    // ---- T50: yeniden düzenle ----------------------------------------------

    fun testYenidenDuzenleUygulanabilirSonucUretir() {
        val capa = capaKur()
        servis.sendAction(AksiyonTuru.YENIDEN_DUZENLE, "Yeniden düzenle", "kt", capa)
        akisiBekle()

        val uygulanabilir = servis.state.value.uygulanabilir
        assertNotNull("uygulanabilir sonuç üretilmemiş", uygulanabilir)
        assertEquals("YENI_KOD", uygulanabilir!!.kod)
        // Dosya henüz DEĞİŞMEMELİ: kullanıcı onaylamadı.
        assertTrue("onaysız uygulanmış", myFixture.editor.document.text.contains("HEDEF"))
    }

    fun testUygulaOnaylandigindaYazar() {
        val capa = capaKur()
        servis.sendAction(AksiyonTuru.YENIDEN_DUZENLE, "Yeniden düzenle", "kt", capa)
        akisiBekle()

        servis.uygula()

        assertTrue("uygulanmamış", myFixture.editor.document.text.contains("YENI_KOD"))
        assertFalse(myFixture.editor.document.text.contains("HEDEF"))
        assertNull("uygulandıktan sonra şerit kalmış", servis.state.value.uygulanabilir)
    }

    fun testBayatKodUygulanmazVeBildirilir() {
        val capa = capaKur()
        servis.sendAction(AksiyonTuru.YENIDEN_DUZENLE, "Yeniden düzenle", "kt", capa)
        akisiBekle()

        // Kullanıcı yanıt geldikten sonra, uygulamadan önce kodu değiştirdi.
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.replaceString(capa.baslangic, capa.bitis, "KULLANICI")
        }
        servis.uygula()

        assertTrue("kullanıcının yazdığı ezilmiş", myFixture.editor.document.text.contains("KULLANICI"))
        assertFalse(myFixture.editor.document.text.contains("YENI_KOD"))
        assertNotNull("durum bildirilmemiş", servis.state.value.error)
    }

    // ---- T62: açıkla dosyayı değiştirmez -----------------------------------

    fun testAciklaDosyayiDegistirmez() {
        capaKur().birak()
        val oncekiMetin = myFixture.editor.document.text

        servis.sendAction(AksiyonTuru.ACIKLA, "Açıkla", "kt", capa = null)
        akisiBekle()

        assertNull("açıkla uygulama önerdi", servis.state.value.uygulanabilir)
        assertEquals("açıkla dosyayı değiştirdi", oncekiMetin, myFixture.editor.document.text)
    }

    // ---- T52: test yaz mevcut dosyayı değiştirmez --------------------------

    fun testTestYazMevcutDosyayiDegistirmez() {
        capaKur().birak()
        val oncekiMetin = myFixture.editor.document.text

        servis.sendAction(AksiyonTuru.TEST_YAZ, "Test yaz", "kt", capa = null)
        akisiBekle()
        bekle("Geçici test dosyası oluşmadı") { scratchDosyalari().isNotEmpty() }

        assertEquals("test yaz mevcut dosyayı değiştirdi", oncekiMetin, myFixture.editor.document.text)
        assertNull("test yaz uygulama önerdi", servis.state.value.uygulanabilir)
        assertTrue(
            "geçici dosyada test içeriği yok",
            scratchDosyalari().first().let {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                    .getDocument(it)?.text?.contains("YENI_KOD") == true
            },
        )
    }

    // ---- T45: kod bloğu yoksa uygulama önerilmez ---------------------------

    fun testKodBlogusuzYanitUygulamaOnermez() {
        sunucu.close()
        sunucu = FakeSglangServer(tokens = listOf("Bu kod zaten iyi görünüyor.")).start()
        ayarlar.baseUrl = sunucu.baseUrl

        val capa = capaKur()
        servis.sendAction(AksiyonTuru.YENIDEN_DUZENLE, "Yeniden düzenle", "kt", capa)
        akisiBekle()

        assertNull("kod bloğu yokken uygulama önerilmiş", servis.state.value.uygulanabilir)
    }
}
