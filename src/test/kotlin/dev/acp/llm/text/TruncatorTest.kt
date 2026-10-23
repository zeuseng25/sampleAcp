package dev.acp.llm.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 002/T71 — H3: "Bağlam ayarlardaki sınırı aşarsa kırpılır; kırpma satır
 * ortasından yapılmaz ve kırpıldığı yerde belirtilir."
 *
 * Ortadan kırpıyoruz, sondan değil: kodun başı (paket, import, sınıf bildirimi)
 * ve sonu (imlecin bulunduğu yer) genellikle ortasından daha değerli.
 */
class TruncatorTest {

    private fun satirlar(n: Int) = (1..n).joinToString("\n") { "satir$it" }

    @Test
    fun `butceye sigan metin degismez`() {
        val metin = satirlar(5)
        val sonuc = Truncator.truncate(metin, 1000)
        assertEquals(metin, sonuc.text)
        assertFalse(sonuc.truncated)
        assertEquals(0, sonuc.atilanSatir)
    }

    @Test
    fun `butceyi asan metin kirpilir ve isaretlenir`() {
        val sonuc = Truncator.truncate(satirlar(200), 200)
        assertTrue(sonuc.truncated)
        assertTrue(sonuc.atilanSatir > 0)
        assertTrue(sonuc.text.contains("kısaltıldı"), "kırpma işareti yok: ${sonuc.text}")
        assertTrue(sonuc.text.contains("${sonuc.atilanSatir}"), "atılan satır sayısı yazılmamış")
    }

    @Test
    fun `kirpma satir ortasindan yapilmaz`() {
        val sonuc = Truncator.truncate(satirlar(200), 200)
        // İşaret satırı hariç her satır ya tam "satirN" olmalı ya da boş.
        val bozuk = sonuc.text.lines()
            .filterNot { it.contains("kısaltıldı") }
            .filter { it.isNotEmpty() && !Regex("^satir\\d+$").matches(it) }
        assertTrue(bozuk.isEmpty(), "satır ortasından kesilmiş: $bozuk")
    }

    @Test
    fun `bas ve son korunur`() {
        val sonuc = Truncator.truncate(satirlar(200), 300)
        assertTrue(sonuc.text.startsWith("satir1"), "baş kaybolmuş")
        assertTrue(sonuc.text.trimEnd().endsWith("satir200"), "son kaybolmuş")
    }

    @Test
    fun `sonuc butceye yakin kalir`() {
        val sonuc = Truncator.truncate(satirlar(500), 300)
        assertTrue(sonuc.text.length <= 300, "çok uzun kalmış: ${sonuc.text.length}")
    }

    @Test
    fun `tek uzun satir karakterden kirpilir`() {
        // Satır sınırı yoksa kırpacak yer de yok; yine de sınıra uyulmalı.
        val sonuc = Truncator.truncate("x".repeat(500), 100)
        assertTrue(sonuc.truncated)
        assertTrue(sonuc.text.length <= 100)
    }

    @Test
    fun `sifir butce bos doner`() {
        val sonuc = Truncator.truncate(satirlar(10), 0)
        assertEquals("", sonuc.text)
        assertTrue(sonuc.truncated)
    }

    @Test
    fun `bos metin bos doner`() {
        val sonuc = Truncator.truncate("", 100)
        assertEquals("", sonuc.text)
        assertFalse(sonuc.truncated)
    }
}
