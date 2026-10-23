package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import dev.acp.service.ChatMessageUi
import dev.acp.service.ChatRole
import dev.acp.service.ChatUiState
import dev.acp.service.MessageId
import java.awt.Component
import java.awt.Container

/**
 * Araştırma adımları cevabın üstünde görünür (spec 010 H3, T51).
 *
 * Kullanıcı cevabın neye dayandığını görmeli; adımlar görünmezse "kodu
 * araştırdı" ile "uydurdu" ayırt edilemez.
 */
class MesajAdimlariTest : BasePlatformTestCase() {

    private fun etiketler(k: Container, hedef: MutableList<JBLabel> = mutableListOf()): List<JBLabel> {
        for (c in k.components) {
            if (c is JBLabel) hedef += c
            if (c is Container) etiketler(c, hedef)
        }
        return hedef
    }

    private fun gorunur(c: Component): Boolean {
        var g: Component? = c
        while (g != null) { if (!g.isVisible) return false; g = g.parent }
        return true
    }

    private fun durum(adimlar: List<String>) = ChatUiState(
        messages = listOf(
            ChatMessageUi(MessageId(1), ChatRole.USER, "soru"),
            ChatMessageUi(MessageId(2), ChatRole.ASSISTANT, "cevap", adimlar = adimlar),
        ),
    )

    fun testAdimlarOzetlenirAyrintiIpucunda() {
        val g = SwingChatView(project)
        try {
            g.render(durum(listOf("""kullanimlari_bul: {"yol":"A.java","ad":"calis"}""", """dosya_oku: {"yol":"B.java"}""")))
            val satir = etiketler(g.component).single { gorunur(it) && "kullanimlari_bul" in it.text }

            assertTrue(satir.text, "2 adım" in satir.text && "dosya_oku" in satir.text)
            assertFalse("argümanlar özete taşmış: ${satir.text}", "A.java" in satir.text)
            assertTrue("ayrıntı ipucunda değil", satir.toolTipText.orEmpty().contains("A.java"))
        } finally {
            g.dispose()
        }
    }

    fun testAdimYoksaSatirGorunmez() {
        val g = SwingChatView(project)
        try {
            g.render(durum(emptyList()))
            assertTrue(etiketler(g.component).none { gorunur(it) && "adım" in it.text })
        } finally {
            g.dispose()
        }
    }
}
