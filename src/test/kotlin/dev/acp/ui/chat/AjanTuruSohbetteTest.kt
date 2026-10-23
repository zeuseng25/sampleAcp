package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import dev.acp.service.AjanUi
import dev.acp.service.ChatMessageUi
import dev.acp.service.ChatRole
import dev.acp.service.ChatUiState
import dev.acp.service.MessageId
import java.awt.Component
import java.awt.Container
import javax.swing.text.JTextComponent

/**
 * Ajan turu sohbetin içinde (spec 009 T90 kullanıcı bulgusu, 2026-10-23).
 *
 * Ajanın cevabı alttaki dar şeritte ayrı bir kaydırmayla görünüyordu. Görev ve
 * cevap sohbet akışına iki mesaj olarak çizilir; şeritte yalnızca durum, onay
 * ve uygulama bağlantıları kalır. Sohbet servisinin geçmişine bir şey yazılmaz
 * (008 T73 yalıtımı) — çizim görünümde.
 */
class AjanTuruSohbetteTest : BasePlatformTestCase() {

    private fun metinler(k: Container, h: MutableList<String> = mutableListOf()): List<String> {
        for (c in k.components) {
            if (c is JTextComponent && gorunur(c)) h += c.document.getText(0, c.document.length)
            if (c is Container) metinler(c, h)
        }
        return h
    }

    private fun gorunur(c: Component): Boolean {
        var g: Component? = c
        while (g != null) { if (!g.isVisible) return false; g = g.parent }
        return true
    }

    private fun m(id: Long, rol: ChatRole, t: String) = ChatMessageUi(MessageId(id), rol, t)

    fun testAjanTuruBasladigiKonumdaSohbetteGorunur() {
        val g = SwingChatView(project)
        try {
            g.render(ChatUiState(
                messages = listOf(
                    m(1, ChatRole.USER, "ilk soru"), m(2, ChatRole.ASSISTANT, "ilk cevap"),
                    m(3, ChatRole.USER, "sonraki soru"), m(4, ChatRole.ASSISTANT, "sonraki cevap"),
                ),
                ajan = AjanUi(
                    acik = true, gorev = "derlenmiyor duzelt", sohbetSirasi = 2,
                    adimlar = listOf("dogrula: başarısız (çıkış 1)"),
                    mesaj = "LdapConfigRepository duzeltildi", komutBilgisi = "[ERROR] A.java:[3,1] cannot find symbol",
                ),
            ))
            val sohbet = (g.component as Container).let { k ->
                fun ilk(c: Container): JBScrollPane? = c.components.firstNotNullOfOrNull { (it as? JBScrollPane) ?: (it as? Container)?.let(::ilk) }
                ilk(k)!!.viewport.view as Container
            }
            val sira = metinler(sohbet)
            fun yer(t: String) = sira.indexOfFirst { t in it }.also { assertTrue("'$t' sohbette yok: $sira", it >= 0) }

            assertTrue(yer("ilk cevap") < yer("derlenmiyor duzelt"))
            assertTrue(yer("derlenmiyor duzelt") < yer("LdapConfigRepository duzeltildi"))
            assertTrue(yer("LdapConfigRepository duzeltildi") < yer("sonraki soru"))
            assertTrue("komut çıktısı cevapta değil", sira.any { "cannot find symbol" in it })
        } finally {
            g.dispose()
        }
    }

    fun testSeritteUzunMetinYokYalnizcaDurum() {
        val g = SwingChatView(project)
        try {
            val uzun = "bu ajanin uzun son mesaji ".repeat(20)
            g.render(ChatUiState(ajan = AjanUi(acik = true, gorev = "g", mesaj = uzun, komutBilgisi = "CIKTI_SATIRI")))
            val sohbet = (g.component as Container).let { k ->
                fun ilk(c: Container): JBScrollPane? = c.components.firstNotNullOfOrNull { (it as? JBScrollPane) ?: (it as? Container)?.let(::ilk) }
                ilk(k)!!
            }
            val sohbetDisi = metinler(g.component).filterNot { t -> metinler(sohbet).contains(t) }
            assertTrue("uzun mesaj şeritte: $sohbetDisi", sohbetDisi.none { "uzun son mesaji" in it })
            assertTrue("komut çıktısı şeritte: $sohbetDisi", sohbetDisi.none { "CIKTI_SATIRI" in it })
        } finally {
            g.dispose()
        }
    }
}
