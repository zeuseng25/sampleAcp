package dev.acp.llm.agent

/**
 * Tek bir düzenleme: [eski] metnin [yeni] ile değiştirilmesi.
 *
 * **Neden satır numarası değil metin:** bir önceki düzenleme uygulandıktan sonra
 * satır numaraları kayar ve sonraki düzenleme **sessizce yanlış yere** yazar.
 * Metin eşleşmesi ise bayatlığı kendiliğinden yakalıyor — eski metin bulunamazsa
 * dosya değişmiş demektir (spec 008, plan kararı).
 */
internal data class Duzenleme(val yol: String, val eski: String, val yeni: String)

/**
 * Ajanın topladığı düzenlemeler (spec 008).
 *
 * **Diske hiçbir şey yazılmaz.** Yazma, döngü bittikten sonra tek bir işlemde
 * yapılır — spec'in "ya tamamen ya hiç" kuralı buna dayanıyor. Yarısı uygulanmış
 * çok dosyalı bir değişiklik, hiç uygulanmamış olmasından kötüdür.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal class DuzenlemePlani {

    private val duzenlemeler = mutableListOf<Duzenleme>()

    val boyut: Int get() = duzenlemeler.size
    val dosyaSayisi: Int get() = duzenlemeler.mapTo(mutableSetOf()) { it.yol }.size

    /**
     * @return eklendiyse true; **aynı düzenleme zaten varsa false**.
     *
     * Model aynı düzenlemeyi iki kez önerebiliyor (T10'da gözlendi: aynı aramayı
     * tekrarlayınca aynı yerleri yeniden buluyor). İki kez uygulanırsa ikincisi
     * "eski metin bulunamadı" diye atlanır — zararsız ama kullanıcıya gereksiz
     * bir "atlandı" uyarısı gösterir.
     */
    fun ekle(d: Duzenleme): Boolean {
        if (duzenlemeler.any { it == d }) return false
        duzenlemeler += d
        return true
    }

    /**
     * Dosya başına düzenlemeler, **ekleme sırasında**.
     *
     * Sıra önemli: aynı dosyada birbirini izleyen düzenlemeler sırayla
     * uygulanacak ve biri diğerinin metnini değiştirmiş olabilir.
     */
    fun dosyaBazinda(): Map<String, List<Duzenleme>> =
        duzenlemeler.groupBy { it.yol }

    fun tumu(): List<Duzenleme> = duzenlemeler.toList()

    fun temizle() = duzenlemeler.clear()
}
