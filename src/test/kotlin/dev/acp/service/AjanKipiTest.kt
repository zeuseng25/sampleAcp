package dev.acp.service

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.editor.TopluSonuc
import dev.acp.editor.TopluUygulayici
import dev.acp.llm.agent.Duzenleme
import dev.acp.llm.agent.DuzenlemePlani
import dev.acp.settings.AcpSettings
import dev.acp.settings.AjanKipi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Kip, döngü bittikten sonra ne olacağını belirler (spec 008 H4).
 *
 * T61 yalnızca `DOGRUDAN`'ın **aynı uygulama yolunu** kullandığını doğruluyordu;
 * o yolu kimsenin otomatik çağırmadığını yakalamadı — runIde'de iki kip aynı
 * davranıyordu (T90).
 */
class AjanKipiTest : BasePlatformTestCase() {

    private val ayarlar get() = AcpSettings.getInstance().state

    override fun tearDown() {
        try {
            ayarlar.agentUygulamaKipi = AjanKipi.ONIZLE.name
            AjanService.getInstance(project).temizle()
        } finally {
            super.tearDown()
        }
    }

    private fun <T> bekle(blok: suspend CoroutineScope.() -> T): T {
        val is_ = CoroutineScope(Dispatchers.Default).async(block = blok)
        PlatformTestUtil.waitWithEventsDispatching("Bitmedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    private fun hazirla(): Pair<VirtualFile, TopluSonuc> {
        val dosya = myFixture.addFileToProject("K.txt", "eski deger").virtualFile
        val plan = DuzenlemePlani().apply { ekle(Duzenleme("K.txt", "eski", "yeni")) }
        return dosya to bekle { TopluUygulayici.hesapla(project, plan) }
    }

    private fun metin(dosya: VirtualFile) = FileDocumentManager.getInstance().getDocument(dosya)!!.text

    fun testOnizleKipindeDosyaDegismez() {
        val (dosya, sonuc) = hazirla()
        val ajan = AjanService.getInstance(project)

        bekle { ajan.sonucuIsle(sonuc) }

        assertEquals("eski deger", metin(dosya))
        assertNotNull("Önizlenecek sonuç kayboldu", ajan.durum.value.sonuc)
    }

    fun testDogrudanKipindeOnaysizUygulanir() {
        ayarlar.agentUygulamaKipi = AjanKipi.DOGRUDAN.name
        val (dosya, sonuc) = hazirla()
        val ajan = AjanService.getInstance(project)

        bekle { ajan.sonucuIsle(sonuc) }

        assertEquals("Doğrudan kipte uygulanmadı", "yeni deger", metin(dosya))
        assertNull(ajan.durum.value.sonuc)
        assertEquals("Uygulandı.", ajan.durum.value.mesaj)
    }

    fun testUygulamaHatasiKullaniciyaGosterilir() {
        val (dosya, sonuc) = hazirla()
        val ajan = AjanService.getInstance(project)
        bekle { ajan.sonucuIsle(sonuc) }

        // Hesaplamadan sonra dosya değişti → bayat; hata yutulmamalı.
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(dosya)!!.setText("baska")
        }
        ajan.uygula()

        assertNotNull("Uygulama hatası sessizce yutuldu", ajan.durum.value.hata)
    }
}
