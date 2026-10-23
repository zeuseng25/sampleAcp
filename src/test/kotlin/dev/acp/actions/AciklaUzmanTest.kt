package dev.acp.actions

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.prompt.AksiyonTuru
import dev.acp.llm.prompt.PromptSelector
import dev.acp.service.ChatSessionService
import dev.acp.service.SohbetKipi
import dev.acp.settings.AcpSettings

/**
 * "Seçili kodu açıkla" Uzman'a gider (spec 010 H6, T52).
 *
 * Araçların dosyayı bulabilmesi için seçimin **proje köküne göreli yolu** ve
 * satır aralığı talimata girer; bağlam rozeti yalnızca dosya adını taşıyordu.
 */
class AciklaUzmanTest : BasePlatformTestCase() {

    private lateinit var sunucu: FakeSglangServer
    private val servis get() = ChatSessionService.getInstance(project)
    private val ayarlar get() = AcpSettings.getInstance().state
    private var eski = AcpSettings.State()

    override fun setUp() {
        super.setUp()
        eski = AcpSettings.State().also { it.copyFrom(ayarlar) }
        sunucu = FakeSglangServer(tokens = listOf("tamam")).start()
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-model"
    }

    override fun tearDown() {
        try {
            servis.clear()
            sunucu.close()
            ayarlar.copyFrom(eski)
        } finally {
            super.tearDown()
        }
    }

    fun testAciklaUzmandaGoreliYolVeSatirlarla() {
        val dosya = myFixture.addFileToProject("paket/Ornek.kt", "fun a() = 1\nfun topla(x: Int) = x + 1\nfun b() = 2\n")
        myFixture.configureFromExistingVirtualFile(dosya.virtualFile)
        val d = myFixture.editor.document
        myFixture.editor.selectionModel.setSelection(d.getLineStartOffset(1), d.getLineEndOffset(2))

        SecimEylemi.calistir(project, myFixture.editor, dosya.virtualFile, AksiyonTuru.ACIKLA)
        PlatformTestUtil.waitWithEventsDispatching("cevap gelmedi", {
            servis.state.value.let { !it.streaming && it.messages.size == 2 }
        }, 30)

        val govde = ObjectMapper().readTree(sunucu.lastRequestBody)
        val mesajlar = govde.path("messages")
        assertEquals(PromptSelector.yonerge("uzman"), mesajlar[0].path("content").asText())
        val talimat = mesajlar.last().path("content").asText()
        assertTrue(talimat, "paket/Ornek.kt" in talimat)
        assertTrue(talimat, "satır 2–3" in talimat)
        assertEquals(SohbetKipi.UZMAN, servis.state.value.messages[0].kip)
        // Kullanıcı panelde kısa eylem adını görür (003 kuralı korunuyor).
        assertEquals(SecimEylemi.gorunenAd(AksiyonTuru.ACIKLA), servis.state.value.messages[0].text)
    }
}
