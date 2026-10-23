package dev.acp.llm.agent

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.acp.llm.AracTanimi

/**
 * Araç tanımları — sohbet, Uzman ve çok dosyalı kip aynı listeleri kullanır (spec 010 T20).
 *
 * Sohbet ve Uzman'a yalnızca [OKUMA] verilir. Bu tek başına yeterli değildir —
 * model tanımda olmayan bir adı da çağırabilir — yürütücü de o kipte
 * [DUZENLE]'yi reddeder.
 */
internal object AracSetleri {

    const val DUZENLE = "dosya_duzenle"
    const val DOGRULA = "dogrula"

    private val mapper = ObjectMapper()

    /** Alanlar sırayla `required`'a girer; `satir` tamsayıdır. */
    private fun sema(zorunlu: List<Pair<String, String>>, istege: List<Pair<String, String>> = emptyList()): JsonNode {
        val n = mapper.createObjectNode()
        n.put("type", "object")
        val p = n.putObject("properties")
        (zorunlu + istege).forEach { (ad, aciklama) ->
            p.putObject(ad).put("type", if (ad == "satir") "integer" else "string").put("description", aciklama)
        }
        val r = n.putArray("required")
        zorunlu.forEach { r.add(it.first) }
        return n
    }

    private const val YOL = "Proje köküne göreli dosya yolu"

    val OKUMA: List<AracTanimi> = listOf(
        AracTanimi(
            "dosya_ara",
            "Projede metin arar; eşleşen dosya yolunu, satır numarasını ve tam satırı döndürür.",
            sema(listOf("desen" to "Aranacak birebir metin")),
        ),
        AracTanimi(
            "dosya_oku",
            "Bir dosyanın içeriğini döndürür (proje köküne göreli yol).",
            sema(listOf("yol" to YOL)),
        ),
        AracTanimi(
            "kullanimlari_bul",
            "Bir metodu, fonksiyonu, sınıfı veya alanı GERÇEKTEN kullanan yerleri bulur " +
                "(IDE'nin anlam dizininden; aynı adlı ilgisiz şeyler karışmaz). Her yer için " +
                "içinde bulunduğu metot/fonksiyon da verilir. Metin aramasından daha kesindir.",
            sema(
                listOf("yol" to "Sembolün TANIMLANDIĞI dosya", "ad" to "Sembolün adı"),
                listOf("satir" to "Aynı adla birden fazla tanım varsa tanımın satırı"),
            ),
        ),
        AracTanimi(
            "uygulamalari_bul",
            "Bir arayüzü, soyut sınıfı veya metodu uygulayan/üzerine yazan tanımları bulur.",
            sema(
                listOf("yol" to "Sembolün TANIMLANDIĞI dosya", "ad" to "Sembolün adı"),
                listOf("satir" to "Aynı adla birden fazla tanım varsa tanımın satırı"),
            ),
        ),
        AracTanimi(
            "tanima_git",
            "Bir dosyanın belirli satırında geçen bir adın nerede TANIMLANDIĞINI bulur " +
                "(çağrılan metodun tanımına inmek için).",
            sema(listOf("yol" to YOL, "satir" to "Adın geçtiği satır (1'den başlar)", "ad" to "Ad")),
        ),
    )

    val DUZENLEME: List<AracTanimi> = OKUMA + listOf(
        AracTanimi(
        DUZENLE,
        "Bir düzenleme kaydeder. Diske YAZMAZ. 'eski' metin dosyada birebir " +
            "ve yalnızca bir kez geçmelidir.",
        sema(
            listOf(
                "yol" to YOL,
                "eski" to "Değiştirilecek metnin tamamı, baştaki boşluklar dahil",
                "yeni" to "Yerine yazılacak metin",
            ),
        ),
        ),
        AracTanimi(
            DOGRULA,
            "Projenin derleme/test komutunu KULLANICININ ONAYIYLA çalıştırır ve çıktısını döndürür. " +
                "Komutu sen seçemezsin. Yalnızca kullanıcı bir derleme veya test hatası bildirdiyse " +
                "ve hata metni görevde yoksa kullan. Diskteki kodu derler: kaydettiğin düzenlemeler " +
                "henüz diskte değildir.",
            sema(emptyList()),
        ),
    )
}
