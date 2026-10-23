package dev.acp.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.prompt.AksiyonTuru
import dev.acp.service.ChatSessionService
import dev.acp.settings.AcpSettings

/** 003/T20–T24. */
class SelectionActionTest : BasePlatformTestCase() {

    private lateinit var sunucu: FakeSglangServer
    private var eskiUrl: String? = null
    private var eskiModel: String? = null

    private val servis get() = ChatSessionService.getInstance(project)
    private val ayarlar get() = AcpSettings.getInstance().state

    override fun setUp() {
        super.setUp()
        eskiUrl = ayarlar.baseUrl
        eskiModel = ayarlar.model
        sunucu = FakeSglangServer(tokens = listOf("yanıt")).start()
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-model"
    }

    override fun tearDown() {
        try {
            servis.clear()
            sunucu.close()
            ayarlar.baseUrl = eskiUrl
            ayarlar.model = eskiModel
        } finally {
            super.tearDown()
        }
    }

    private fun akisiBekle(mesajSayisi: Int) {
        PlatformTestUtil.waitWithEventsDispatching(
            "Akış tamamlanmadı",
            {
                val d = servis.state.value
                !d.streaming && d.messages.size == mesajSayisi
            },
            30,
        )
    }

    // ---- T20: etkinlik kuralı ----------------------------------------------

    fun testSecimYokkenEylemPasif() {
        myFixture.configureByText("Ornek.kt", "fun a() = 1")
        // Seçim yok: testAction update() çalıştırır ve pasif olduğu için
        // eylemi ÇALIŞTIRMAZ.
        val sunum = myFixture.testAction(ExplainSelectionAction())
        assertFalse("seçim yokken eylem etkin kalmış", sunum.isEnabled)
        assertTrue("pasif eylem yine de çalışmış", servis.state.value.messages.isEmpty())
    }

    fun testSecimVarkenEylemEtkinVeCalisir() {
        myFixture.configureByText("Ornek.kt", "fun topla(a: Int, b: Int) = a + b")
        myFixture.editor.selectionModel.setSelection(0, 33)

        val sunum = myFixture.testAction(ExplainSelectionAction())
        assertTrue("seçim varken eylem pasif kalmış", sunum.isEnabled)

        akisiBekle(2)
        assertEquals(2, servis.state.value.messages.size)
    }

    // ---- T21/T24: kayıt ----------------------------------------------------

    fun testDortEylemDeKayitli() {
        val yonetici = ActionManager.getInstance()
        for (id in listOf("dev.acp.Explain", "dev.acp.Test", "dev.acp.Refactor", "dev.acp.Docs")) {
            assertNotNull("eylem kayıtlı değil: $id", yonetici.getAction(id))
        }
    }

    fun testEylemGrubuKayitli() {
        // Grup Keymap'te ve editör menüsünde görünmesinin ön şartı.
        assertNotNull(
            "eylem grubu kayıtlı değil",
            ActionManager.getInstance().getAction("dev.acp.EditorPopup"),
        )
    }

    fun testEylemMetinleriBundleDanGeliyor() {
        val eylem = ActionManager.getInstance().getAction("dev.acp.Explain")
        val metin = eylem.templatePresentation.text
        assertNotNull("eylem metni yok", metin)
        assertTrue("metin bundle'dan gelmemiş: $metin", metin!!.contains("açıkla"))
    }

    // ---- T23: doğru şablon gidiyor ----------------------------------------

    fun testAciklaEylemiDogruSablonuGonderir() {
        myFixture.configureByText("Ornek.kt", "fun topla(a: Int, b: Int) = a + b")
        myFixture.editor.selectionModel.setSelection(0, 33)

        servis.sendAction(AksiyonTuru.ACIKLA, "Seçili kodu açıkla", "kt")
        akisiBekle(2)

        val govde = sunucu.lastRequestBody!!
        assertTrue("açıkla şablonu gönderilmemiş", govde.contains("yalnızca açıkla"))
        assertTrue("seçili kod gönderilmemiş", govde.contains("fun topla"))

        // Kullanıcı panelde şablonu değil kısa eylem adını görmeli.
        assertEquals("Seçili kodu açıkla", servis.state.value.messages[0].text)
    }

    fun testTestYazEylemiDileOzguSablonKullanir() {
        myFixture.configureByText("Ornek.py", "def topla(a, b):\n    return a + b")
        myFixture.editor.selectionModel.setSelection(0, 30)

        servis.sendAction(AksiyonTuru.TEST_YAZ, "Seçili koda test yaz", "py")
        akisiBekle(2)

        assertTrue(
            "python'a özgü şablon seçilmemiş",
            sunucu.lastRequestBody!!.contains("pytest"),
        )
    }
}
