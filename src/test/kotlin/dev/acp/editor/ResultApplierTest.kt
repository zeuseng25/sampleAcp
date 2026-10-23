package dev.acp.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** 003/T40–T45. */
class ResultApplierTest : BasePlatformTestCase() {

    private val metin = "fun a() = 1\nHEDEF\nfun b() = 2"

    private fun capaKur(): TargetAnchor {
        myFixture.configureByText("Ornek.kt", metin)
        val baslangic = metin.indexOf("HEDEF")
        return TargetAnchor.yakala(
            document = myFixture.editor.document,
            file = myFixture.file.virtualFile,
            baslangic = baslangic,
            bitis = baslangic + "HEDEF".length,
        )
    }

    /** `TextEditorProvider` derleme yolunda yok; açık editörü yöneticiden alıyoruz. */
    private fun acikEditor() =
        FileEditorManager.getInstance(project).getSelectedEditor(myFixture.file.virtualFile)

    // ---- T45: kod bloğu ayıklama -------------------------------------------

    fun testIlkKodBlogunuAyiklar() {
        val yanit = "İşte düzeltilmiş hâli:\n```kotlin\nfun a() = 42\n```\nUmarım yardımcı olur."
        assertEquals("fun a() = 42", ResultApplier.kodBlogunuAyikla(yanit))
    }

    fun testKodBlogunuYokkenNullDoner() {
        // Uygulama düğmesi hiç gösterilmemeli; açıklama metnini koda yazmak felaket olurdu.
        assertNull(ResultApplier.kodBlogunuAyikla("Bu kod zaten iyi görünüyor, değişiklik önermiyorum."))
    }

    fun testBosKodBlogunuYokSayar() {
        assertNull(ResultApplier.kodBlogunuAyikla("```kotlin\n\n```"))
    }

    fun testAciklamaOncedeOlsaBileKodBulunur() {
        val yanit = "Önce açıklama.\n\nSonra kod:\n```\nx = 1\n```"
        assertEquals("x = 1", ResultApplier.kodBlogunuAyikla(yanit))
    }

    // ---- T41/T42/T43: uygulama ve geri alma --------------------------------

    fun testUygulamaDegisikligiYazar() {
        val capa = capaKur()
        try {
            val sonuc = ResultApplier.uygula(project, capa, "YENI", "ACP: dene")
            assertEquals(ResultApplier.Sonuc.Uygulandi, sonuc)
            assertTrue(
                "değişiklik yazılmamış: ${capa.document.text}",
                capa.document.text.contains("YENI"),
            )
            assertFalse(capa.document.text.contains("HEDEF"))
        } finally {
            capa.birak()
        }
    }

    fun testTekGeriAlmaAdimiyleGeriAlinir() {
        val capa = capaKur()
        try {
            ResultApplier.uygula(project, capa, "YENI", "ACP: dene")
            assertTrue(capa.document.text.contains("YENI"))

            val fileEditor = acikEditor()
            val undo = UndoManager.getInstance(project)
            assertTrue("geri alma mümkün değil", undo.isUndoAvailable(fileEditor))

            undo.undo(fileEditor)

            // TEK adım yeterli olmalı: yazma ve biçimlendirme aynı komutta.
            assertEquals("tek geri alma metni eski hâline döndürmedi", metin, capa.document.text)
        } finally {
            capa.birak()
        }
    }

    fun testGeriAlmaListesindeOkunabilirAdVar() {
        val capa = capaKur()
        try {
            ResultApplier.uygula(project, capa, "YENI", "ACP: kodu yeniden düzenle")

            val aciklama = UndoManager.getInstance(project)
                .getUndoActionNameAndDescription(acikEditor())
            assertTrue(
                "geri alma adı eylemi anlatmıyor: $aciklama",
                aciklama.first.contains("ACP") || aciklama.second.contains("ACP"),
            )
        } finally {
            capa.birak()
        }
    }

    // ---- T31: bayatlık koruması --------------------------------------------

    fun testBayatKodUygulanmaz() {
        val capa = capaKur()
        try {
            // Kullanıcı yanıt beklerken hedefi değiştirdi.
            WriteCommandAction.runWriteCommandAction(project) {
                capa.document.replaceString(capa.baslangic, capa.bitis, "KULLANICI_YAZDI")
            }

            val sonuc = ResultApplier.uygula(project, capa, "MODEL_YAZDI", "ACP: dene")

            assertEquals(ResultApplier.Sonuc.Bayat, sonuc)
            assertTrue(
                "kullanıcının yazdığı ezilmiş — veri kaybı",
                capa.document.text.contains("KULLANICI_YAZDI"),
            )
            assertFalse(capa.document.text.contains("MODEL_YAZDI"))
        } finally {
            capa.birak()
        }
    }

    // ---- T44: salt okunur ---------------------------------------------------

    fun testSaltOkunurDosyaReddedilir() {
        val capa = capaKur()
        try {
            (capa.document as DocumentEx).setReadOnly(true)
            try {
                val sonuc = ResultApplier.uygula(project, capa, "YENI", "ACP: dene")
                assertEquals(ResultApplier.Sonuc.SaltOkunur, sonuc)
                assertTrue(capa.document.text.contains("HEDEF"))
            } finally {
                (capa.document as DocumentEx).setReadOnly(false)
            }
        } finally {
            capa.birak()
        }
    }
}
