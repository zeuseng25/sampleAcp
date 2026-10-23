package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import dev.acp.service.ChatUiState
import java.awt.Component
import java.awt.Container
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

/**
 * Sohbet / Uzman / çok dosyalı seçicisi gözden kaçmamalı (spec 008 T93, spec 010 T50).
 *
 * Kullanıcı bulgusu (T90, 2026-10-22): anahtar düğme sütununun dibindeydi;
 * çok dosyalı istek yazıldı ama sohbete gitti, "çalışmadı" sanıldı.
 */
class KipSeciciTest : BasePlatformTestCase() {

    private fun <T : Component> topla(k: Container, tur: Class<T>, hedef: MutableList<T> = mutableListOf()): List<T> {
        for (c in k.components) {
            if (tur.isInstance(c)) hedef += tur.cast(c)
            if (c is Container) topla(c, tur, hedef)
        }
        return hedef
    }

    private fun gercektenGorunur(c: Component): Boolean {
        var g: Component? = c
        while (g != null) {
            if (!g.isVisible) return false
            g = g.parent
        }
        return true
    }

    private fun yerlesimiUygula(c: Container) {
        c.doLayout()
        for (alt in c.components) if (alt is Container) yerlesimiUygula(alt)
    }

    fun testDarPaneldeIkiKipDeGorunurVeGirdininUstunde() {
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(ChatUiState())
            val kok = gorunum.component
            kok.setSize(280, 600)
            yerlesimiUygula(kok)

            val seciciler = topla(kok, JRadioButton::class.java)
            assertEquals(listOf("Sohbet", "Uzman", "Çok dosyalı"), seciciler.map { it.text })
            assertTrue("Varsayılan sohbet değil", seciciler[0].isSelected)

            val girdi = topla(kok, JBTextArea::class.java).single { it.isEditable }
            for (s in seciciler) {
                assertTrue("${s.text} görünmüyor", gercektenGorunur(s))
                val r = SwingUtilities.convertRectangle(s.parent, s.bounds, kok)
                assertTrue("${s.text} kırpıldı: $r", r.x >= 0 && r.x + r.width <= 280)
                val g = SwingUtilities.convertRectangle(girdi.parent, girdi.bounds, kok)
                assertTrue("${s.text} girdinin üstünde değil", r.y + r.height <= g.y)
            }
        } finally {
            gorunum.dispose()
        }
    }

    fun testIpucuMetniKipeGoreDegisir() {
        val gorunum = SwingChatView(project)
        try {
            val kok = gorunum.component
            val girdi = topla(kok, JBTextArea::class.java).single { it.isEditable }
            val (sohbet, uzman, cokDosyali) = topla(kok, JRadioButton::class.java)

            val ipuclari = listOf(sohbet, uzman, cokDosyali).map { it.doClick(); girdi.emptyText.text }
            assertEquals("Her kipin kendi ipucu olmalı: $ipuclari", 3, ipuclari.toSet().size)
            sohbet.doClick()
            assertEquals(ipuclari[0], girdi.emptyText.text)
        } finally {
            gorunum.dispose()
        }
    }
}
