package dev.acp.llm.openai

import dev.acp.llm.AracCagrisi
import dev.acp.llm.ToolCallParcasi

/**
 * Akışta parça parça gelen araç çağrılarını birleştirir (spec 008).
 *
 * **Neden gerekli:** OpenAI akışında araç argümanları JSON *metni* olarak
 * bölünerek geliyor:
 *
 * ```
 * {"index":0,"id":"call_1","function":{"name":"ara","arguments":"{\"desen\":"}}
 * {"index":0,"function":{"arguments":"\"eskiCagri\"}"}}
 * ```
 *
 * Yarım JSON ayrıştırılamaz. Parçalar birleşmeden tüketiciye verilirse her araç
 * çağrısı bozulur — ve bu, çalışan bir agent ile hiç çalışmayan bir agent
 * arasındaki farktır.
 *
 * `index` çağrıları ayırır: model **aynı anda birden fazla** araç çağırabilir
 * (T10 ölçümünde model 4 düzenlemeyi tek mesajda gönderdi).
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez, durum yalnızca burada.
 */
internal class ToolCallBiriktirici {

    private class Yapim(var id: String? = null, var ad: String? = null) {
        val argumanlar = StringBuilder()
    }

    /**
     * Sıra korunuyor: model çağrıları belirli bir sırayla göndermiş olabilir ve
     * kullanıcıya da o sırayla gösterilecek.
     */
    private val yapimlar = LinkedHashMap<Int, Yapim>()

    fun besle(parcalar: List<ToolCallParcasi>) {
        for (p in parcalar) {
            val y = yapimlar.getOrPut(p.index) { Yapim() }
            // id ve ad genellikle ilk parçada gelir; sonra tekrar gelirse
            // üzerine yazılmaz — ilk gelen doğrudur.
            if (y.id == null) p.id?.let { y.id = it }
            if (y.ad == null) p.ad?.let { y.ad = it }
            p.argumanParcasi?.let { y.argumanlar.append(it) }
        }
    }

    /** Şu ana kadar biriken parçalar var mı. */
    fun bosMu(): Boolean = yapimlar.isEmpty()

    /**
     * Tamamlanmış çağrıları döndürür ve biriktiriciyi sıfırlar.
     *
     * **Adı olmayan çağrı atılır.** Ad olmadan çalıştırılacak bir şey yok;
     * yarım kalmış bir parçayı çağrı sanıp modele "bilinmeyen araç" hatası
     * döndürmek onu yanlış yola sokardı.
     *
     * Argümanlar boşsa `{}` kabul edilir: parametresiz araç çağrısında bazı
     * sunucular `arguments` alanını hiç göndermiyor.
     */
    fun tamamla(): List<AracCagrisi> {
        val sonuc = yapimlar.values.mapNotNull { y ->
            val ad = y.ad ?: return@mapNotNull null
            AracCagrisi(
                id = y.id ?: "",
                ad = ad,
                argumanlarJson = y.argumanlar.toString().ifBlank { "{}" },
            )
        }
        yapimlar.clear()
        return sonuc
    }
}
