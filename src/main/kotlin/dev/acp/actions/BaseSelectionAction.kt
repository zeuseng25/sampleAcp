package dev.acp.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import dev.acp.llm.prompt.AksiyonTuru
import dev.acp.service.ChatSessionService

/**
 * Seçili koda uygulanan hazır eylemlerin ortak davranışı.
 *
 * Dört türev yalnızca [tur] veriyor; menü kaydı, etkinlik kuralı, paneli açma ve
 * servise iletme burada.
 *
 * Sonuç sohbet panelinde akıyor — ayrı pencere yok. Böylece ilerleme, iptal ve
 * konuşma geçmişi [002](../../../../specs/002-sohbet-paneli/) altyapısından
 * bedava geliyor.
 */
internal abstract class BaseSelectionAction(
    private val tur: AksiyonTuru,
) : AnAction(), DumbAware {

    /**
     * EDT: `update` seçim durumunu okuyor ve [com.intellij.openapi.editor.SelectionModel]
     * arka planda okunamaz. İşin kendisi ucuz (yalnızca "seçim var mı").
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        // Hiçbir şey seçili değilken eylem pasif (spec 003 H1).
        e.presentation.isEnabled = e.project != null &&
            editor != null &&
            editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        SecimEylemi.calistir(project, editor, e.getData(CommonDataKeys.VIRTUAL_FILE), tur)
    }
}

internal class ExplainSelectionAction : BaseSelectionAction(AksiyonTuru.ACIKLA)

internal class GenerateTestAction : BaseSelectionAction(AksiyonTuru.TEST_YAZ)

internal class RefactorSelectionAction : BaseSelectionAction(AksiyonTuru.YENIDEN_DUZENLE)

internal class AddDocsAction : BaseSelectionAction(AksiyonTuru.DOKUMAN_EKLE)
