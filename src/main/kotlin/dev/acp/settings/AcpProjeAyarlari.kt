package dev.acp.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtilCore
import dev.acp.agent.ProjeYollari
import dev.acp.komut.KomutSatiri
import dev.acp.komut.KomutTahmini

/**
 * Proje bazındaki ayarlar (spec 009). `workspace.xml`'de — depoya girmez, her
 * geliştiricinin kendi makinesine göre kalır.
 *
 * Altın Kural 6'nın proje karşılığı: proje bazında ayarlanabilir bir değerin tek
 * tanım yeri burasıdır.
 */
@Service(Service.Level.PROJECT)
@State(name = "AcpProjeAyarlari", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class AcpProjeAyarlari(private val project: Project) :
    SimplePersistentStateComponent<AcpProjeAyarlari.State>(State()) {

    class State : BaseState() {
        /** Doğrulama komutu (spec 009 H4). Boş = proje türünden tahmin edilir. */
        var dogrulamaKomutu by string("")

        /**
         * Son seçilen panel kipi (spec 010 T53), `PanelKipi` adı olarak; ilk açılışta
         * Sohbet. Metin: kip adı değişirse eski workspace.xml çökmesin.
         */
        var sonKip by string("SOHBET")
    }

    /** Çalışacak komut: kullanıcınınki, yoksa tahmin. null = tanımsız. */
    fun komut(windows: Boolean = SystemInfo.isWindows): List<String>? =
        state.dogrulamaKomutu?.takeIf { it.isNotBlank() }?.let { KomutSatiri.ayristir(it.trim()) }
            ?: tahmin(windows)

    /** Proje kökündeki dosyalara bakarak tahmin (ilk içerik kökü). */
    fun tahmin(windows: Boolean = SystemInfo.isWindows): List<String>? {
        val kok = ProjeYollari.kokler(project).firstOrNull() ?: return null
        val adlar = kok.children.mapTo(mutableSetOf()) { it.name }
        val pkg = kok.findChild("package.json")?.let { runCatching { VfsUtilCore.loadText(it) }.getOrNull() }
        return KomutTahmini.tahmin(adlar, pkg, windows)
    }

    companion object {
        fun getInstance(project: Project): AcpProjeAyarlari = project.service()
    }
}
