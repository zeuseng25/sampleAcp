package dev.acp

import java.io.InputStream
import java.io.InputStreamReader
import java.util.Properties

/**
 * Eklentinin **görünen adı**. Build sırasında `gradle.properties` içindeki
 * `acp.displayName` değerinden üretilen kaynaktan okunur (H6).
 *
 * Tek doğruluk kaynağı `gradle.properties`'tir; buradaki [VARSAYILAN] yalnızca
 * kaynak hiç üretilmemişse devreye giren acil düşüştür ve doğru bir build'de
 * hiçbir zaman kullanılmaz.
 *
 * **Eklenti kimliği (`dev.acp.assistant`) bundan etkilenmez** — ad değişse bile
 * IDE aynı eklentinin yeni sürümü olarak görür, yan yana ikinci kopya kurulmaz.
 */
internal object AcpBranding {

    const val VARSAYILAN = "ACP"

    private const val KAYNAK = "/acp-branding.properties"
    private const val ANAHTAR = "displayName"

    val displayName: String by lazy {
        oku(AcpBranding::class.java.getResourceAsStream(KAYNAK))
    }

    /** Ayrı fonksiyon: kaynak yüklemeden test edilebilsin diye. */
    internal fun oku(stream: InputStream?): String {
        if (stream == null) return VARSAYILAN
        return try {
            // Reader ile okuyoruz: Properties.load(InputStream) ISO-8859-1 varsayar
            // ve Türkçe karakterleri bozardı.
            stream.use { s ->
                Properties()
                    .apply { load(InputStreamReader(s, Charsets.UTF_8)) }
                    .getProperty(ANAHTAR)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: VARSAYILAN
            }
        } catch (e: Exception) {
            VARSAYILAN
        }
    }
}
