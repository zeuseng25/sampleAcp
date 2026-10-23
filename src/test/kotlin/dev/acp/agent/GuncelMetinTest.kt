package dev.acp.agent

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.llm.AracCagrisi
import dev.acp.llm.agent.DuzenlemePlani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Araçlar editördeki **kaydedilmemiş** hâli görür (spec 010 gözden geçirme bulgusu 1).
 *
 * Satır numaraları (seçim konumu, ilişki araması) editördeki belgeden geliyor.
 * Okuma diskteki hâlden yapılırsa model "satır 12" diye başka bir kodu okur ve
 * yanlış kodu kendinden emin anlatır.
 */
class GuncelMetinTest : BasePlatformTestCase() {

    private fun calistir(ad: String, args: String): String {
        val y = IdeAracYurutucu(project, DuzenlemePlani(), iliski = null)
        val is_ = CoroutineScope(Dispatchers.Default).async { y.calistir(AracCagrisi("id", ad, args)) }
        PlatformTestUtil.waitWithEventsDispatching("Araç yanıt vermedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    override fun setUp() {
        super.setUp()
        val dosya = myFixture.addFileToProject("A.txt", "diskteki satir").virtualFile
        val belge = FileDocumentManager.getInstance().getDocument(dosya)!!
        WriteCommandAction.runWriteCommandAction(project) { belge.setText("editordeki satir") }
        assertTrue("belge kaydedilmemiş olmalı", FileDocumentManager.getInstance().isDocumentUnsaved(belge))
    }

    fun testOkumaEditordekiHali() {
        assertEquals("editordeki satir", calistir("dosya_oku", """{"yol":"A.txt"}"""))
    }

    fun testAramaEditordekiHali() {
        assertTrue(calistir("dosya_ara", """{"desen":"editordeki"}""").startsWith("A.txt:1:"))
    }

    fun testDuzenlemeEditordekiHaleGore() {
        assertEquals(
            "kaydedildi",
            calistir("dosya_duzenle", """{"yol":"A.txt","eski":"editordeki","yeni":"yeni"}"""),
        )
    }
}
