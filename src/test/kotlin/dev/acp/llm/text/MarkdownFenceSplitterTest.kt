package dev.acp.llm.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 002/T40 — yanıttaki kod bloklarını ayırır.
 *
 * Tam bir markdown ayrıştırıcısı değil, kasten: tek ihtiyacımız kod bloklarını
 * gövdeden ayırmak. Bundled Markdown eklentisine bağlanmıyoruz çünkü JetBrains
 * kendi panel API'sinin kararsız olduğunu söylüyor.
 */
class MarkdownFenceSplitterTest {

    private fun bol(md: String) = MarkdownFenceSplitter.split(md)

    @Test
    fun `kod blogu yoksa tek metin blogu doner`() {
        assertEquals(listOf(MarkdownBlok.Metin("merhaba dünya")), bol("merhaba dünya"))
    }

    @Test
    fun `bos girdi bos liste doner`() {
        assertEquals(emptyList(), bol(""))
        assertEquals(emptyList(), bol("   \n  \n"))
    }

    @Test
    fun `dil etiketli kod blogu ayristirilir`() {
        val bloklar = bol("```kotlin\nfun a() {}\n```")
        assertEquals(1, bloklar.size)
        val kod = bloklar[0] as MarkdownBlok.Kod
        assertEquals("kotlin", kod.dil)
        assertEquals("fun a() {}", kod.kod)
        assertTrue(kod.kapali)
    }

    @Test
    fun `dil etiketi yoksa null olur`() {
        val kod = bol("```\nx\n```")[0] as MarkdownBlok.Kod
        assertEquals(null, kod.dil)
        assertEquals("x", kod.kod)
    }

    @Test
    fun `oncesi ve sonrasindaki metin korunur`() {
        val bloklar = bol("İşte örnek:\n```java\nint a;\n```\nUmarım yardımcı olur.")
        assertEquals(3, bloklar.size)
        assertEquals(MarkdownBlok.Metin("İşte örnek:"), bloklar[0])
        assertEquals("int a;", (bloklar[1] as MarkdownBlok.Kod).kod)
        assertEquals(MarkdownBlok.Metin("Umarım yardımcı olur."), bloklar[2])
    }

    @Test
    fun `birden fazla kod blogu`() {
        val bloklar = bol("```py\na\n```\narada\n```py\nb\n```")
        assertEquals(3, bloklar.size)
        assertEquals("a", (bloklar[0] as MarkdownBlok.Kod).kod)
        assertEquals(MarkdownBlok.Metin("arada"), bloklar[1])
        assertEquals("b", (bloklar[2] as MarkdownBlok.Kod).kod)
    }

    @Test
    fun `kapanmamis blok akis sirasinda gorulur`() {
        // Yanıt akarken kapanış henüz gelmemiş olur; blok yine de gösterilmeli.
        val kod = bol("```kotlin\nfun yarim(") [0] as MarkdownBlok.Kod
        assertEquals("kotlin", kod.dil)
        assertEquals("fun yarim(", kod.kod)
        assertFalse(kod.kapali)
    }

    @Test
    fun `tilde citleri de calisir`() {
        val kod = bol("~~~sql\nSELECT 1;\n~~~")[0] as MarkdownBlok.Kod
        assertEquals("sql", kod.dil)
        assertEquals("SELECT 1;", kod.kod)
    }

    @Test
    fun `farkli cit karakteri blogu kapatmaz`() {
        val kod = bol("```\nmetin\n~~~\ndevam\n```")[0] as MarkdownBlok.Kod
        assertEquals("metin\n~~~\ndevam", kod.kod)
    }

    @Test
    fun `dort backtick blogu ic ice uc backtick barindirir`() {
        val kod = bol("````md\n```\nic\n```\n````")[0] as MarkdownBlok.Kod
        assertEquals("md", kod.dil)
        assertEquals("```\nic\n```", kod.kod)
    }

    @Test
    fun `satir ici kod cit sayilmaz`() {
        assertEquals(
            listOf(MarkdownBlok.Metin("`x` diye yaz")),
            bol("`x` diye yaz"),
        )
    }

    @Test
    fun `bos kod blogu`() {
        val kod = bol("```\n```")[0] as MarkdownBlok.Kod
        assertEquals("", kod.kod)
        assertTrue(kod.kapali)
    }

    @Test
    fun `girintili cit de taninir`() {
        // Üç boşuğa kadar girinti markdown'da geçerli.
        val kod = bol("  ```go\n  x := 1\n  ```")[0] as MarkdownBlok.Kod
        assertEquals("go", kod.dil)
    }

    @Test
    fun `kod icindeki bos satirlar korunur`() {
        val kod = bol("```\na\n\nb\n```")[0] as MarkdownBlok.Kod
        assertEquals("a\n\nb", kod.kod)
    }
}
