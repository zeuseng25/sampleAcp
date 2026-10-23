package dev.acp.llm.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Model iyi davranmayabilir. Buradaki her test, gerçek modellerde görülen bir
 * bozukluğa karşılık geliyor — özellikle **sonek tekrarı**: temizlenmezse
 * kabul edilen öneri kodu iki kez yazar.
 */
class OneriTemizleyiciTest {

    private fun temizle(ham: String, sonrasi: String = "", bicim: FimBicimi = FimBicimi.QWEN) =
        OneriTemizleyici.temizle(ham, bicim, sonrasi)

    // --- T21: temel temizlik ---

    @Test
    fun `duzgun tek satir oldugu gibi doner`() {
        assertEquals("a + b", temizle("a + b"))
    }

    @Test
    fun `bastaki girinti korunur`() {
        // Girinti anlamlı — kırpılırsa kod yanlış hizalanır.
        assertEquals("    return x", temizle("    return x"))
    }

    @Test
    fun `sondaki bosluk atilir`() {
        assertEquals("a + b", temizle("a + b   "))
    }

    @Test
    fun `ilk satirdan sonrasi atilir`() {
        // Spec 006 Belirsizlik 4: tek satır kararı.
        assertEquals("val x = 1", temizle("val x = 1\nval y = 2\nval z = 3"))
    }

    @Test
    fun `kod citi ayiklanir`() {
        assertEquals("a + b", temizle("```kotlin\na + b\n```"))
    }

    @Test
    fun `dil etiketsiz kod citi ayiklanir`() {
        assertEquals("a + b", temizle("```\na + b\n```"))
    }

    @Test
    fun `kapanmamis kod citi de ayiklanir`() {
        // max_tokens dolduğunda kapanış çiti gelmeyebilir.
        assertEquals("a + b", temizle("```kotlin\na + b"))
    }

    @Test
    fun `kacan fim belirtecleri kesilir`() {
        assertEquals("a + b", temizle("a + b<|fim_pad|>"))
    }

    @Test
    fun `kacan endoftext kesilir`() {
        assertEquals("a + b", temizle("a + b<|endoftext|>"))
    }

    @Test
    fun `deepseek belirtecleri kendi biciminde kesilir`() {
        assertEquals("a + b", temizle("a + b<|EOT|>", bicim = FimBicimi.DEEPSEEK))
    }

    @Test
    fun `metnin ortasindaki kod citi ayiklanmaz`() {
        // Yalnızca çitle *başlayan* çıktı sarmalanmış sayılır.
        assertEquals("x = \"```\"", temizle("x = \"```\""))
    }

    // --- T22: sonek tekrarı — en tehlikeli durum ---

    @Test
    fun `oneri tam sonegi tekrarliyorsa kirpilir`() {
        // İmleçten sonra `)` var; model de `)` yazmış → kabul edilirse `))` olur.
        assertEquals("a + b", temizle("a + b)", sonrasi = ")"))
    }

    @Test
    fun `kismi sonek ortusmesi kirpilir`() {
        // İmleçten sonra `);` var, model `);` ile bitirmiş.
        assertEquals("topla(a, b", temizle("topla(a, b);", sonrasi = ");"))
    }

    @Test
    fun `sonek bosluklu yazilmissa da eslesir`() {
        assertEquals("a + b", temizle("a + b )", sonrasi = "  )  "))
    }

    @Test
    fun `sonekle ilgisi olmayan oneri kirpilmaz`() {
        assertEquals("hesapla(x)", temizle("hesapla(x)", sonrasi = "// yorum"))
    }

    @Test
    fun `tek karakterlik kismi ortusme kirpilmaz`() {
        // Tek karakter tesadüf olabilir; kırpmak doğru kodu bozardı.
        assertEquals("listOf(1, 2)", temizle("listOf(1, 2)", sonrasi = ")x"))
    }

    @Test
    fun `bos sonek hicbir sey kirpmaz`() {
        assertEquals("a + b)", temizle("a + b)", sonrasi = ""))
    }

    // --- T23: gösterilmeyecek çıktılar ---

    @Test
    fun `bos yanit null doner`() {
        assertNull(temizle(""))
    }

    @Test
    fun `yalnizca bosluk null doner`() {
        assertNull(temizle("     "))
    }

    @Test
    fun `yalnizca yeni satir null doner`() {
        assertNull(temizle("\n\n"))
    }

    @Test
    fun `yalnizca belirtecten ibaret yanit null doner`() {
        assertNull(temizle("<|fim_pad|><|endoftext|>"))
    }

    @Test
    fun `bos kod citi null doner`() {
        assertNull(temizle("```kotlin\n```"))
    }

    @Test
    fun `tamamen sonek tekrari olan oneri null doner`() {
        // Model yalnızca imleç sonrasını tekrarladıysa gösterilecek bir şey yok.
        assertNull(temizle(")", sonrasi = ")"))
    }
}
