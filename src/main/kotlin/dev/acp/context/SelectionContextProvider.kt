package dev.acp.context

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import dev.acp.llm.prompt.ContextItem
import dev.acp.llm.prompt.ContextKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v1 bağlam sağlayıcısı: **seçili kod + açık dosya**.
 *
 * Kasten naif. Proje geneli arama veya indeksleme yok (spec 002 kapsam dışı).
 * Dile özgü API kullanmıyor — yalnızca editör ve dosya adı, yani dört IDE'de de
 * aynı çalışıyor (Altın Kural 3).
 *
 * **Plandan sapma:** plan ayrıca bir `SURROUNDING` (seçim çevresi) öğesi
 * öngörüyordu. Açık dosyanın tamamı zaten gönderildiği ve bütçe aşılırsa
 * ortadan kırpıldığı için ayrı bir çevre öğesi v1'de değer katmıyor; gerekirse
 * sonradan eklenir.
 */
internal class SelectionContextProvider : ContextProvider {

    override val priority: Int = 0

    override suspend fun collect(request: ContextRequest): List<ContextItem> {
        // Editör durumu yalnızca EDT'de okunabilir. Anlık görüntü alıp çıkıyoruz:
        // kullanıcı biz istekle uğraşırken imleci oynatacak.
        val anlik = withContext(Dispatchers.EDT) {
            val editor: Editor =
                FileEditorManager.getInstance(request.project).selectedTextEditor
                    ?: return@withContext null

            val belge = editor.document
            val dosya = FileEditorManager.getInstance(request.project).selectedFiles.firstOrNull()
            val secim = editor.selectionModel

            Anlik(
                dosyaAdi = dosya?.name,
                yol = dosya?.path,
                dil = dosya?.extension,
                tamMetin = belge.text,
                secim = secim.selectedText,
                secimBaslangicSatiri = if (secim.hasSelection()) {
                    belge.getLineNumber(secim.selectionStart) + 1
                } else {
                    null
                },
                secimBitisSatiri = if (secim.hasSelection()) {
                    belge.getLineNumber(secim.selectionEnd) + 1
                } else {
                    null
                },
            )
        } ?: return emptyList()

        val ogeler = mutableListOf<ContextItem>()

        if (!anlik.secim.isNullOrBlank()) {
            ogeler += ContextItem(
                kind = ContextKind.SELECTION,
                displayName = "${anlik.dosyaAdi.orEmpty()} " +
                    "(${anlik.secimBaslangicSatiri}-${anlik.secimBitisSatiri})",
                text = anlik.secim,
                path = anlik.dosyaAdi,
                language = anlik.dil,
                lineRange = anlik.secimBaslangicSatiri!!..anlik.secimBitisSatiri!!,
            )
        }

        if (anlik.tamMetin.isNotBlank()) {
            ogeler += ContextItem(
                kind = ContextKind.CURRENT_FILE,
                displayName = anlik.dosyaAdi ?: "açık dosya",
                text = anlik.tamMetin,
                path = anlik.dosyaAdi,
                language = anlik.dil,
            )
        }

        return ogeler
    }

    private data class Anlik(
        val dosyaAdi: String?,
        val yol: String?,
        val dil: String?,
        val tamMetin: String,
        val secim: String?,
        val secimBaslangicSatiri: Int?,
        val secimBitisSatiri: Int?,
    )
}
