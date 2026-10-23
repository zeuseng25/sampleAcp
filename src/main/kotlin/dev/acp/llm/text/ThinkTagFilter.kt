package dev.acp.llm.text

import dev.acp.llm.ChatDelta

private const val OPEN = "<think>"
private const val CLOSE = "</think>"

/**
 * Akış içindeki `<think>…</think>` bloğunu ayıklayıp [ChatDelta.Reasoning] olarak
 * işaretler; kalanı [ChatDelta.Text] olarak geçirir.
 *
 * Neden gerekli: SGLang `--reasoning-parser qwen3` ile başlatılmışsa düşünme
 * içeriği ayrı bir `reasoning_content` alanında gelir ve bu filtreye hiç uğramaz.
 * Başlatılmamışsa aynı içerik `content` içinde satır içi gelir. İkisini de
 * karşılamak, sunucu yapılandırmasına bağımlı olmamayı sağlar.
 *
 * Etiket parça sınırına denk gelebildiği için (`"<thi"` + `"nk>"`), sondaki
 * olası kısmi etiket yayınlanmadan tamponda tutulur.
 */
internal class ThinkTagFilter {

    private val buffer = StringBuilder()
    private var inside = false

    fun feed(text: String): List<ChatDelta> {
        if (text.isEmpty()) return emptyList()
        buffer.append(text)

        val out = mutableListOf<ChatDelta>()
        while (true) {
            val tag = if (inside) CLOSE else OPEN
            val index = buffer.indexOf(tag)

            if (index >= 0) {
                if (index > 0) out += yayinla(buffer.substring(0, index))
                buffer.delete(0, index + tag.length)
                inside = !inside
                continue
            }

            // Etiket yok: sondaki olası kısmi etiketi tut, gerisini yayınla.
            val tutulan = kismiEtiketUzunlugu(tag)
            val guvenli = buffer.length - tutulan
            if (guvenli > 0) {
                out += yayinla(buffer.substring(0, guvenli))
                buffer.delete(0, guvenli)
            }
            break
        }
        return out
    }

    /** Akış bittiğinde tamponda kalanı verir (kapanmamış blok dahil). */
    fun flush(): List<ChatDelta> {
        if (buffer.isEmpty()) return emptyList()
        val kalan = buffer.toString()
        buffer.setLength(0)
        return yayinla(kalan)
    }

    private fun yayinla(s: String): List<ChatDelta> =
        listOf(if (inside) ChatDelta.Reasoning(s) else ChatDelta.Text(s))

    /** Tamponun sonu, aranan etiketin bir önekiyle bitiyorsa o önekin uzunluğu. */
    private fun kismiEtiketUzunlugu(tag: String): Int {
        val enFazla = minOf(buffer.length, tag.length - 1)
        for (uzunluk in enFazla downTo 1) {
            if (buffer.substring(buffer.length - uzunluk) == tag.substring(0, uzunluk)) {
                return uzunluk
            }
        }
        return 0
    }
}
