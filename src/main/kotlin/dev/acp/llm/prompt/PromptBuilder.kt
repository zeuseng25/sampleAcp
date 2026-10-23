package dev.acp.llm.prompt

/**
 * Bağlam öğelerini ve kullanıcının sorusunu tek bir mesaja çevirir.
 *
 * Sağlayıcılar biçimlenmiş metin değil **tiplenmiş öğe** döndürüyor; biçimleme
 * yalnızca burada yapılıyor. Böylece ileride PSI tabanlı bir sağlayıcı eklendiğinde
 * yalnızca yeni öğeler katkı verir, biçim tek yerden değişir.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object PromptBuilder {

    /** Kullanıcı mesajının modele gidecek gövdesi. */
    fun build(soru: String, ogeler: List<ContextItem>): String {
        if (ogeler.isEmpty()) return soru

        return buildString {
            appendLine("Aşağıdaki bağlam kullanıcının IDE'sinden alınmıştır.")
            appendLine()
            for (oge in ogeler.sortedBy { it.kind.ordinal }) {
                appendLine(baslik(oge))
                if (oge.kind == ContextKind.PROJECT_META) {
                    appendLine(oge.text)
                } else {
                    appendLine("```${oge.language.orEmpty()}")
                    appendLine(oge.text)
                    appendLine("```")
                }
                if (oge.truncated) appendLine("(Bu parça bütçe nedeniyle kısaltıldı.)")
                appendLine()
            }
            appendLine("---")
            append(soru)
        }
    }

    private fun baslik(oge: ContextItem): String {
        val tur = when (oge.kind) {
            ContextKind.SELECTION -> "Seçili kod"
            ContextKind.CURRENT_FILE -> "Açık dosya"
            ContextKind.PROJECT_META -> "Bilgi"
        }
        val yer = buildString {
            oge.path?.let { append(" — ").append(it) }
            oge.lineRange?.let { append(" (${it.first}-${it.last}. satır)") }
        }
        return "## $tur$yer"
    }
}
