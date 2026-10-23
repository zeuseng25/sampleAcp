package dev.acp.llm.openai

/** Bir SSE olayı. */
internal sealed interface SseEvent {
    /** `data:` alanlarının birleşimi. JSON gövdesi burada, ayrıştırılmamış halde. */
    data class Data(val payload: String) : SseEvent

    /** `data: [DONE]` — akışın normal sonu. */
    data object Done : SseEvent
}

/**
 * Satırları SSE olaylarına çevirir.
 *
 * Bir olay boş satırla sonlanır; aynı olaydaki birden çok `data:` alanı
 * `\n` ile birleşir. `event:`, `id:`, `retry:` alanları ve `:` ile başlayan
 * yorum/heartbeat satırları yok sayılır.
 */
internal class SseParser {

    private val data = StringBuilder()
    private var sawData = false

    /** Bir satır işler. Olay tamamlandıysa döner, aksi halde `null`. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return finishEvent()
        if (line.startsWith(":")) return null // yorum / heartbeat

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val rawValue = if (colon < 0) "" else line.substring(colon + 1)
        val value = rawValue.removePrefix(" ")

        if (field == "data") {
            if (sawData) data.append('\n')
            data.append(value)
            sawData = true
        }
        return null
    }

    /** Akış, sonlandırıcı boş satır gelmeden bittiyse son olayı kurtarır. */
    fun flush(): SseEvent? = finishEvent()

    private fun finishEvent(): SseEvent? {
        if (!sawData) return null
        val payload = data.toString()
        data.setLength(0)
        sawData = false
        return if (payload.trim() == "[DONE]") SseEvent.Done else SseEvent.Data(payload)
    }
}
