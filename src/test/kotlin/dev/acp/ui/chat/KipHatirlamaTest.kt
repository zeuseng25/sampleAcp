package dev.acp.ui.chat

import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import dev.acp.settings.AcpProjeAyarlari
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Container
import javax.swing.JRadioButton

/**
 * Son kip hatırlanır (spec 010 T53; 009 T90 kullanıcı bulgusu).
 *
 * Görünüm ayarlara dokunmaz (ChatView disiplini): kip değişince eylem yayar,
 * kaydetme bağlantı katmanının işi; açılışta kip görünüme verilir.
 */
class KipHatirlamaTest : BasePlatformTestCase() {

    private fun <T : Component> topla(k: Container, tur: Class<T>, h: MutableList<T> = mutableListOf()): List<T> {
        for (c in k.components) {
            if (tur.isInstance(c)) h += tur.cast(c)
            if (c is Container) topla(c, tur, h)
        }
        return h
    }

    fun testVarsayilanSohbetVeKalici() {
        assertEquals(PanelKipi.SOHBET.name, AcpProjeAyarlari.State().sonKip)
        val xml = JDOMUtil.write(serialize(AcpProjeAyarlari.State().apply { sonKip = PanelKipi.COK_DOSYALI.name })!!)
        assertTrue(xml, "COK_DOSYALI" in xml)
    }

    fun testTaninmayanDegerSohbeteDuser() {
        assertEquals(PanelKipi.SOHBET, PanelKipi.coz("BOYLE_BIR_KIP_YOK"))
        assertEquals(PanelKipi.UZMAN, PanelKipi.coz("UZMAN"))
    }

    fun testVerilenKipSeciliVeIpucuUyumlu() {
        val g = SwingChatView(project)
        try {
            g.kipiAyarla(PanelKipi.COK_DOSYALI)
            val seciciler = topla(g.component, JRadioButton::class.java)
            assertTrue(seciciler.single { it.text == "Çok dosyalı" }.isSelected)
            val girdi = topla(g.component, JBTextArea::class.java).single { it.isEditable }
            seciciler.single { it.text == "Sohbet" }.doClick()
            val sohbetIpucu = girdi.emptyText.text
            g.kipiAyarla(PanelKipi.COK_DOSYALI)
            assertFalse("ipucu kipe uymuyor", girdi.emptyText.text == sohbetIpucu)
        } finally {
            g.dispose()
        }
    }

    fun testKullaniciKipDegistirinceEylemYayilir() {
        val g = SwingChatView(project)
        val gelen = mutableListOf<ChatViewAction>()
        val d = CoroutineScope(Dispatchers.Default)
        try {
            d.launch { g.actions.collect { synchronized(gelen) { gelen += it } } }
            Thread.sleep(100)
            topla(g.component, JRadioButton::class.java).single { it.text == "Uzman" }.doClick()
            PlatformTestUtil.waitWithEventsDispatching("eylem yok", { synchronized(gelen) { gelen.isNotEmpty() } }, 5)
            assertEquals(ChatViewAction.KipDegisti(PanelKipi.UZMAN), synchronized(gelen) { gelen.first() })
        } finally {
            d.cancel()
            g.dispose()
        }
    }
}
