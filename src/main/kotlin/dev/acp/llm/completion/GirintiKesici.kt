package dev.acp.llm.completion

/**
 * Çok satırlı öneriyi imlecin bulunduğu bloğun içinde tutar (spec 007).
 *
 * **Neden girinti:** dile özgü kod çözümleme kullanamıyoruz (Altın Kural 3 —
 * eklenti dört IDE'de çalışmalı). Girinti, dilden bağımsız tek yapısal sinyal:
 * Python'da yapının kendisi, süslü parantezli dillerde kapanış satırı doğal
 * sınırı veriyor.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object GirintiKesici {

    /**
     * Sekme, editörün ayarına göre değil sabit genişlikte sayılır.
     *
     * Karışık sekme/boşluk kullanan dosyada karşılaştırma yaklaşık olur; kabul
     * edilebilir, çünkü hata yalnızca önerinin **uzunluğunu** etkiler, kabul
     * edilen kodu bozmaz.
     */
    const val SEKME_GENISLIGI = 4

    /** Satırın girinti genişliği. Tamamen boşluksa uzunluğu döner. */
    fun girintiGenisligi(satir: String): Int {
        var genislik = 0
        for (c in satir) {
            when (c) {
                ' ' -> genislik++
                '\t' -> genislik += SEKME_GENISLIGI
                else -> return genislik
            }
        }
        return genislik
    }

    /**
     * @param oneri modelin ürettiği ham metin (belirteçleri temizlenmiş)
     * @param tabanGirinti imlecin bulunduğu satırın girinti genişliği
     * @param maxSatir emniyet supabı — model hiç durmazsa
     *
     * Kurallar ve **neden**:
     *
     * 1. **Baştaki yeni satır atılır.** İmleç zaten girintideyken model kendi
     *    satırını açıyor; olduğu gibi kabul edilirse imlecin satırı boş kalır.
     *    (T10 ölçümünde gözlendi.)
     * 2. **İlk satır girinti karşılaştırmasına girmez.** Prompt imlecin satırının
     *    yazılmış kısmıyla bitiyor, dolayısıyla modelin ilk satırı o satırın
     *    devamıdır ve kendi girintisi yoktur. Karşılaştırmaya alınsaydı öneri
     *    daha başlamadan kesilirdi.
     * 3. **Boş satır bloğu bitirmez.** Girinti hesabına katılmaz — yoksa fonksiyon
     *    içindeki tek bir boş satır öneriyi erken keserdi.
     * 4. **Girintisi tabandan küçük ilk satırda durulur**, o satır atılır.
     *    Java/Kotlin'de kapanış `}` böyle elenir.
     * 5. **`maxSatir` her koşulda uygulanır.**
     * 6. **Derinlik 0'ken `}` ile başlayan satırda durulur.** Model bloğu
     *    girintisiz yazdığında girinti artık ayırt edemiyor: iç `for`'un
     *    kapanışı da fonksiyonun kapanışı da aynı hizada. Parantez derinliği
     *    ayırıyor — açılmamış bir bloğu kapatan satır, bizim bloğumuzu
     *    kapatıyordur.
     *
     *    Bu bir ayrıştırıcı değil, sayaç: dizge ve yorum içindeki parantezleri
     *    de sayar. Yanılırsa bedeli önerinin biraz kısa veya uzun olmasıdır,
     *    kodun bozulması değil. Süslü parantez kullanmayan dillerde (Python)
     *    hiç devreye girmez, girinti kuralı zaten yeterlidir.
     */
    fun kes(oneri: String, tabanGirinti: Int, maxSatir: Int): String {
        if (maxSatir <= 0) return ""

        // Kural 1.
        val govde = bastakiSatirBasiniAt(oneri.replace("\r\n", "\n"), tabanGirinti)
        val satirlar = govde.split('\n')

        val etkinTaban = etkinTaban(satirlar, tabanGirinti)

        val alinan = mutableListOf<String>()
        var derinlik = 0
        for ((sira, satir) in satirlar.withIndex()) {
            if (alinan.size >= maxSatir) break
            if (sira > 0 && satir.isNotBlank()) {
                // Kural 2 + 3 + 4.
                if (girintiGenisligi(satir) < etkinTaban) break
                // Kural 6.
                if (derinlik <= 0 && satir.trimStart().startsWith("}")) break
            }
            alinan += satir
            derinlik += satir.count { it == '{' } - satir.count { it == '}' }
        }

        // Sondaki boş satırlar gösterilecek bir şey değil.
        return alinan.joinToString("\n").trimEnd()
    }

    /**
     * Kesme eşiği — **modelin kendi hizasından** türetilir.
     *
     * Model bloğu her zaman bizim girintimizde yazmıyor; aynı sunucu, aynı ayar,
     * farklı fonksiyonda farklı davranıyor (2026-09-04 gözlemi):
     *
     * ```
     * uzunluk   → "\n    return s.length();"        girintili
     * tersCevir → "\nString ters = \"\";\nfor (…) {" girintisiz
     * ```
     *
     * Eşik olarak bizim girintimiz kullanılsaydı, ikinci durumda **ikinci satırda
     * hemen kesilirdi**: beş satırlık doğru bir gövde, tek işe yaramaz satıra
     * inerdi. Bu yüzden ölçüt, modelin ilk gövde satırının hizası ile bizim
     * girintimizden **küçük** olanıdır.
     */
    private fun etkinTaban(satirlar: List<String>, tabanGirinti: Int): Int =
        satirlar.drop(1).firstOrNull { it.isNotBlank() }
            ?.let { minOf(tabanGirinti, girintiGenisligi(it)) }
            ?: tabanGirinti

    /**
     * Modelin açtığı gereksiz satır başını atar.
     *
     * Model, imleç zaten girintideyken çıktısına `\n` + kendi girintisini
     * koyuyor (T10 ölçümünde gözlendi):
     *
     * ```
     * imleç:  "void f() {\n    ‸"
     * model:  "\n    int c = a + b;"
     * ```
     *
     * **Yalnızca `\n`'i atmak yetmez** — geriye kalan `    ` imlecin zaten
     * bulunduğu girintiye eklenir ve öneri 8 boşlukla girer. Bu yüzden `\n`
     * ile birlikte, taban girintiyi aşmayan kadar boşluk da atılır.
     *
     * Sonraki `\n` atılmaz: arka arkaya iki satır başı, modelin kasten
     * bıraktığı boş satır olabilir.
     */
    private fun bastakiSatirBasiniAt(metin: String, tabanGirinti: Int): String {
        if (!metin.startsWith("\n")) return metin

        var i = 1
        var atilan = 0
        while (i < metin.length && atilan < tabanGirinti) {
            when (metin[i]) {
                ' ' -> atilan++
                '\t' -> atilan += SEKME_GENISLIGI
                else -> return metin.substring(i)
            }
            i++
        }
        return metin.substring(i)
    }
}
