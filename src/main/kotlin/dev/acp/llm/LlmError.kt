package dev.acp.llm

/**
 * LLM katmanının hata tipleri.
 *
 * Her hata, kullanıcıya gösterilebilecek Türkçe bir mesaj taşır. UI katmanı
 * yığın izini değil bu mesajı gösterir; ham gövde yalnızca log'a gider.
 *
 * `CancellationException` bu hiyerarşide YOKTUR — iptal bir hata değil,
 * kullanıcının eylemidir ve asla hata olarak gösterilmez.
 */
sealed class LlmError(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    /** Ayarlar eksik — base URL veya model adı girilmemiş. */
    class NotConfigured(val missing: String) : LlmError(
        "ACP ayarlanmamış: $missing eksik. Ayarlar → Tools → ACP bölümünden doldurun."
    )

    /** DNS, bağlantı reddi, TLS el sıkışma hatası. */
    class Connection(cause: Throwable) : LlmError(
        "Sunucuya ulaşılamıyor. Adresi, ağı ve sertifikayı kontrol edin.", cause
    )

    class Timeout(val phase: Phase, cause: Throwable? = null) : LlmError(
        when (phase) {
            Phase.CONNECT -> "Sunucuya bağlanma süresi aşıldı."
            Phase.READ -> "Sunucu yanıt vermeyi bıraktı (okuma süresi aşıldı)."
            Phase.IDLE -> "Sunucu yanıt ortasında sustu; bağlantı kesildi."
        },
        cause
    ) {
        enum class Phase {
            CONNECT,
            READ,

            /**
             * Akış başladı ama sunucu belirlenen süre boyunca hiç veri
             * göndermedi. Bu olmadan istemci sonsuza kadar beklerdi.
             */
            IDLE,
        }
    }

    /** 401 / 403. */
    class Unauthorized(val body: String?) : LlmError(
        "API anahtarı gerekli veya hatalı."
    )

    /** 400 — genellikle model adı yanlış ya da chat template uyumsuz. */
    class BadRequest(val body: String?) : LlmError(
        "Sunucu isteği reddetti. Model adı yanlış olabilir."
    )

    class ServerError(val status: Int, val body: String?) : LlmError(
        "Sunucu hatası (HTTP $status)."
    )

    /** Bozuk SSE veya JSON — sunucu OpenAI-uyumlu olmayan bir şey gönderdi. */
    class Protocol(val detail: String) : LlmError(
        "Sunucudan beklenmeyen yanıt biçimi geldi."
    )

    /** `[DONE]` gelmeden bağlantı koptu. Kısmi yanıt korunur. */
    class StreamTruncated : LlmError(
        "Yanıt tamamlanmadan bağlantı kesildi."
    )
}
