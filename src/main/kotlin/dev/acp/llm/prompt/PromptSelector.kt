package dev.acp.llm.prompt

/**
 * Seçime uygulanabilecek hazır eylemler.
 *
 * @property dosyaAdi `resources/prompts/` altındaki şablonun taban adı.
 * @property editoruDegistirir Sonuç editöre uygulanabilir mi. `false` olanlarda
 *   uygulama düğmesi hiç gösterilmez.
 */
internal enum class AksiyonTuru(val dosyaAdi: String, val editoruDegistirir: Boolean) {
    ACIKLA("acikla", editoruDegistirir = false),
    TEST_YAZ("test-yaz", editoruDegistirir = false),
    YENIDEN_DUZENLE("yeniden-duzenle", editoruDegistirir = true),
    DOKUMAN_EKLE("dokuman-ekle", editoruDegistirir = true),
}

/**
 * Eylem ve dosya türüne göre prompt şablonunu seçer.
 *
 * Şablonlar **pakette** durur ve kullanıcı düzenleyemez (spec 003 Belirsizlik 1):
 * prompt bir yapılandırma değeri değil, ürünün davranışıdır. İyileştirme sürümle
 * gelir ve herkes aynı kaliteyi alır.
 *
 * Dile özgü şablon varsa (`test-yaz.kt.md`) o kullanılır, yoksa genel olana
 * düşülür (`test-yaz.md`). Dil çözümü `FileType` uzantısından gelir — dile özgü
 * PSI API'si kullanılmaz (Altın Kural 3).
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object PromptSelector {

    fun sablon(tur: AksiyonTuru, dilEtiketi: String?): String =
        sablon(tur, dilEtiketi, ::kaynagiOku)

    /** Kaynak yükleyici enjekte edilebilir: paketleme olmadan test edilebilsin diye. */
    internal fun sablon(
        tur: AksiyonTuru,
        dilEtiketi: String?,
        yukle: (String) -> String?,
    ): String {
        val dil = dilEtiketi?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (dil != null) {
            yukle("/prompts/${tur.dosyaAdi}.$dil.md")?.let { return it.trim() }
        }
        return yukle("/prompts/${tur.dosyaAdi}.md")?.trim()
            // Şablonun eksik olması bir paketleme hatasıdır; sessizce boş prompt
            // göndermektense yüksek sesle patlaması iyidir.
            ?: error("Prompt şablonu pakette yok: ${tur.dosyaAdi}")
    }

    /** Sohbet/Uzman sistem yönergesi (spec 010) — `prompts/<ad>.md`. */
    fun yonerge(ad: String): String =
        kaynagiOku("/prompts/$ad.md")?.trim() ?: error("Yönerge pakette yok: $ad")

    private fun kaynagiOku(yol: String): String? =
        PromptSelector::class.java.getResourceAsStream(yol)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
}
