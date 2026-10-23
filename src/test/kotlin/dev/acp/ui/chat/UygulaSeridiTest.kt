package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import dev.acp.service.ChatMessageUi
import dev.acp.service.ChatRole
import dev.acp.service.ChatUiState
import dev.acp.service.MessageId
import dev.acp.service.UygulanabilirSonuc
import java.awt.Container
import javax.swing.JComponent

/**
 * 003/T40 — "Önizle" ve "Uygula" bağlantılarının **dar panelde de** görünmesi.
 *
 * Kullanıcı "Uygula"nın çıkmadığını bildirdi: şerit `FlowLayout` kullanıyordu ve
 * sohbet paneli dar olduğu için ikinci bağlantı alt satıra sarıp kırpılıyordu.
 * Araç penceresi tipik olarak 250–350 piksel geniştir; bu test o genişlikte
 * ikisinin de görünür kaldığını doğrular.
 */
class UygulaSeridiTest : BasePlatformTestCase() {

    private fun baglantilariTopla(kok: Container, sonuc: MutableList<ActionLink> = mutableListOf()): List<ActionLink> {
        for (c in kok.components) {
            if (c is ActionLink) sonuc += c
            if (c is Container) baglantilariTopla(c, sonuc)
        }
        return sonuc
    }

    private fun uygulanabilirDurum() = ChatUiState(
        messages = listOf(
            ChatMessageUi(MessageId(1), ChatRole.USER, "Yeniden düzenle"),
            ChatMessageUi(MessageId(2), ChatRole.ASSISTANT, "```kotlin\nYENI\n```"),
        ),
        uygulanabilir = UygulanabilirSonuc(MessageId(2), "YENI"),
    )

    fun testUygulanabilirSonucVarkenIkiBaglantiDaVar() {
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(uygulanabilirDurum())

            val metinler = baglantilariTopla(gorunum.component).map { it.text }
            assertTrue("Önizle bağlantısı yok: $metinler", metinler.any { it.contains("Önizle") })
            assertTrue("Uygula bağlantısı yok: $metinler", metinler.any { it.contains("Uygula") })
        } finally {
            gorunum.dispose()
        }
    }

    fun testDarPaneldeHerIkiBaglantiDaGorunurAlandaKalir() {
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(uygulanabilirDurum())

            // Tipik araç penceresi genişliği.
            val kok = gorunum.component
            kok.setSize(280, 600)
            kok.doLayout()
            yerlesimiUygula(kok)

            val baglantilar = baglantilariTopla(kok)
                .filter { it.text.contains("Önizle") || it.text.contains("Uygula") }
            assertEquals("iki bağlantı bekleniyordu", 2, baglantilar.size)

            for (baglanti in baglantilar) {
                assertTrue(
                    "${baglanti.text} sıfır genişlikte — kırpılmış",
                    baglanti.width > 0 && baglanti.height > 0,
                )
                val ust = baglanti.parent
                assertTrue(
                    "${baglanti.text} yatayda kabının dışına taşmış",
                    baglanti.x >= 0 && baglanti.x + baglanti.width <= ust.width + 1,
                )
                // Asıl hata buydu: FlowLayout bağlantıyı alt satıra sarıyor,
                // şeridin yüksekliği yetmiyor ve bağlantı kırpılıyordu.
                assertTrue(
                    "${baglanti.text} dikeyde kabının dışına taşmış — kırpılır " +
                        "(y=${baglanti.y}+${baglanti.height} > ${ust.height})",
                    baglanti.y >= 0 && baglanti.y + baglanti.height <= ust.height + 1,
                )
            }
        } finally {
            gorunum.dispose()
        }
    }

    private fun yerlesimiUygula(bilesen: Container) {
        bilesen.doLayout()
        for (c in bilesen.components) {
            if (c is Container) yerlesimiUygula(c)
        }
    }

    fun testUygulanabilirSonucYokkenSeritGizli() {
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(ChatUiState())
            val gorunurBaglantilar = baglantilariTopla(gorunum.component)
                .filter { (it.text.contains("Önizle") || it.text.contains("Uygula")) }
                .filter { bilesenGorunur(it) }
            assertTrue("sonuç yokken şerit görünüyor", gorunurBaglantilar.isEmpty())
        } finally {
            gorunum.dispose()
        }
    }

    /** Bileşen ve tüm ataları görünür mü. */
    private fun bilesenGorunur(bilesen: JComponent): Boolean {
        var s: Container? = bilesen
        while (s != null) {
            if (!s.isVisible) return false
            s = s.parent
        }
        return true
    }
}
