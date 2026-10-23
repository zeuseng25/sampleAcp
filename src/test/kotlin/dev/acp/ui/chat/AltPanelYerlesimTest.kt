package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import dev.acp.service.AjanUi
import dev.acp.service.ChatError
import dev.acp.service.ChatUiState
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Alt panel yerleşimi (spec 009 T90 kullanıcı bulgusu, 2026-10-23).
 *
 * Ajan bölümü kaydırma alanının dışındaydı: uzun komut çıktısı veya uzun son
 * mesaj paneli dolduruyor, kaydırılamıyordu; işin bitip bitmediği belli değildi.
 */
class AltPanelYerlesimTest : BasePlatformTestCase() {

    private fun <T : Component> topla(k: Container, tur: Class<T>, h: MutableList<T> = mutableListOf()): List<T> {
        for (c in k.components) {
            if (tur.isInstance(c)) h += tur.cast(c)
            if (c is Container) topla(c, tur, h)
        }
        return h
    }

    private fun gorunur(c: Component): Boolean {
        var g: Component? = c
        while (g != null) { if (!g.isVisible) return false; g = g.parent }
        return true
    }

    private fun yerlesim(c: Container) {
        c.doLayout()
        for (a in c.components) if (a is Container) yerlesim(a)
    }

    private fun ciz(durum: ChatUiState, blok: (JComponent) -> Unit) {
        val g = SwingChatView(project)
        try {
            g.render(durum)
            val kok = g.component
            kok.setSize(300, 700)
            repeat(3) { yerlesim(kok) }
            blok(kok)
        } finally {
            g.dispose()
        }
    }

    private val uzun = (1..120).joinToString("\n") { "[ERROR] /x/src/A$it.java:[$it,5] cannot find symbol" }

    fun testUzunAjanIcerigiSohbetAlaniniEzmez() {
        ciz(ChatUiState(ajan = AjanUi(acik = true, gorev = "derlenmiyor", komutBilgisi = uzun, mesaj = uzun))) { kok ->
            // Sohbet görüntüsü (ilk kaydırma alanı) kullanılabilir yükseklikte kalmalı.
            val sohbet = topla(kok, JBScrollPane::class.java).first()
            assertTrue("sohbet alanı ezildi: ${sohbet.height}", sohbet.height >= 200)
            // Uzun içerik kendi kaydırma alanında.
            val ic = topla(kok, JBScrollPane::class.java).drop(1).filter(::gorunur)
            assertTrue("ajan bölümü kaydırılabilir değil", ic.isNotEmpty())
        }
    }

    fun testHataSeridiGercektenYerKaplar() {
        ciz(ChatUiState(error = ChatError("Sunucuya ulaşılamıyor", yenidenDenenebilir = true))) { kok ->
            val etiket = topla(kok, JComponent::class.java).firstOrNull {
                gorunur(it) && (it as? JBLabel)?.text?.contains("Sunucuya ulaşılamıyor") == true ||
                    (it as? javax.swing.text.JTextComponent)?.let { t -> "Sunucuya ulaşılamıyor" in t.document.getText(0, t.document.length) } == true
            }
            assertNotNull("hata metni bileşeni yok", etiket)
            val r = SwingUtilities.convertRectangle(etiket!!.parent, etiket.bounds, kok)
            assertTrue("hata şeridi çizilmiyor: $r", r.height > 0 && r.y >= 0 && r.y < 700)
        }
    }

    private fun durumMetni(ajan: AjanUi): String {
        var metin = ""
        ciz(ChatUiState(ajan = ajan)) { kok ->
            metin = topla(kok, JBLabel::class.java).filter(::gorunur).joinToString(" | ") { it.text }
        }
        return metin
    }

    fun testDurumTekBakistaAnlasilir() {
        assertTrue(durumMetni(AjanUi(acik = true, calisiyor = true, gorev = "x", adimlar = listOf("a: {}", "b: {}"))).contains("Çalışıyor"))
        assertTrue(durumMetni(AjanUi(acik = true, gorev = "x", mesaj = "uzun bir analiz")).contains("değişiklik önerilmedi"))
        assertTrue(durumMetni(AjanUi(acik = true, gorev = "x", mesaj = "Durduruldu.")).contains("Durduruldu"))
    }
}
