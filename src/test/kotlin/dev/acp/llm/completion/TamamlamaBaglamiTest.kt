package dev.acp.llm.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * İmleç çevresindeki kodun kırpılması.
 *
 * Sohbetteki `Truncator`'ın tersi: orada dosyanın ortası atılır, burada imlecin
 * **uzağı** atılır. Dosyanın başındaki import satırları, imlecin bir üstündeki
 * satır kadar işe yaramaz.
 */
class TamamlamaBaglamiTest {

    @Test
    fun `imlec metni ikiye boler`() {
        val b = TamamlamaBaglami.kur("abc|def".replace("|", ""), 3)
        assertEquals("abc", b.oncesi)
        assertEquals("def", b.sonrasi)
    }

    @Test
    fun `imlec sonrasi satir ayri tutulur`() {
        // Sonek tekrarını kırpmak için gereken bilgi bu: imlecin sağında kalan,
        // ama YALNIZCA aynı satırdaki metin.
        val metin = "val x = topla(a);\nsonraki satir"
        val b = TamamlamaBaglami.kur(metin, metin.indexOf(");"))

        assertEquals(");", b.imlecSonrasiSatir)
        // Sonraki satır buna dahil değil — o, kırpma kararını ilgilendirmez.
        assertTrue(!b.imlecSonrasiSatir.contains("sonraki"))
    }

    @Test
    fun `imlec satir sonundaysa sonrasi satir bostur`() {
        val b = TamamlamaBaglami.kur("birinci\nikinci", 7)
        assertEquals("", b.imlecSonrasiSatir)
    }

    @Test
    fun `imlec basta`() {
        val b = TamamlamaBaglami.kur("merhaba", 0)
        assertEquals("", b.oncesi)
        assertEquals("merhaba", b.sonrasi)
    }

    @Test
    fun `imlec sonda`() {
        val b = TamamlamaBaglami.kur("merhaba", 7)
        assertEquals("merhaba", b.oncesi)
        assertEquals("", b.sonrasi)
    }

    @Test
    fun `sinirlar disindaki imlec kirpilir`() {
        // Belge imleç okunduktan sonra değişmiş olabilir; çökmemeli.
        assertEquals("abc", TamamlamaBaglami.kur("abc", 999).oncesi)
        assertEquals("abc", TamamlamaBaglami.kur("abc", -5).sonrasi)
    }

    @Test
    fun `butce asilinca imlecin YAKINI korunur`() {
        val metin = buildString {
            repeat(200) { appendLine("satir$it") }
            append("IMLEC_BURADA")
        }
        val b = TamamlamaBaglami.kur(metin, metin.length, oncesiButce = 100)

        assertTrue(b.oncesi.endsWith("IMLEC_BURADA"), "İmlecin hemen öncesi atılmış")
        assertTrue(!b.oncesi.contains("satir0\n"), "Uzaktaki satır korunmuş, bütçe uygulanmamış")
        assertTrue(b.oncesi.length <= 100)
    }

    @Test
    fun `oncesi kirpilirken satir basina yuvarlanir`() {
        val metin = "uzun_bir_ilk_satir_burada\nikinci\nucuncu\n"
        val b = TamamlamaBaglami.kur(metin, metin.length, oncesiButce = 20)

        // Yarım satırla başlayan bağlam modeli yanıltır.
        assertTrue(!b.oncesi.startsWith("_"), "Satır ortasından kesilmiş: '${b.oncesi}'")
        assertTrue(b.oncesi.startsWith("ikinci") || b.oncesi.startsWith("ucuncu"))
    }

    @Test
    fun `sonrasi kirpilirken satir sonuna yuvarlanir`() {
        val metin = "imlec" + "\nbir\niki\nuc\ndort\nbes\nalti\nyedi\n"
        val b = TamamlamaBaglami.kur(metin, 5, sonrasiButce = 12)

        assertTrue(b.sonrasi.endsWith("\n"), "Satır ortasından kesilmiş: '${b.sonrasi}'")
        assertTrue(b.sonrasi.length <= 12)
    }

    @Test
    fun `tek devasa satirda yuvarlanacak yer yoksa kaba kesim yapilir`() {
        // Küçültülmüş bir JS dosyası olabilir; çökmek yerine kesip devam etmeli.
        val metin = "x".repeat(5_000)
        val b = TamamlamaBaglami.kur(metin, metin.length, oncesiButce = 100)
        assertEquals(100, b.oncesi.length)
    }

    @Test
    fun `butce altindaki metin oldugu gibi kalir`() {
        val metin = "kisa\nmetin"
        val b = TamamlamaBaglami.kur(metin, 4)
        assertEquals("kisa", b.oncesi)
        assertEquals("\nmetin", b.sonrasi)
    }

    @Test
    fun `varsayilan butceler oncesine daha comert`() {
        // Geçmiş, gelecekten daha bilgilendirici.
        assertTrue(TamamlamaBaglami.VARSAYILAN_ONCESI > TamamlamaBaglami.VARSAYILAN_SONRASI)
    }

    @Test
    fun `bos dosyada coker mi`() {
        val b = TamamlamaBaglami.kur("", 0)
        assertEquals("", b.oncesi)
        assertEquals("", b.sonrasi)
        assertEquals("", b.imlecSonrasiSatir)
    }

    // --- üye erişimi bastırma (kullanıcı bulgusu 2026-09-04) ---

    private fun uyeMi(kod: String) =
        TamamlamaBaglami.uyeErisimiSonrasiMi(kod.replace("|", ""), kod.indexOf("|"))

    @Test
    fun `noktadan hemen sonra oneri uretilmez`() {
        // Gerçek bulgu: `toplam` int'ken model `toString()` önerdi — derlenmez.
        // IDE tipi biliyor, biz bilmiyoruz; alan IDE'nin.
        assertTrue(uyeMi("int toplam = 0; toplam.|"))
    }

    @Test
    fun `nokta sonrasi yazilmaya baslanmissa da bastirilir`() {
        assertTrue(uyeMi("toplam.toStr|"))
    }

    @Test
    fun `cift iki nokta bastirilir`() {
        assertTrue(uyeMi("Integer::|"))
        assertTrue(uyeMi("Integer::pars|"))
    }

    @Test
    fun `ok operatoru bastirilir`() {
        assertTrue(uyeMi("ptr->|"))
    }

    @Test
    fun `siradan satirda bastirilmaz`() {
        // Asıl risk bastırmanın FAZLA geniş olması: o zaman özellik hiç çalışmaz.
        assertTrue(!uyeMi("int toplam = |"))
        assertTrue(!uyeMi("    return |"))
        assertTrue(!uyeMi("for (int i = 1; i <= |"))
    }

    @Test
    fun `ondalik sayi uye erisimi sayilmaz`() {
        // "3.14" içindeki nokta üye erişimi DEĞİL. Bastırılırsa sayı yazarken
        // öneri kaybolur.
        // NOT: bu testin ilk hâli imleci "3|" koyuyordu — orada nokta yoktu,
        // yani hiçbir şeyi doğrulamıyordu. Gerçek durum aşağıdaki.
        assertTrue(!uyeMi("double x = 3.1|"), "ondalık sayı bastırıldı")
        assertTrue(!uyeMi("double x = 3.|"), "ondalık sayı bastırıldı")
    }

    @Test
    fun `satir basinda bastirilmaz`() {
        assertTrue(!uyeMi("|"))
        assertTrue(!uyeMi("kod\n|"))
    }

    @Test
    fun `noktadan sonra bosluk varsa bastirilmaz`() {
        // Zincirleme çağrı yeni satıra geçmişse artık "hemen sonra" değiliz.
        assertTrue(!uyeMi("nesne.metot() |"))
    }
}
