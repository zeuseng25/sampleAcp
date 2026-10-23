package dev.acp.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
 * Bir eylemin uygulanacağı kod aralığını **sabitler**.
 *
 * Neden gerekli: eylem başladığında seçim şurada, yanıt üç saniye sonra geliyor.
 * Kullanıcı bu arada yazmış olabilir. Sabitleme olmadan, o an seçili olan
 * aralığa yazmak **yanlış kodu sessizce ezer** — geri alınabilir olsa bile
 * kullanıcı ne kaybettiğini fark etmez.
 *
 * İki katmanlı koruma:
 * 1. [RangeMarker] belgedeki düzenlemelerle birlikte kayar; aralık silinirse
 *    geçersizleşir.
 * 2. Metin karşılaştırması: aralık aynı yerde durup **içeriği** değişmişse de
 *    bayat sayılır.
 *
 * Aralığın **öncesine** yazmak bayatlık sayılmaz — marker kaydığı için hedef
 * metin hâlâ aynıdır. Bu kasıtlı: kullanıcı başka yerde çalışıyorsa eylemi
 * engellemek gereksiz olurdu.
 */
internal class TargetAnchor private constructor(
    val file: VirtualFile,
    val document: Document,
    private val marker: RangeMarker,
    val originalText: String,
) {

    val baslangic: Int get() = marker.startOffset
    val bitis: Int get() = marker.endOffset

    /** Hedef aralık yakalandığından beri değişti mi. */
    fun bayatMi(): Boolean {
        if (!marker.isValid) return true
        val simdiki = document.getText(TextRange(marker.startOffset, marker.endOffset))
        return simdiki != originalText
    }

    /** Marker'ı serbest bırakır. Uygulama bitince veya iptal edilince çağrılmalı. */
    fun birak() {
        if (marker.isValid) marker.dispose()
    }

    companion object {
        fun yakala(
            document: Document,
            file: VirtualFile,
            baslangic: Int,
            bitis: Int,
        ): TargetAnchor {
            val marker = document.createRangeMarker(baslangic, bitis).apply {
                // Aralığın kenarına yazılan metin hedefe dahil edilmemeli.
                isGreedyToLeft = false
                isGreedyToRight = false
            }
            return TargetAnchor(
                file = file,
                document = document,
                marker = marker,
                originalText = document.getText(TextRange(baslangic, bitis)),
            )
        }
    }
}
