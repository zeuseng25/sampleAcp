package dev.acp.llm.prompt

/**
 * Bağlam öğeleri **saf** tiplerdir ve `dev.acp.llm` altında durur.
 *
 * Sağlayıcılar (IDE'ye bağlı olanlar) bu tipleri üretir, [PromptBuilder] tüketir.
 * Böylece prompt kurma mantığı IDE olmadan test edilebilir.
 *
 * **Plandan sapma:** plan bu tipleri `dev.acp.context` altında öngörüyordu.
 * Oraya konsaydı `PromptBuilder` IDE'ye bağlı bir paketten tip almak zorunda
 * kalırdı; katmanlama tersine dönerdi.
 */
internal enum class ContextKind {
    /** Kullanıcının seçtiği kod. **Asla kırpılmaz.** */
    SELECTION,

    /** Açık dosyanın tamamı veya bir kısmı. */
    CURRENT_FILE,

    /** Dosya yolu, dil gibi ucuz ama faydalı bilgiler. */
    PROJECT_META,
}

internal data class ContextItem(
    val kind: ContextKind,
    /** Panelde rozet olarak görünecek ad — kullanıcı ne paylaştığını bilmeli (H3). */
    val displayName: String,
    val text: String,
    val path: String? = null,
    /** Markdown kod bloğu etiketi. */
    val language: String? = null,
    val lineRange: IntRange? = null,
    val truncated: Boolean = false,
)
