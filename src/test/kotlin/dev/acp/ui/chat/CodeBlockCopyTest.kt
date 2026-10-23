package dev.acp.ui.chat

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import java.awt.Container
import java.awt.datatransfer.DataFlavor

/**
 * 002/T42 — "Her kod bloğu tek tıkla kopyalanabilir."
 *
 * Kullanıcı bu düğmenin çalışmadığını bildirdi; elle denemek yerine tıklamayı
 * programla tetikleyip panoyu okuyoruz.
 */
class CodeBlockCopyTest : BasePlatformTestCase() {

    private fun baglantiBul(kok: Container): ActionLink? {
        for (c in kok.components) {
            if (c is ActionLink) return c
            if (c is Container) baglantiBul(c)?.let { return it }
        }
        return null
    }

    private fun panoMetni(): String? =
        CopyPasteManager.getInstance().contents
            ?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
            ?.getTransferData(DataFlavor.stringFlavor) as? String

    fun testKopyalaBaglantisiVar() {
        val bilesen = CodeBlockComponent(project, "kotlin", "fun a() = 42")
        try {
            assertNotNull("kod bloğunda Kopyala bağlantısı bulunamadı", baglantiBul(bilesen))
        } finally {
            bilesen.dispose()
        }
    }

    fun testKopyalaPanoyaYazar() {
        val kod = "fun topla(a: Int, b: Int) = a + b"
        CopyPasteManager.getInstance().setContents(java.awt.datatransfer.StringSelection("baslangic"))

        val bilesen = CodeBlockComponent(project, "kotlin", kod)
        try {
            // error() Nothing döner; JUnit3'ün fail()'i void döndüğü için
            // tip çıkarımını bozuyordu.
            val baglanti = baglantiBul(bilesen) ?: error("Kopyala bağlantısı yok")
            baglanti.doClick()

            assertEquals("pano güncellenmemiş", kod, panoMetni())
        } finally {
            bilesen.dispose()
        }
    }

    fun testCokSatirliKodTamKopyalanir() {
        val kod = "satir1\nsatir2\nsatir3"
        val bilesen = CodeBlockComponent(project, null, kod)
        try {
            baglantiBul(bilesen)!!.doClick()
            assertEquals(kod, panoMetni())
        } finally {
            bilesen.dispose()
        }
    }
}
