package dev.acp.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import dev.acp.AcpBundle
import dev.acp.editor.TargetAnchor
import dev.acp.llm.prompt.AksiyonTuru
import dev.acp.service.ChatSessionService

/**
 * Seçim eyleminin ortak yürütmesi.
 *
 * Hem sağ tık menüsündeki [BaseSelectionAction] hem Alt+Enter'daki
 * [AcpIntentionAction] buradan geçer; iki giriş noktası için iki ayrı mantık
 * tutmak davranışların ayrışmasına yol açardı.
 */
internal object SecimEylemi {

    /** plugin.xml'deki sabit tanımlayıcı; görünen başlıkla karıştırılmamalı. */
    private const val TOOL_WINDOW_ID = "ACP"

    fun calistir(project: Project, editor: Editor, dosya: VirtualFile?, tur: AksiyonTuru) {
        if (!editor.selectionModel.hasSelection()) return

        // Kodu değiştiren eylemlerde hedef aralık ŞİMDİ sabitlenir. Yanıt saniyeler
        // sonra gelecek ve kullanıcı bu arada yazmış olabilir; sabitleme olmadan
        // yanlış aralık sessizce ezilirdi (spec 003 H2).
        val capa = if (tur.editoruDegistirir && dosya != null) {
            TargetAnchor.yakala(
                document = editor.document,
                file = dosya,
                baslangic = editor.selectionModel.selectionStart,
                bitis = editor.selectionModel.selectionEnd,
            )
        } else {
            null
        }

        // Panel kapalıysa açılmalı; yoksa kullanıcı sonucun nereye gittiğini görmez.
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate(null)

        val belge = editor.document
        val konum = dosya?.let {
            val ilk = belge.getLineNumber(editor.selectionModel.selectionStart) + 1
            val son = belge.getLineNumber(editor.selectionModel.selectionEnd) + 1
            "${dev.acp.agent.ProjeYollari.goreli(project, it)}, satır $ilk–$son"
        }

        ChatSessionService.getInstance(project).sendAction(
            tur = tur,
            gorunenAd = AcpBundle.message(gorunenAdAnahtari(tur)),
            dilEtiketi = dosya?.extension,
            capa = capa,
            konum = konum,
        )
    }

    fun gorunenAd(tur: AksiyonTuru): String = AcpBundle.message(gorunenAdAnahtari(tur))

    private fun gorunenAdAnahtari(tur: AksiyonTuru) = when (tur) {
        AksiyonTuru.ACIKLA -> "action.acikla.gorunen"
        AksiyonTuru.TEST_YAZ -> "action.testYaz.gorunen"
        AksiyonTuru.YENIDEN_DUZENLE -> "action.yenidenDuzenle.gorunen"
        AksiyonTuru.DOKUMAN_EKLE -> "action.dokumanEkle.gorunen"
    }
}
