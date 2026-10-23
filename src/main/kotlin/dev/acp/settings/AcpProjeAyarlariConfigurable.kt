package dev.acp.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.acp.AcpBundle

/**
 * Proje ayar sayfası (spec 009 H4): doğrulama komutu.
 *
 * Boşken tahmin edilen komut alanın içinde soluk görünür — kullanıcı çalışmadan
 * önce neyin çalışacağını görür.
 */
internal class AcpProjeAyarlariConfigurable(private val project: Project) : BoundSearchableConfigurable(
    AcpBundle.message("settings.project.title"),
    "dev.acp.settings.project",
) {

    private val ayarlar get() = AcpProjeAyarlari.getInstance(project).state

    override fun createPanel(): DialogPanel = panel {
        row(AcpBundle.message("settings.project.verifyCommand")) {
            val alan = JBTextField().apply {
                emptyText.text = AcpProjeAyarlari.getInstance(project).tahmin()?.joinToString(" ")
                    ?: AcpBundle.message("settings.project.verifyCommand.none")
            }
            cell(alan)
                .align(AlignX.FILL)
                .bindText({ ayarlar.dogrulamaKomutu.orEmpty() }, { ayarlar.dogrulamaKomutu = it.trim() })
                .comment(AcpBundle.message("settings.project.verifyCommand.comment"))
        }
    }
}
