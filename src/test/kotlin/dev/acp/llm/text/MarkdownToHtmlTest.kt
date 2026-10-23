package dev.acp.llm.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Markdown → HTML (spec 002, 2026-09-04'te eklendi).
 *
 * En kritik iki davranış: **model çıktısı kaçırılır** (yanıtta HTML geçmesi
 * sıradan) ve **hiçbir koşulda metin kaybolmaz** — kapanmamış bir işaret
 * yüzünden cümlenin gerisi yok olmamalı.
 */
class MarkdownToHtmlTest {

    private fun cevir(md: String) = MarkdownToHtml.cevir(md)

    // ---- başlıklar ----

    @Test
    fun `basliklar seviyeye gore cevrilir`() {
        assertTrue(cevir("# Baslik").contains("<h1>Baslik</h1>"))
        assertTrue(cevir("## Baslik").contains("<h2>Baslik</h2>"))
        assertTrue(cevir("### Baslik").contains("<h3>Baslik</h3>"))
    }

    @Test
    fun `derin basliklar h4te toplanir`() {
        // Swing h5/h6'yı ayırt edilebilir göstermiyor.
        assertTrue(cevir("##### Baslik").contains("<h4>"))
    }

    @Test
    fun `bosluksuz diyez baslik degildir`() {
        // "#5 numaralı hata" gibi metinler başlığa dönüşmemeli.
        val h = cevir("#5 numarali hata")
        assertTrue(!h.contains("<h1>"), h)
        assertTrue(h.contains("#5 numarali hata"), h)
    }

    // ---- satır içi ----

    @Test
    fun `kalin ve italik`() {
        assertTrue(cevir("bu **kalin** ve *italik*").contains("<b>kalin</b>"))
        assertTrue(cevir("bu **kalin** ve *italik*").contains("<i>italik</i>"))
    }

    @Test
    fun `satir ici kod`() {
        assertTrue(cevir("`kod` burada").contains("<code>kod</code>"))
    }

    @Test
    fun `kod icindeki yildiz bicimlendirme sayilmaz`() {
        // `a*b*c` içindeki yıldızlar italik yapmamalı.
        val h = cevir("`a*b*c`")
        assertTrue(h.contains("<code>a*b*c</code>"), h)
        assertTrue(!h.contains("<i>"), h)
    }

    // ---- listeler ----

    @Test
    fun `madde listesi`() {
        val h = cevir("- bir\n- iki")
        assertTrue(h.contains("<ul>"), h)
        assertTrue(h.contains("<li>bir</li>"), h)
        assertTrue(h.contains("<li>iki</li>"), h)
        assertTrue(h.contains("</ul>"), h)
    }

    @Test
    fun `numarali liste`() {
        val h = cevir("1. bir\n2. iki")
        assertTrue(h.contains("<ol>"), h)
        assertTrue(h.contains("<li>bir</li>"), h)
    }

    @Test
    fun `liste turu degisince kapanir ve yenisi acilir`() {
        val h = cevir("- madde\n1. numarali")
        assertTrue(h.contains("</ul>"), h)
        assertTrue(h.contains("<ol>"), h)
    }

    @Test
    fun `liste bitince kapanir`() {
        val h = cevir("- madde\n\nduz metin")
        assertTrue(h.contains("</ul>"), h)
        assertTrue(h.contains("duz metin"), h)
    }

    @Test
    fun `yatay cizgi`() {
        assertTrue(cevir("---").contains("<hr>"))
        assertTrue(cevir("***").contains("<hr>"))
    }

    // ---- KAÇIRMA: en kritik davranış ----

    @Test
    fun `model ciktisindaki HTML duz metin olarak gorunur`() {
        // Kullanıcı HTML sorabilir; yanıttaki <div> görünümü bozmamalı.
        val h = cevir("<div class=\"x\">merhaba</div>")
        assertTrue(h.contains("&lt;div"), h)
        assertTrue(!h.contains("<div"), "HTML kaçırılmamış: $h")
    }

    @Test
    fun `karsilastirma operatorleri bozulmaz`() {
        // `i < sayilar.length` sıradan bir kod parçası.
        val h = cevir("`i < sayilar.length`")
        assertTrue(h.contains("&lt;"), h)
        assertTrue(h.contains("<code>"), "kod etiketi de kaçırılmış: $h")
    }

    @Test
    fun `ampersand kacirilir`() {
        assertTrue(cevir("a && b").contains("&amp;&amp;"))
    }

    @Test
    fun `urettigimiz etiketler kacirilmaz`() {
        // Sıra yanlış olsaydı ekranda &lt;b&gt; görünürdü.
        val h = cevir("**kalin**")
        assertTrue(h.contains("<b>kalin</b>"), h)
        assertTrue(!h.contains("&lt;b&gt;"), h)
    }

    // ---- METİN KAYBOLMAZ: ikinci kritik davranış ----

    @Test
    fun `kapanmamis kalin isareti metni yutmaz`() {
        val h = cevir("bu **kapanmadi ve devami var")
        assertTrue(h.contains("kapanmadi ve devami var"), "metin kayboldu: $h")
    }

    @Test
    fun `kapanmamis kod isareti metni yutmaz`() {
        val h = cevir("bu `kapanmadi ve devami")
        assertTrue(h.contains("kapanmadi ve devami"), "metin kayboldu: $h")
    }

    @Test
    fun `bos cift bicimlendirme sayilmaz`() {
        val h = cevir("**** dort yildiz")
        assertTrue(h.contains("dort yildiz"), h)
    }

    @Test
    fun `bos girdi coker mi`() {
        assertEquals("", cevir(""))
    }

    @Test
    fun `yalnizca bosluk`() {
        assertTrue(cevir("   ").contains("<br>"))
    }

    @Test
    fun `duz metin oldugu gibi kalir`() {
        assertTrue(cevir("sadece duz bir cumle.").contains("sadece duz bir cumle."))
    }

    @Test
    fun `hicbir girdide metin kaybolmaz`() {
        // Kaba güvence: harf ve rakamlar çıktıda korunur.
        val girdiler = listOf(
            "## Baslik\n\n- madde **kalin**\n- `kod`\n\nson cumle.",
            "*** tuhaf **islaretler* karisik `kod",
            "1. bir\n2. iki\n\n---\n\nson",
        )
        for (g in girdiler) {
            val h = cevir(g)
            // Liste işaretleri (1. 2. gibi) kasten kaybolur — <ol> onları kendisi
            // numaralandırır. Bu yüzden yalnızca HARFLER kontrol edilir.
            val beklenen = g.filter { it.isLetter() }.lowercase().toSet()
            val gelen = h.filter { it.isLetter() }.lowercase()
            for (c in beklenen) {
                assertTrue(gelen.contains(c), "'$c' harfi kayboldu. Girdi: $g")
            }
        }
    }
}
