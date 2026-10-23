package dev.acp.settings

import java.net.URI

/**
 * Kullanıcının girdiği sunucu adresini tek bir biçime indirger.
 *
 * Kullanıcılar adresi çoğu zaman `/v1` ile birlikte yapıştırır (dokümanlarda
 * öyle geçtiği için). İstek yollarını biz ekliyoruz, dolayısıyla sondaki `/v1`
 * kalırsa `/v1/v1/models` gibi bir adrese gidilir ve hata "sunucuya ulaşılamıyor"
 * diye görünür — sebebi çok geç anlaşılır.
 *
 * Şema uydurulmaz: `http` mi `https` mi olduğunu kullanıcı belirtmeli. Sessizce
 * `http` varsaymak, kurumsal ağda şifresiz bağlantıya sebep olabilirdi.
 *
 * @return normalleşmiş adres, geçersizse `null`.
 */
internal object UrlNormalizer {

    private val DESTEKLENEN_SEMALAR = setOf("http", "https")
    private const val SURUM_EKI = "/v1"

    fun normalize(raw: String): String? {
        val kirpilmis = raw.trim()
        if (kirpilmis.isEmpty()) return null

        val uri = try {
            URI(kirpilmis)
        } catch (e: Exception) {
            return null
        }

        val sema = uri.scheme?.lowercase() ?: return null
        if (sema !in DESTEKLENEN_SEMALAR) return null

        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null

        var yol = uri.path.orEmpty().trimEnd('/')
        // Yalnızca SONDAKİ /v1 atılır; ortadaki bir yol parçasıysa korunur.
        if (yol.endsWith(SURUM_EKI)) {
            yol = yol.dropLast(SURUM_EKI.length).trimEnd('/')
        }

        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$sema://$host$port$yol"
    }
}
