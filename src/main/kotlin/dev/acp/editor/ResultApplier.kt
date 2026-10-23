package dev.acp.editor

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import dev.acp.AcpBundle
import dev.acp.llm.text.MarkdownBlok
import dev.acp.llm.text.MarkdownFenceSplitter

/**
 * Model yanıtını editöre uygular.
 *
 * Zincir: **kod bloğunu ayıkla → önizle → onay → tek geri alma adımı.**
 * Hiçbir aşamada kullanıcı onayı atlanmaz (spec 003 H2).
 */
internal object ResultApplier {

    sealed interface Sonuc {
        data object Uygulandi : Sonuc

        /** Yanıt beklenirken kod değişmiş; uygulama reddedildi. */
        data object Bayat : Sonuc

        data object SaltOkunur : Sonuc
    }

    /**
     * Yanıttan uygulanacak kodu ayıklar: **ilk kod bloğu**.
     *
     * Şablonlar modele "tek bir kod bloğu ver, açıklamanı sonrasına yaz" diyor
     * (bkz. `PromptSelector` testleri). Yine de blok yoksa `null` dönülüyor ve
     * uygulama hiç önerilmiyor — açıklama metnini koda yazmak felaket olurdu.
     */
    fun kodBlogunuAyikla(yanit: String): String? =
        MarkdownFenceSplitter.split(yanit)
            .filterIsInstance<MarkdownBlok.Kod>()
            .firstOrNull()
            ?.kod
            ?.takeIf { it.isNotBlank() }

    /** Karşılaştırma isteğini kurar. Pencereyi açmadan test edilebilsin diye ayrı. */
    fun diffIstegiKur(
        project: Project,
        anchor: TargetAnchor,
        yeniKod: String,
        dosyaTuru: FileType,
    ): SimpleDiffRequest {
        val fabrika = DiffContentFactory.getInstance()
        return SimpleDiffRequest(
            AcpBundle.message("editor.diff.title"),
            fabrika.create(project, anchor.originalText, dosyaTuru),
            fabrika.create(project, yeniKod, dosyaTuru),
            AcpBundle.message("editor.diff.current"),
            AcpBundle.message("editor.diff.proposed"),
        )
    }

    /**
     * Önizlemeyi ayrı bir pencerede açar ve **pencere tutamağını** döndürür.
     *
     * Tutamak gerekli: değişiklik uygulandıktan sonra önizleme kapanmalı, yoksa
     * artık geçerli olmayan bir karşılaştırmayı göstermeye devam eder ve
     * kullanıcıyı yanıltır.
     */
    fun onizle(project: Project, istek: SimpleDiffRequest): WindowWrapper? {
        var pencere: WindowWrapper? = null
        DiffManager.getInstance().showDiff(
            project,
            istek,
            DiffDialogHints(WindowWrapper.Mode.FRAME, null) { w -> pencere = w },
        )
        return pencere
    }

    /**
     * Değişikliği uygular.
     *
     * Yazma ve biçimlendirme **aynı** [WriteCommandAction] içinde: ayrı komutlar
     * olsaydı geri alma iki adım sürerdi ve kullanıcı yarı biçimlenmiş kodla
     * kalırdı (spec 003 H2).
     *
     * [komutAdi] geri alma listesinde görünür; "Undo" yerine ne yapıldığı yazsın.
     */
    fun uygula(
        project: Project,
        anchor: TargetAnchor,
        yeniKod: String,
        komutAdi: String,
    ): Sonuc {
        // Yazmadan hemen önce kontrol: yanıt bekleme süresi boyunca kullanıcı
        // yazmış olabilir.
        if (anchor.bayatMi()) return Sonuc.Bayat
        if (!anchor.document.isWritable) return Sonuc.SaltOkunur

        val baslangic = anchor.baslangic

        WriteCommandAction.writeCommandAction(project)
            .withName(komutAdi)
            .withGroupId(GRUP)
            .run<RuntimeException> {
                anchor.document.replaceString(baslangic, anchor.bitis, yeniKod)

                val psiYoneticisi = PsiDocumentManager.getInstance(project)
                psiYoneticisi.commitDocument(anchor.document)
                psiYoneticisi.getPsiFile(anchor.document)?.let { psiDosya ->
                    CodeStyleManager.getInstance(project)
                        .reformatText(psiDosya, baslangic, baslangic + yeniKod.length)
                }
            }
        return Sonuc.Uygulandi
    }

    /** Ardışık ACP düzenlemeleri gerekirse birleştirilebilsin diye ortak grup. */
    private const val GRUP = "ACP"
}
