package dev.acp.llm.completion

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kip kararı (spec 007 T20).
 *
 * Karar tek yerde toplandı: dağıtılsaydı birinin unutulması sessizce yanlış
 * kipte istek göndermeye yol açardı.
 */
class TamamlamaKipiSeciciTest {

    /** `|` imleci gösterir. */
    private fun kip(kod: String) =
        TamamlamaKipiSecici.sec(kod.replace("|", ""), kod.indexOf("|"))

    // ---- çok satır ----

    @Test
    fun `bos satirda cok satir`() {
        assertEquals(TamamlamaKipi.COK_SATIR, kip("void f() {\n    |\n}"))
    }

    @Test
    fun `girintisiz satir basinda cok satir`() {
        assertEquals(TamamlamaKipi.COK_SATIR, kip("void f() {\n|"))
    }

    @Test
    fun `dosyanin en basinda cok satir`() {
        assertEquals(TamamlamaKipi.COK_SATIR, kip("|"))
    }

    @Test
    fun `sekmeyle girintili bos satirda cok satir`() {
        assertEquals(TamamlamaKipi.COK_SATIR, kip("void f() {\n\t\t|\n}"))
    }

    @Test
    fun `imlec sonrasinda kod olsa da sol taraf bakilir`() {
        // Sağda kapanış olması bloğa yeni girildiği gerçeğini değiştirmez.
        assertEquals(TamamlamaKipi.COK_SATIR, kip("void f() {\n    |}"))
    }

    // ---- tek satır ----

    @Test
    fun `satir ortasinda tek satir`() {
        assertEquals(TamamlamaKipi.TEK_SATIR, kip("int x = |"))
    }

    @Test
    fun `kelimenin ortasinda tek satir`() {
        assertEquals(TamamlamaKipi.TEK_SATIR, kip("    retur|"))
    }

    @Test
    fun `satir sonunda noktali virgulden sonra tek satir`() {
        assertEquals(TamamlamaKipi.TEK_SATIR, kip("    int x = 1;|"))
    }

    // ---- öneri yok ----

    @Test
    fun `uye erisiminden sonra oneri yok`() {
        // 006'da alınan karar burada da geçerli: IDE tipi biliyor, biz bilmiyoruz.
        assertEquals(TamamlamaKipi.YOK, kip("int toplam = 0; toplam.|"))
    }

    @Test
    fun `uye erisimi tek karar noktasindan geciyor`() {
        // Kural dağıtılmadığının kanıtı: kip seçici tek başına YOK döndürebiliyor.
        assertEquals(TamamlamaKipi.YOK, kip("Integer::pars|"))
        assertEquals(TamamlamaKipi.YOK, kip("ptr->|"))
    }

    @Test
    fun `ondalik sayi oneriyi engellemez`() {
        assertEquals(TamamlamaKipi.TEK_SATIR, kip("double x = 3.1|"))
    }

    // ---- sınırlar ----

    @Test
    fun `sinirlar disindaki imlec coker mi`() {
        assertEquals(TamamlamaKipi.TEK_SATIR, TamamlamaKipiSecici.sec("abc", 999))
        assertEquals(TamamlamaKipi.COK_SATIR, TamamlamaKipiSecici.sec("abc", -5))
    }

    @Test
    fun `bos dosyada cok satir`() {
        assertEquals(TamamlamaKipi.COK_SATIR, TamamlamaKipiSecici.sec("", 0))
    }
}
