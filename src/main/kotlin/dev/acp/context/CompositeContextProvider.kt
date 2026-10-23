package dev.acp.context

import dev.acp.llm.prompt.ContextItem
import dev.acp.llm.prompt.ContextKind
import dev.acp.llm.text.TokenEstimator
import dev.acp.llm.text.Truncator

/**
 * Kayıtlı tüm sağlayıcıları toplar ve **bütçeyi tek yerden** uygular.
 *
 * Bütçe kuralları (spec 002 H3):
 * - **Seçim asla kırpılmaz.** Kullanıcı onu bilerek seçti; kırpılmış bir seçim
 *   sorunun anlamını bozar. Seçim tek başına bütçeyi aşsa bile bütün gider,
 *   diğer öğeler düşer.
 * - Kalan bütçe önceliğe göre dağıtılır; sığmayan öğe **satır sınırında** kırpılır.
 * - Kırpılan öğe işaretlenir ki panelde ve prompt'ta belli olsun.
 */
internal object CompositeContextProvider {

    suspend fun collect(request: ContextRequest): List<ContextItem> {
        val ogeler = ContextProvider.EP.extensionList
            .sortedBy { it.priority }
            .flatMap { it.collect(request) }

        return butceyeSigdir(ogeler, TokenEstimator.karakterButcesi(request.tokenButcesi))
    }

    internal fun butceyeSigdir(ogeler: List<ContextItem>, karakterButcesi: Int): List<ContextItem> {
        if (ogeler.isEmpty()) return emptyList()

        val secimler = ogeler.filter { it.kind == ContextKind.SELECTION }
        val digerleri = ogeler.filterNot { it.kind == ContextKind.SELECTION }

        val sonuc = mutableListOf<ContextItem>()
        sonuc += secimler // kırpılmaz

        var kalan = karakterButcesi - secimler.sumOf { it.text.length }
        if (kalan <= 0) return sonuc // seçim bütçeyi doldurdu; gerisi düşer

        for (oge in digerleri) {
            if (kalan <= 0) break
            if (oge.text.length <= kalan) {
                sonuc += oge
                kalan -= oge.text.length
            } else {
                val kirpilmis = Truncator.truncate(oge.text, kalan)
                if (kirpilmis.text.isNotEmpty()) {
                    sonuc += oge.copy(text = kirpilmis.text, truncated = true)
                }
                kalan = 0
            }
        }
        return sonuc
    }
}
