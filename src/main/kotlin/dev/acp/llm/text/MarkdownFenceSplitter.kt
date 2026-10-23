package dev.acp.llm.text

/** Yanıt metninin bir parçası. */
internal sealed interface MarkdownBlok {

    data class Metin(val text: String) : MarkdownBlok

    /**
     * @param kapali Kapanış çiti geldi mi. Akış sırasında `false` olabilir —
     *   yanıt henüz bitmemişken de blok gösterilebilmeli.
     */
    data class Kod(val dil: String?, val kod: String, val kapali: Boolean) : MarkdownBlok
}

/**
 * Yanıt metnini düz metin ve kod bloklarına ayırır.
 *
 * **Tam bir markdown ayrıştırıcısı değildir, kasten.** Tek ihtiyacımız kod
 * bloklarını gövdeden ayırmak; gerisi düz metin olarak gösteriliyor. Bundled
 * Markdown eklentisine bağlanmıyoruz — JetBrains kendi panel API'sinin kararsız
 * olduğunu ve gözden geçirileceğini söylüyor.
 *
 * Desteklenen: ``` ve ~~~ çitleri, dil etiketi, üç boşluğa kadar girinti,
 * kapanmamış blok, iç içe daha kısa çitler.
 */
internal object MarkdownFenceSplitter {

    /** Açılış çiti: en fazla 3 boşluk girinti, 3+ çit karakteri, isteğe bağlı dil etiketi. */
    private val ACILIS = Regex("^ {0,3}(`{3,}|~{3,})[ \\t]*([^`~\\s]*)[ \\t]*$")

    fun split(markdown: String): List<MarkdownBlok> {
        if (markdown.isBlank()) return emptyList()

        val bloklar = mutableListOf<MarkdownBlok>()
        val metin = StringBuilder()
        val kod = StringBuilder()

        var kodIcinde = false
        var citKarakteri = ' '
        var citUzunlugu = 0
        var dil: String? = null

        fun metniKapat() {
            val icerik = metin.toString().trim('\n', ' ', '\t')
            if (icerik.isNotEmpty()) bloklar += MarkdownBlok.Metin(icerik)
            metin.setLength(0)
        }

        fun koduKapat(kapali: Boolean) {
            bloklar += MarkdownBlok.Kod(dil, kod.toString().removeSuffix("\n"), kapali)
            kod.setLength(0)
            dil = null
            kodIcinde = false
        }

        for (satir in markdown.lines()) {
            if (!kodIcinde) {
                val eslesme = ACILIS.matchEntire(satir)
                if (eslesme != null) {
                    metniKapat()
                    val cit = eslesme.groupValues[1]
                    citKarakteri = cit[0]
                    citUzunlugu = cit.length
                    dil = eslesme.groupValues[2].takeIf { it.isNotEmpty() }
                    kodIcinde = true
                } else {
                    metin.append(satir).append('\n')
                }
            } else {
                if (kapanisMi(satir, citKarakteri, citUzunlugu)) {
                    koduKapat(kapali = true)
                } else {
                    kod.append(satir).append('\n')
                }
            }
        }

        // Akış sırasında kapanış çiti henüz gelmemiş olabilir.
        if (kodIcinde) koduKapat(kapali = false) else metniKapat()

        return bloklar
    }

    /**
     * Kapanış çiti: **aynı karakterden**, açılıştan kısa olmayan, bilgi dizesi
     * içermeyen bir satır. Farklı çit karakteri bloğu kapatmaz — bu sayede
     * ```` bloğunun içinde ``` görünebilir.
     */
    private fun kapanisMi(satir: String, karakter: Char, enAzUzunluk: Int): Boolean {
        val kirpik = satir.trimStart(' ')
        if (satir.length - kirpik.length > 3) return false // 3'ten fazla girinti çit değil
        val cit = kirpik.takeWhile { it == karakter }
        if (cit.length < enAzUzunluk) return false
        return kirpik.drop(cit.length).isBlank()
    }
}
