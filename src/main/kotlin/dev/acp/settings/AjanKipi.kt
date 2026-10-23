package dev.acp.settings

/**
 * Ajanın değişiklikleri nasıl uygulayacağı (spec 008 H4).
 *
 * Sıra önemli: [ONIZLE] ilk sırada ve varsayılan. Enum'un ilk değeri, ayar
 * dosyası bozuk veya eksik olduğunda düşülecek yerdir.
 */
internal enum class AjanKipi(val gorunenAd: String) {
    /** Değişiklikler önce gösterilir; kullanıcı onaylamadan hiçbir dosya değişmez. */
    ONIZLE("Önizle ve onayla"),

    /**
     * Değişiklikler doğrudan uygulanır.
     *
     * "Öneri asla onay olmadan koda yazılmaz" kuralından **bilinçli sapma**
     * (spec 008). Tek geri alma adımı burada da korunur.
     */
    DOGRUDAN("Doğrudan uygula"),
}
