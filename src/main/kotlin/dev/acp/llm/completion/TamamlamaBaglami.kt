package dev.acp.llm.completion

/**
 * İmlecin iki yanındaki kodu, modele gönderilecek boyuta indirir.
 *
 * Sohbetteki `Truncator`'dan farklı: orada dosyanın **ortası** atılıp baş ve son
 * korunuyor. Burada tam tersi gerekiyor — imlecin **yakını** en değerli bilgi,
 * uzağı atılabilir. Dosyanın başındaki `import` satırları, imlecin bir üstündeki
 * satır kadar işe yaramaz.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object TamamlamaBaglami {

    /** İmleç öncesi için karakter bütçesi. Sonrasından cömert: geçmiş daha bilgilendirici. */
    const val VARSAYILAN_ONCESI = 3_000

    /** İmleç sonrası için karakter bütçesi. */
    const val VARSAYILAN_SONRASI = 1_000

    data class Baglam(
        val oncesi: String,
        val sonrasi: String,
        /** İmlecin bulunduğu satırın imleçten sonraki kısmı — sonek tekrarını kırpmak için. */
        val imlecSonrasiSatir: String,
        /**
         * İmlecin bulunduğu sütunun girinti genişliği — çok satırlı önerinin
         * nerede biteceğini bu belirler (spec 007).
         *
         * Satır başından imlece kadarki boşluk ölçülür. Çok satır kipinde bu,
         * imlecin solunda zaten yalnızca boşluk olduğu için imlecin sütunudur;
         * modelin üreteceği devam satırlarının hizalanması gereken değer budur.
         */
        val tabanGirinti: Int = 0,
    )

    /**
     * İmleç bir **üye erişiminin** hemen ardındaysa öneri üretilmemeli.
     *
     * Gerekçe: `toplam.` yazıldığında modele yalnızca metin gidiyor, tip bilgisi
     * gitmiyor. Model "nokta geldiyse metot çağrısı gelir" diye devam ediyor ve
     * `int` için `toString()` gibi **derlenmeyen** öneriler üretiyor.
     *
     * IDE ise `toplam`'ın tipini biliyor ve tam burada kendi listesini açıyor.
     * Yani bu, bizim kaybetmemiz gereken bir yarış: alanı IDE'ye bırakıyoruz
     * (spec 006 Kapsam dışı: "IDE'nin kendi kod tamamlamasının yerini almak").
     *
     * Diller arası çalışır — `.` Java, Kotlin, Python, JS, SQL'de aynı anlamda;
     * `::` ve `->` de üye/kapsam erişimi. Dile özgü API kullanılmıyor (Altın Kural 3).
     */
    fun uyeErisimiSonrasiMi(metin: String, imlec: Int): Boolean {
        val konum = imlec.coerceIn(0, metin.length)
        // İmlecin solundaki yazılmakta olan tanımlayıcıyı atla: `toplam.toStr|`
        // durumunda da nokta erişimi içindeyiz.
        var i = konum
        while (i > 0 && (metin[i - 1].isLetterOrDigit() || metin[i - 1] == '_')) i--
        if (i == 0) return false

        return when {
            // Noktadan önce rakam varsa bu ondalık sayıdır (`3.14`), üye erişimi
            // değil. Ayırt edilmezse sayı yazarken öneri kaybolur.
            metin[i - 1] == '.' -> !(i >= 2 && metin[i - 2].isDigit())
            i >= 2 && metin.regionMatches(i - 2, "::", 0, 2) -> true
            i >= 2 && metin.regionMatches(i - 2, "->", 0, 2) -> true
            else -> false
        }
    }

    /**
     * @param metin dosyanın tamamı
     * @param imlec imlecin metindeki konumu
     */
    fun kur(
        metin: String,
        imlec: Int,
        oncesiButce: Int = VARSAYILAN_ONCESI,
        sonrasiButce: Int = VARSAYILAN_SONRASI,
    ): Baglam {
        val konum = imlec.coerceIn(0, metin.length)

        val hamOncesi = metin.substring(0, konum)
        val hamSonrasi = metin.substring(konum)

        return Baglam(
            oncesi = bastanKirp(hamOncesi, oncesiButce),
            sonrasi = sondanKirp(hamSonrasi, sonrasiButce),
            imlecSonrasiSatir = hamSonrasi.substringBefore('\n'),
            tabanGirinti = GirintiKesici.girintiGenisligi(hamOncesi.substringAfterLast('\n')),
        )
    }

    /**
     * Metnin **sonunu** korur, başını atar. Kesme noktası satır başına
     * yuvarlanır — yarım satırla başlayan bağlam modeli yanıltır.
     */
    private fun bastanKirp(metin: String, butce: Int): String {
        if (metin.length <= butce) return metin
        val kaba = metin.substring(metin.length - butce)
        val ilkSatirSonu = kaba.indexOf('\n')
        // Bütçe tek bir devasa satırın ortasına düştüyse yuvarlanacak yer yok.
        return if (ilkSatirSonu >= 0) kaba.substring(ilkSatirSonu + 1) else kaba
    }

    /**
     * Metnin **başını** korur, sonunu atar. Kesme noktası satır sonuna yuvarlanır.
     */
    private fun sondanKirp(metin: String, butce: Int): String {
        if (metin.length <= butce) return metin
        val kaba = metin.substring(0, butce)
        val sonSatirSonu = kaba.lastIndexOf('\n')
        return if (sonSatirSonu >= 0) kaba.substring(0, sonSatirSonu + 1) else kaba
    }
}
