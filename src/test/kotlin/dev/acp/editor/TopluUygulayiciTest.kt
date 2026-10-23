package dev.acp.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.llm.agent.Duzenleme
import dev.acp.llm.agent.DuzenlemePlani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Çok dosyalı uygulama (spec 008 T50-T55).
 *
 * İki davranış her şeyden önemli:
 * - **Ya tamamen ya hiç** — yarısı uygulanmış değişiklik derlenmeyen kod bırakır
 * - **Tek geri alma adımı** — on dosyaya dokunulsa da tek Ctrl+Z
 */
class TopluUygulayiciTest : BasePlatformTestCase() {

    private fun plan(vararg d: Duzenleme) = DuzenlemePlani().apply { d.forEach { ekle(it) } }

    private fun hesapla(p: DuzenlemePlani): TopluSonuc {
        val is_ = CoroutineScope(Dispatchers.Default).async { TopluUygulayici.hesapla(project, p) }
        PlatformTestUtil.waitWithEventsDispatching("Hesaplama bitmedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    private fun icerik(dosya: VirtualFile) = String(dosya.contentsToByteArray())

    private fun olustur(ad: String, metin: String): VirtualFile =
        myFixture.addFileToProject(ad, metin).virtualFile

    // ---- T50: hesaplama uygulamadan önce biter ----

    fun testHesaplamaDosyayaDokunmaz() {
        val a = olustur("A.txt", "eski deger")
        val sonuc = hesapla(plan(Duzenleme("A.txt", "eski", "yeni")))

        assertEquals(1, sonuc.dosyalar.size)
        assertEquals("yeni deger", sonuc.dosyalar[0].yeniIcerik)
        // Dosya HÂLÂ eski.
        assertEquals("Hesaplama diske yazdı", "eski deger", icerik(a))
    }

    fun testAyniDosyadaBirdenFazlaDuzenlemeSirayla() {
        olustur("B.txt", "bir iki uc")
        val sonuc = hesapla(plan(
            Duzenleme("B.txt", "bir", "BIR"),
            Duzenleme("B.txt", "uc", "UC"),
        ))
        assertEquals("BIR iki UC", sonuc.dosyalar.single().yeniIcerik)
        assertEquals(2, sonuc.dosyalar.single().degisiklikSayisi)
    }

    fun testCokDosyaAyriAyriHesaplanir() {
        olustur("C1.txt", "x")
        olustur("C2.txt", "y")
        val sonuc = hesapla(plan(
            Duzenleme("C1.txt", "x", "X"),
            Duzenleme("C2.txt", "y", "Y"),
        ))
        assertEquals(2, sonuc.dosyalar.size)
        assertEquals(2, sonuc.toplamDegisiklik)
    }

    // ---- T51: atlananlar BİLDİRİLİR ----

    fun testBulunamayanMetinAtlanirVeBildirilir() {
        olustur("D.txt", "gercek")
        val sonuc = hesapla(plan(Duzenleme("D.txt", "olmayan", "x")))

        assertTrue("Sessizce yutuldu", sonuc.atlananlar.isNotEmpty())
        assertTrue(sonuc.atlananlar.single().contains("D.txt"))
        assertTrue("Uygulanamayan dosya sonuca girdi", sonuc.dosyalar.isEmpty())
    }

    fun testOlmayanDosyaBildirilir() {
        val sonuc = hesapla(plan(Duzenleme("yok.txt", "a", "b")))
        assertTrue(sonuc.atlananlar.single().contains("bulunamadı"))
    }

    fun testKismenUygulanabilirDosyaUygulananlariKorur() {
        // Biri tutuyor biri tutmuyor: tutan uygulanır, tutmayan bildirilir.
        olustur("E.txt", "var olan metin")
        val sonuc = hesapla(plan(
            Duzenleme("E.txt", "var olan", "VAR OLAN"),
            Duzenleme("E.txt", "hic yok", "x"),
        ))
        assertEquals("VAR OLAN metin", sonuc.dosyalar.single().yeniIcerik)
        assertEquals(1, sonuc.atlananlar.size)
    }

    fun testProjeDisiYolAtlanir() {
        val sonuc = hesapla(plan(Duzenleme("../../etc/passwd", "a", "b")))
        assertTrue(sonuc.dosyalar.isEmpty())
        assertTrue(sonuc.atlananlar.isNotEmpty())
    }

    // ---- T53: TEK GERİ ALMA ADIMI ----

    fun testUcDosyaTekGeriAlmaAdimiyla() {
        val a = olustur("U1.txt", "aaa")
        val b = olustur("U2.txt", "bbb")
        val c = olustur("U3.txt", "ccc")
        // Geri alma bir editöre bağlı; ilkini açıyoruz.
        myFixture.openFileInEditor(a)

        val sonuc = hesapla(plan(
            Duzenleme("U1.txt", "aaa", "AAA"),
            Duzenleme("U2.txt", "bbb", "BBB"),
            Duzenleme("U3.txt", "ccc", "CCC"),
        ))
        assertNull(TopluUygulayici.uygula(project, sonuc, "ACP toplu düzenleme"))

        assertEquals("AAA", belgeMetni(a))
        assertEquals("BBB", belgeMetni(b))
        assertEquals("CCC", belgeMetni(c))

        // TEK geri alma → hepsi geri gelmeli.
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(a)
        val undo = UndoManager.getInstance(project)
        assertTrue("Geri alma mümkün değil", undo.isUndoAvailable(editor))
        WriteCommandAction.runWriteCommandAction(project) { undo.undo(editor) }

        assertEquals("Tek geri almada 1. dosya dönmedi", "aaa", belgeMetni(a))
        assertEquals("Tek geri almada 2. dosya dönmedi", "bbb", belgeMetni(b))
        assertEquals("Tek geri almada 3. dosya dönmedi", "ccc", belgeMetni(c))
    }

    private fun belgeMetni(dosya: VirtualFile): String =
        FileDocumentManager.getInstance().getDocument(dosya)!!.text

    // ---- T54/T55: ya tamamen ya hiç ----

    fun testDosyaBuArADaDegistiyseHICBIRIUygulanmaz() {
        val a = olustur("F1.txt", "aaa")
        val b = olustur("F2.txt", "bbb")
        val sonuc = hesapla(plan(
            Duzenleme("F1.txt", "aaa", "AAA"),
            Duzenleme("F2.txt", "bbb", "BBB"),
        ))

        // Hesaplama ile uygulama arasında biri dosyayı değiştirdi.
        WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(b)!!.setText("baskasi yazdi")
        }

        val hata = TopluUygulayici.uygula(project, sonuc, "ACP")

        assertNotNull("Bayatlık yakalanmadı", hata)
        assertTrue(hata, hata!!.contains("değişti"))
        // İlki de uygulanmamalı — yarım iş bırakılmıyor.
        assertEquals("Bayatlıkta ilk dosya yine de yazıldı", "aaa", belgeMetni(a))
    }

    fun testBosSonucUygulanmaz() {
        val sonuc = TopluSonuc(emptyList(), emptyList())
        assertNull(TopluUygulayici.uygula(project, sonuc, "ACP"))
        assertTrue(sonuc.bosMu)
    }

    fun testBosSonucOnizlemeAcmaz() {
        assertFalse(TopluUygulayici.onizle(project, TopluSonuc(emptyList(), emptyList())))
    }
}
