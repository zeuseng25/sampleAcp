package dev.acp.llm.openai

/**
 * Akıştan gelen karakter parçalarını satırlara böler.
 *
 * Parça sınırları satırın — hatta CRLF'in — ortasına düşebildiği için
 * tamamlanmamış son satır tamponda bekletilir.
 *
 * Kasıtlı basitleştirme: satır sonu olarak yalnızca `\n` kabul edilir ve
 * varsa öndeki `\r` kırpılır. Tek başına `\r` (eski Mac stili) satır sonu
 * sayılmaz — OpenAI-uyumlu sunucular bunu üretmez.
 */
internal class SseLineSplitter {

    private val buffer = StringBuilder()

    fun feed(chunk: String): List<String> {
        if (chunk.isEmpty()) return emptyList()
        buffer.append(chunk)

        val lines = mutableListOf<String>()
        var start = 0
        while (true) {
            val newline = buffer.indexOf("\n", start)
            if (newline < 0) break
            var end = newline
            if (end > start && buffer[end - 1] == '\r') end--
            lines.add(buffer.substring(start, end))
            start = newline + 1
        }
        buffer.delete(0, start)
        return lines
    }

    /** Akış bittiğinde tamponda kalan tamamlanmamış satırı verir. */
    fun flush(): String? {
        if (buffer.isEmpty()) return null
        val rest = buffer.toString().removeSuffix("\r")
        buffer.setLength(0)
        return rest.ifEmpty { null }
    }
}
