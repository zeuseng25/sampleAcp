package dev.acp.anlama

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.acp.llm.agent.IliskiBicimi
import dev.acp.llm.agent.IliskiSonucu
import dev.acp.llm.agent.SembolIstegi

/**
 * İlişki aramasının **gerçek IDE'de** doğrulanması (spec 010 T32).
 *
 * Neden birim testi değil: test ortamında dil eklentisi yüklü değil, dosyalar
 * düz metin açılıyor; eklemek tuzak #2'ye çarpıyor (AGENTS.md). Bu denetim
 * yalnızca `./gradlew runIde -Pacp.iliskiDenetimi=true` ile açılır: sandbox
 * `src/test/testData/iliski`'yi açar, senaryoları koşar, sonucu `idea.log`'a
 * `ILISKI-DENETIMI` önekiyle yazar ve IDE'yi kapatır. Bayraksız hiçbir şey yapmaz.
 */
internal class IliskiDenetimi : ProjectActivity {

    private val log = logger<IliskiDenetimi>()

    override suspend fun execute(project: Project) {
        if (System.getProperty(BAYRAK) != "true") return
        // Örnek projeye dışarıdan eklenen dosyalar açılışta VFS'te olmayabilir;
        // toplu yenileme yetmedi, her dosya tek tek bulduruluyor.
        project.basePath?.let { kok ->
            java.io.File(kok, "src").walkTopDown().filter { it.isFile }.forEach {
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it)
                    // Sandbox önbelleği kapalıyken eklenen dosyayı dizinlememiş olabilir.
                    ?.let { vf -> com.intellij.util.indexing.FileBasedIndex.getInstance().requestReindex(vf) }
            }
        }
        // Yenilemenin tetiklediği dizinleme biraz sonra başlıyor; önce başlamasına fırsat ver.
        repeat(2) {
            kotlinx.coroutines.delay(2_000)
            DumbService.getInstance(project).waitForSmartMode()
        }

        val a = IliskiArayici.getInstance(project)
        if (a == null) {
            log.warn("ILISKI-DENETIMI KALDI: IliskiArayici kayıtlı değil")
            bitir(0, 1)
            return
        }
        var gecti = 0
        var kaldi = 0
        for ((ad, calistir, beklenen) in senaryolar(a)) {
            val sonuc = runCatching { calistir() }.getOrElse { IliskiSonucu.Yok("İSTİSNA: $it") }
            val metin = IliskiBicimi.metin(sonuc).replace("\n", " | ")
            if (beklenen(sonuc)) {
                gecti++; log.warn("ILISKI-DENETIMI GECTI $ad -> $metin")
            } else {
                kaldi++; log.warn("ILISKI-DENETIMI KALDI $ad -> $metin")
            }
        }
        bitir(gecti, kaldi)
    }

    private fun bitir(gecti: Int, kaldi: Int) {
        log.warn("ILISKI-DENETIMI OZET gecti=$gecti kaldi=$kaldi")
        ApplicationManager.getApplication().invokeLater { ApplicationManager.getApplication().exit(true, true, false) }
    }

    private data class Senaryo(
        val ad: String,
        val calistir: suspend () -> IliskiSonucu,
        val beklenen: (IliskiSonucu) -> Boolean,
    )

    private fun kapsayanlar(s: IliskiSonucu) = (s as? IliskiSonucu.Bulundu)?.yerler?.map { it.kapsayan }.orEmpty()

    private fun senaryolar(a: IliskiArayici) = listOf(
        // Denemedeki ana bulgu: aynı adlı altı geçişten yalnızca gerçek çağıran.
        Senaryo("java-arayuz-kullanimi", { a.kullanimlar(SembolIstegi("src/ornek/Arac.java", "calis"), 40) }) {
            kapsayanlar(it) == listOf("Kullanici.yaz")
        },
        Senaryo("java-uygulama", { a.uygulamalar(SembolIstegi("src/ornek/Arac.java", "calis"), 40) }) {
            kapsayanlar(it) == listOf("Kalem.calis")
        },
        Senaryo("java-somut-kullanim", { a.kullanimlar(SembolIstegi("src/ornek/Kalem.java", "calis"), 40) }) {
            kapsayanlar(it) == listOf("Kullanici.ciz")
        },
        Senaryo("java-ayni-adli-yerel", { a.kullanimlar(SembolIstegi("src/ornek/Kullanici.java", "calis"), 40) }) {
            kapsayanlar(it) == listOf("Kullanici.baska")
        },
        Senaryo("java-tanima-git", { a.tanim("src/ornek/Kullanici.java", 4, "calis") }) {
            (it as? IliskiSonucu.Bulundu)?.yerler?.singleOrNull()?.konum?.startsWith("src/ornek/Arac.java:") == true
        },
        Senaryo("java-belirsiz-asiri-yukleme", { a.kullanimlar(SembolIstegi("src/ornek/Cift.java", "topla"), 40) }) {
            (it as? IliskiSonucu.Belirsiz)?.adaylar?.size == 2
        },
        Senaryo("java-belirsiz-satirla-cozulur", { a.kullanimlar(SembolIstegi("src/ornek/Cift.java", "topla", 5), 40) }) {
            it is IliskiSonucu.Bulundu
        },
        Senaryo("java-yok-benzer-onerir", { a.kullanimlar(SembolIstegi("src/ornek/Arac.java", "cals"), 40) }) {
            it is IliskiSonucu.Yok && "calis" in it.sebep
        },
        Senaryo("js-kullanim", { a.kullanimlar(SembolIstegi("src/util.js", "topla"), 40) }) {
            "hesap" in kapsayanlar(it)
        },
        Senaryo("kotlin-kullanim", { a.kullanimlar(SembolIstegi("src/ornek/Servis.kt", "kaydet"), 40) }) {
            kapsayanlar(it) == listOf("ana")
        },
        // Gözden geçirme bulgusu 4: atamanın içindeki çağrıda kapsayan yerel değişken olmamalı.
        Senaryo("java-atamada-kapsayan", { a.kullanimlar(SembolIstegi("src/ornek/Hesap.java", "deger"), 40) }) {
            kapsayanlar(it) == listOf("Tuketici.oku")
        },
        Senaryo("kotlin-atamada-kapsayan", { a.kullanimlar(SembolIstegi("src/ornek/Depo.kt", "getir"), 40) }) {
            kapsayanlar(it) == listOf("tuket")
        },
    )

    companion object {
        const val BAYRAK = "acp.iliskiDenetimi"
    }
}
