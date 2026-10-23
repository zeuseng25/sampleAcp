package dev.acp.ui.chat

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import dev.acp.service.AjanUi
import dev.acp.service.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Komut onayı panelde görünür ve dar panelde kırpılmaz (spec 009 T51).
 *
 * AGENTS.md "Dar panelde bağlantı yerleşimi": bağlantılar kendi satırında.
 */
class KomutOnaySeridiTest : BasePlatformTestCase() {

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

    private fun durum(bekleyen: String?) = ChatUiState(
        ajan = AjanUi(acik = true, calisiyor = true, gorev = "derlenmiyor", bekleyenKomut = bekleyen),
    )

    fun testBekleyenKomutVeIkiBaglantiDarPaneldeGorunur() {
        val g = SwingChatView(project)
        try {
            g.render(durum("./mvnw -q compile"))
            val kok = g.component
            kok.setSize(280, 700)
            repeat(2) { yerlesim(kok) }

            // JEditorPane.text HTML kaynağıdır; görünen metin belgededir.
            val gorunenMetinler = topla(kok, JTextComponent::class.java).filter(::gorunur)
                .map { it.document.getText(0, it.document.length) }
            assertTrue("komut metni görünmüyor: $gorunenMetinler", gorunenMetinler.any { "./mvnw -q compile" in it })
            for (ad in listOf("Çalıştır", "Atla")) {
                val b = topla(kok, ActionLink::class.java).singleOrNull { it.text == ad && gorunur(it) }
                assertNotNull("$ad görünmüyor", b)
                val r = SwingUtilities.convertRectangle(b!!.parent, b.bounds, kok as JComponent)
                assertTrue("$ad kırpıldı: $r", r.x >= 0 && r.x + r.width <= 280)
            }
        } finally {
            g.dispose()
        }
    }

    fun testKomutBilgisiGorunur() {
        val g = SwingChatView(project)
        try {
            g.render(ChatUiState(ajan = AjanUi(acik = true, gorev = "x", komutBilgisi = "[ERROR] Foo.java:[3,1] cannot find symbol")))
            val metinler = topla(g.component, JTextComponent::class.java).filter(::gorunur)
                .map { it.document.getText(0, it.document.length) }
            assertTrue(metinler.toString(), metinler.any { "cannot find symbol" in it })
        } finally {
            g.dispose()
        }
    }

    fun testBekleyenYoksaBaglantilarGizli() {
        val g = SwingChatView(project)
        try {
            g.render(durum(null))
            assertTrue(topla(g.component, ActionLink::class.java).none { (it.text == "Çalıştır" || it.text == "Atla") && gorunur(it) })
        } finally {
            g.dispose()
        }
    }

    fun testBaglantilarEylemYayar() {
        val g = SwingChatView(project)
        val gelenler = mutableListOf<ChatViewAction>()
        val dinleyici = CoroutineScope(Dispatchers.Default)
        try {
            dinleyici.launch { g.actions.collect { synchronized(gelenler) { gelenler += it } } }
            Thread.sleep(100)
            g.render(durum("mvn"))
            val linkler = topla(g.component, ActionLink::class.java)
            linkler.single { it.text == "Çalıştır" }.doClick()
            linkler.single { it.text == "Atla" }.doClick()
            PlatformTestUtil.waitWithEventsDispatching("eylem gelmedi", { synchronized(gelenler) { gelenler.size >= 2 } }, 5)
            assertEquals(listOf(ChatViewAction.AjanKomutCalistir, ChatViewAction.AjanKomutAtla), synchronized(gelenler) { gelenler.toList() })
        } finally {
            dinleyici.cancel()
            g.dispose()
        }
    }
}
