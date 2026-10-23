package dev.acp.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 003/T30–T31 — H2: "Yanıt beklenirken kullanıcı o kodu değiştirdiyse değişiklik
 * uygulanmaz."
 *
 * Bu testlerin koruduğu şey **veri kaybı**. Sabitleme olmadan, yanıt geldiğinde
 * o an oradaki koda yazılırdı ve kullanıcı ne kaybettiğini fark etmezdi.
 */
class TargetAnchorTest : BasePlatformTestCase() {

    private val metin = "satir1\nHEDEF\nsatir3"

    private fun kur(): TargetAnchor {
        myFixture.configureByText("Ornek.kt", metin)
        val belge = myFixture.editor.document
        val baslangic = metin.indexOf("HEDEF")
        return TargetAnchor.yakala(
            document = belge,
            file = myFixture.file.virtualFile,
            baslangic = baslangic,
            bitis = baslangic + "HEDEF".length,
        )
    }

    private fun yaz(islem: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project) { islem() }
    }

    fun testYeniYakalananCapaBayatDegil() {
        val capa = kur()
        try {
            assertFalse("yeni yakalanan çapa bayat görünüyor", capa.bayatMi())
            assertEquals("HEDEF", capa.originalText)
        } finally {
            capa.birak()
        }
    }

    fun testHedefinIcindeDegisiklikBayatlatir() {
        val capa = kur()
        try {
            yaz { capa.document.replaceString(capa.baslangic, capa.bitis, "BASKA") }
            assertTrue("hedef değişti ama bayat sayılmadı — yanlış kod ezilirdi", capa.bayatMi())
        } finally {
            capa.birak()
        }
    }

    fun testHedefinOncesineYazmakBayatlatmaz() {
        // Kullanıcı başka yerde çalışıyorsa eylemi engellemek gereksiz olurdu.
        val capa = kur()
        try {
            yaz { capa.document.insertString(0, "yeni satır\n") }
            assertFalse("öncesine yazmak bayatlık saydı", capa.bayatMi())
            // Marker kaymış olmalı: hedef metin hâlâ doğru yerde.
            assertEquals(
                "HEDEF",
                capa.document.getText(
                    com.intellij.openapi.util.TextRange(capa.baslangic, capa.bitis),
                ),
            )
        } finally {
            capa.birak()
        }
    }

    fun testHedefinSonrasinaYazmakBayatlatmaz() {
        val capa = kur()
        try {
            yaz { capa.document.insertString(capa.document.textLength, "\nson satır") }
            assertFalse("sonrasına yazmak bayatlık saydı", capa.bayatMi())
        } finally {
            capa.birak()
        }
    }

    fun testHedefSilinirseBayat() {
        val capa = kur()
        try {
            yaz { capa.document.deleteString(capa.baslangic, capa.bitis) }
            assertTrue("hedef silindi ama bayat sayılmadı", capa.bayatMi())
        } finally {
            capa.birak()
        }
    }

    fun testTumBelgeDegisirseBayat() {
        val capa = kur()
        try {
            yaz { capa.document.setText("tamamen başka içerik") }
            assertTrue("belge baştan yazıldı ama bayat sayılmadı", capa.bayatMi())
        } finally {
            capa.birak()
        }
    }

    fun testHedefeBitisikYazmakHedefiBuyutmez() {
        // Marker açgözlü değil: kenara yazılan metin hedefe dahil olmamalı.
        val capa = kur()
        try {
            yaz { capa.document.insertString(capa.bitis, "EK") }
            assertEquals("HEDEF", capa.originalText)
            assertFalse("bitişik ekleme hedefi büyüttü", capa.bayatMi())
        } finally {
            capa.birak()
        }
    }
}
