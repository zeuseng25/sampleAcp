package dev.acp.llm.text

/**
 * Metni bir karakter bütçesine sığdırır.
 *
 * **Ortadan kırpar, sondan değil.** Kodun başı (paket, import, sınıf bildirimi)
 * ve sonu (genellikle imlecin bulunduğu yer) ortasından daha değerlidir.
 *
 * Kırpma **satır sınırında** yapılır: yarım satır göndermek modelin kafasını
 * karıştırır ve kırpıldığı belli olmaz.
 */
internal object Truncator {

    data class Sonuc(val text: String, val truncated: Boolean, val atilanSatir: Int)

    /** İşaret satırı için ayrılan kaba pay. */
    private const val ISARET_PAYI = 40

    fun truncate(text: String, maxChars: Int): Sonuc {
        if (text.isEmpty()) return Sonuc("", false, 0)
        if (maxChars <= 0) return Sonuc("", true, text.lines().size)
        if (text.length <= maxChars) return Sonuc(text, false, 0)

        val satirlar = text.lines()
        if (satirlar.size < 3) {
            // Satır sınırı yok; sınıra uymak için karakterden kesmek zorundayız.
            return Sonuc(text.take(maxChars), true, 0)
        }

        val butce = (maxChars - ISARET_PAYI).coerceAtLeast(0)
        if (butce == 0) return Sonuc(text.take(maxChars), true, satirlar.size)

        val basButcesi = butce / 2
        val sonButcesi = butce - basButcesi

        val bas = mutableListOf<String>()
        var basUzunluk = 0
        for (satir in satirlar) {
            if (basUzunluk + satir.length + 1 > basButcesi) break
            bas += satir
            basUzunluk += satir.length + 1
        }

        val son = ArrayDeque<String>()
        var sonUzunluk = 0
        for (satir in satirlar.asReversed()) {
            if (bas.size + son.size >= satirlar.size) break
            if (sonUzunluk + satir.length + 1 > sonButcesi) break
            son.addFirst(satir)
            sonUzunluk += satir.length + 1
        }

        val atilan = satirlar.size - bas.size - son.size
        if (atilan <= 0) return Sonuc(text, false, 0)

        val isaret = "… (kısaltıldı: $atilan satır) …"
        val birlesik = (bas + isaret + son).joinToString("\n")

        // Pay tahmini tutmadıysa (çok uzun işaret, uzun satırlar) yine de sınıra uy.
        return if (birlesik.length <= maxChars) {
            Sonuc(birlesik, true, atilan)
        } else {
            Sonuc(birlesik.take(maxChars), true, atilan)
        }
    }
}
