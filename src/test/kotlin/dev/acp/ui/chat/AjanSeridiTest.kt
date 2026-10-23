package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import dev.acp.service.AjanOzet
import dev.acp.service.AjanUi
import dev.acp.service.ChatUiState
import java.awt.Container
import javax.swing.JComponent

/**
 * Ajan şeridinin dar panelde kırpılmadığı (spec 008, kullanıcı bulgusu 2026-09-05).
 *
 * **Bu hata ikinci kez oldu.** Spec 003'te "Uygula" bağlantısı dar panelde
 * kırpılmıştı; çözümü `BorderLayout` ile bağlantıları DOĞU'ya koymaktı
 * (dar alanda kaybolması gereken şey açıklama etiketi, düğmeler değil).
 *
 * Ajan şeridini yazarken o dersi uygulamadım, düz `FlowLayout` kullandım ve
 * "Tümünü uygula" ile "Vazgeç" ekranın dışında kaldı. `UygulaSeridiTest` bunu
 * göremedi çünkü yalnızca sohbetin kendi şeridine bakıyor.
 */
class AjanSeridiTest : BasePlatformTestCase() {

    private fun hazirDurum() = ChatUiState(
        ajan = AjanUi(
            acik = true,
            adimlar = listOf("dosya_ara: {\"desen\":\"class \"}"),
            ozet = AjanOzet(
                dosyalar = listOf("src/A.java", "src/B.java"),
                toplamDegisiklik = 2,
                atlananlar = emptyList(),
            ),
        ),
    )

    private fun baglantilariTopla(k: Container, hedef: MutableList<ActionLink> = mutableListOf()):
        MutableList<ActionLink> {
        for (c in k.components) {
            if (c is ActionLink) hedef += c
            if (c is Container) baglantilariTopla(c, hedef)
        }
        return hedef
    }

    /**
     * **Gerçek** görünürlük.
     *
     * Swing'de `isVisible` bileşenin kendi bayrağıdır: kabı gizliyken bile true
     * döner. Yalnızca ona bakan bir test, gizli bir şeridin bağlantılarını
     * "görünüyor" sanar.
     */
    private fun gercektenGorunur(c: java.awt.Component): Boolean {
        var g: java.awt.Component? = c
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

    fun testDarPaneldeUcBaglantiDaGorunurAlandaKalir() {
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(hazirDurum())

            // Tipik araç penceresi genişliği — ekran görüntüsündeki durum.
            val kok = gorunum.component
            kok.setSize(280, 600)
            kok.doLayout()
            yerlesimiUygula(kok)

            // Etiketler birebir: sohbetin kendi "Uygula" bağlantısıyla karışmasın.
            val ajanEtiketleri = setOf("Göster", "Hepsini uygula", "Vazgeç")
            val ajanBaglantilari = baglantilariTopla(kok)
                .filter { it.text in ajanEtiketleri && gercektenGorunur(it) }
            assertEquals(
                "Üç bağlantı da görünür olmalı, bulunan: ${ajanBaglantilari.map { it.text }}",
                3, ajanBaglantilari.size,
            )

            for (b in ajanBaglantilari) {
                assertTrue("${b.text} sıfır boyutta — kırpılmış", b.width > 0 && b.height > 0)

                val ust = b.parent as JComponent
                val sag = b.x + b.width
                val alt = b.y + b.height
                // YATAY sınır: ekran görüntüsünde bağlantılar sağdan taşmıştı.
                assertTrue(
                    "${b.text} yatayda kabının dışına taştı — kırpılır " +
                        "(x=${b.x}+${b.width} > ${ust.width})",
                    sag <= ust.width,
                )
                // DİKEY sınır: FlowLayout satır kaydırınca alttan taşıyor.
                assertTrue(
                    "${b.text} dikeyde kabının dışına taştı — kırpılır " +
                        "(y=${b.y}+${b.height} > ${ust.height})",
                    alt <= ust.height,
                )
            }
        } finally {
            gorunum.dispose()
        }
    }

    /** Kullanıcı bulgusu 2026-09-05: sorulan şey panelde görünmüyordu. */
    fun testGorevPaneldeGorunur() {
        val gorunum = SwingChatView(project)
        try {
            val gorev = "IdeAracYurutucu sinifini bul ve topla metodu ekle"
            gorunum.render(ChatUiState(ajan = AjanUi(calisiyor = true, gorev = gorev)))

            gorunum.component.setSize(280, 600)
            yerlesimiUygula(gorunum.component)
            assertNotNull(
                "Görev panelde yok",
                ozetBileseni(gorunum.component, "topla metodu"),
            )
        } finally {
            gorunum.dispose()
        }
    }

    /**
     * Kullanıcı bulgusu 2026-09-05: uzun özet tek satırda kırpılıyordu
     * (`...şu an tamame`).
     *
     * **Ölçülen şey bileşenin TÜRÜ, yüksekliği değil.**
     *
     * Önce yükseklikle ölçmeye çalıştım — dar panelde metin birden fazla satıra
     * sarılıyor mu diye. Yöntem geçersiz çıktı: başsız test ortamında gerçek
     * boyama döngüsü olmadığı için bu bileşenler her hâlükârda tek satır
     * bildiriyor. Kanıt: kullanıcının **doğruladığı** sohbet gövdesi de aynı
     * testte 16 px veriyor.
     *
     * Geriye kalan gerçek gerileme riski, birinin bu alanı tek satırlık bir
     * `JBLabel`'a çevirmesi. Test onu yakalıyor.
     */
    fun testOzetSarmalananBilesende() {
        val gorunum = SwingChatView(project)
        try {
            val uzun = "Aramalara gore IdeAracYurutucu sinifi su an tamamen bos; " +
                "icinde ne alan ne de metot var, bu yuzden degistirilecek bir yer bulunamadi."
            gorunum.render(
                ChatUiState(ajan = AjanUi(calisiyor = false, gorev = "g", mesaj = uzun)),
            )
            gorunum.component.setSize(280, 600)
            yerlesimiUygula(gorunum.component)

            val bilesen = ozetBileseni(gorunum.component, "IdeAracYurutucu")
            assertNotNull("Özet bileşeni bulunamadı", bilesen)
            assertTrue(
                "Özet tek satırlık bir bileşende — uzun metin kırpılır. " +
                    "Sınıf: ${bilesen!!.javaClass.simpleName}",
                bilesen is javax.swing.JEditorPane || bilesen is javax.swing.JTextArea,
            )
        } finally {
            gorunum.dispose()
        }
    }

    private fun ozetBileseni(k: Container, metin: String): javax.swing.JComponent? {
        for (c in k.components) {
            val icerik = when (c) {
                is javax.swing.JEditorPane -> c.text
                is javax.swing.JTextArea -> c.text
                is javax.swing.JLabel -> c.text.orEmpty()
                else -> null
            }
            if (icerik != null && icerik.contains(metin) && c.isVisible) {
                return c as javax.swing.JComponent
            }
            if (c is Container) ozetBileseni(c, metin)?.let { return it }
        }
        return null
    }

    fun testSonucYokkenBaglantilarGizli() {
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(ChatUiState(ajan = AjanUi(calisiyor = true)))
            yerlesimiUygula(gorunum.component)

            val ajanEtiketleri = setOf("Göster", "Hepsini uygula", "Vazgeç")
            val gorunen = baglantilariTopla(gorunum.component)
                .filter { it.text in ajanEtiketleri && gercektenGorunur(it) }
            assertTrue("Sonuç yokken bağlantı görünüyor: ${gorunen.map { it.text }}", gorunen.isEmpty())
        } finally {
            gorunum.dispose()
        }
    }
}
