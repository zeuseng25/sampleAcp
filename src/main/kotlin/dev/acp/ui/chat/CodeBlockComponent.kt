package dev.acp.ui.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import dev.acp.AcpBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.JPanel

/**
 * Yanıttaki bir kod bloğu.
 *
 * Kendi renklendiricimizi yazmıyoruz: IDE'nin **gerçek** editör bileşenini
 * salt-okunur kipte kullanıyoruz. Böylece renklendirme her dilde doğru ve
 * kullanıcının editöründekiyle birebir aynı oluyor.
 *
 * Bedeli: editörler ağırdır ve [dispose] içinde `releaseEditor` çağrılması
 * **zorunludur**. Unutulursa klasik IntelliJ bellek sızıntısı olur; testle
 * korunuyor (spec 002 H2 son kriteri).
 *
 * Dil, [FileTypeManager] üzerinden çözülüyor — dile özgü PSI API'si değil,
 * platform seviyesinde (Altın Kural 3).
 */
internal class CodeBlockComponent(
    project: Project,
    dil: String?,
    kod: String,
) : JPanel(BorderLayout()), Disposable {

    private val editor: EditorEx

    init {
        val fabrika = EditorFactory.getInstance()
        val document = fabrika.createDocument(kod)
        editor = fabrika.createViewer(document, project) as EditorEx

        editor.highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, dosyaTuru(dil))

        editor.settings.apply {
            isLineNumbersShown = false
            isFoldingOutlineShown = false
            isLineMarkerAreaShown = false
            isIndentGuidesShown = false
            isRightMarginShown = false
            isCaretRowShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
        }
        editor.setVerticalScrollbarVisible(false)
        // Uzun satırlar için yatay kaydırma kalsın; sarmak kodu okunmaz yapıyor.
        editor.setHorizontalScrollbarVisible(true)
        editor.setBorder(JBUI.Borders.empty(4))

        // Editör yüksekliği içeriğe göre; dış panel zaten kaydırılıyor.
        val satirSayisi = document.lineCount.coerceAtLeast(1)
        editor.component.preferredSize =
            Dimension(1, editor.lineHeight * satirSayisi + JBUI.scale(12))

        border = JBUI.Borders.compound(
            JBUI.Borders.empty(4, 0),
            JBUI.Borders.customLine(JBColor.border(), 1),
        )

        add(ustBar(dil, kod), BorderLayout.NORTH)
        add(editor.component, BorderLayout.CENTER)
    }

    private fun ustBar(dil: String?, kod: String): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 6)
            dil?.let {
                add(
                    com.intellij.ui.components.JBLabel(it).apply {
                        foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
                        font = com.intellij.util.ui.JBFont.medium()
                    },
                    BorderLayout.WEST,
                )
            }
            add(kopyalaBaglantisi(kod), BorderLayout.EAST)
        }

    /**
     * Kopyalama, tıklandığını **görünür** kılar: metin kısa süre "Kopyalandı"
     * olur. Geri bildirim olmadan kullanıcı tıklamanın kaydedilip kaydedilmediğini
     * anlayamıyor — panoya bakmak tek yol kalıyor.
     */
    private fun kopyalaBaglantisi(kod: String): ActionLink {
        val varsayilan = AcpBundle.message("chat.copy")
        lateinit var baglanti: ActionLink
        baglanti = ActionLink(varsayilan) {
            CopyPasteManager.getInstance().setContents(StringSelection(kod))
            baglanti.text = AcpBundle.message("chat.copied")
            javax.swing.Timer(1500) { baglanti.text = varsayilan }
                .apply { isRepeats = false }
                .start()
        }
        return baglanti
    }

    override fun dispose() {
        // ZORUNLU. Atlanırsa editör sızar ve platform testleri (haklı olarak) düşer.
        EditorFactory.getInstance().releaseEditor(editor)
    }

    private companion object {
        /** Yaygın dil etiketleri → dosya uzantısı. */
        val UZANTILAR = mapOf(
            "kotlin" to "kt", "kt" to "kt",
            "java" to "java",
            "python" to "py", "py" to "py",
            "javascript" to "js", "js" to "js",
            "typescript" to "ts", "ts" to "ts",
            "tsx" to "tsx", "jsx" to "jsx",
            "go" to "go", "golang" to "go",
            "sql" to "sql",
            "json" to "json",
            "xml" to "xml",
            "html" to "html",
            "css" to "css",
            "yaml" to "yaml", "yml" to "yaml",
            "bash" to "sh", "sh" to "sh", "shell" to "sh", "zsh" to "sh",
            "rust" to "rs", "rs" to "rs",
            "c" to "c", "cpp" to "cpp", "c++" to "cpp",
            "csharp" to "cs", "cs" to "cs",
            "php" to "php",
            "ruby" to "rb", "rb" to "rb",
            "markdown" to "md", "md" to "md",
            "properties" to "properties",
            "gradle" to "gradle",
            "dockerfile" to "Dockerfile",
        )

        fun dosyaTuru(dil: String?) = when {
            dil == null -> PlainTextFileType.INSTANCE
            else -> {
                val uzanti = UZANTILAR[dil.lowercase()] ?: dil.lowercase()
                FileTypeManager.getInstance().getFileTypeByFileName("acp.$uzanti")
            }
        }
    }
}
