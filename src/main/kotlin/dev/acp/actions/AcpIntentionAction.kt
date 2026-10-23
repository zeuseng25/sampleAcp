package dev.acp.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import dev.acp.AcpBundle
import dev.acp.llm.prompt.AksiyonTuru

/**
 * Alt+Enter girişi.
 *
 * Dört ayrı intention yerine **tek** bir giriş: Alt+Enter listesi zaten kalabalık,
 * dört satır daha eklemek kullanıcıyı boğardı. Seçildiğinde dört eylemi içeren
 * küçük bir liste açılıyor.
 *
 * [startInWriteAction] `false` olmak **zorunda**: eylem ağ çağrısı başlatıyor ve
 * yazma kilidi altında ağ beklemek IDE'yi yanıt boyunca dondururdu.
 */
internal class AcpIntentionAction : IntentionAction, DumbAware {

    override fun getText(): String = AcpBundle.message("intention.text")

    override fun getFamilyName(): String = AcpBundle.message("intention.family")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        editor?.selectionModel?.hasSelection() == true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null) return
        val dosya = file?.virtualFile

        val turler = AksiyonTuru.entries.toList()
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(turler)
            .setTitle(AcpBundle.message("intention.chooseTitle"))
            .setNamerForFiltering { SecimEylemi.gorunenAd(it) }
            .setItemChosenCallback { tur -> SecimEylemi.calistir(project, editor, dosya, tur) }
            .createPopup()
            .showInBestPositionFor(editor)
    }
}
