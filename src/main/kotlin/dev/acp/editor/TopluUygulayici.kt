package dev.acp.editor

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.acp.llm.agent.DuzenlemePlani

/** Tek bir dosyanın hesaplanmış sonucu. */
internal data class DosyaSonucu(
    val yol: String,
    val dosya: VirtualFile,
    val eskiIcerik: String,
    val yeniIcerik: String,
    val degisiklikSayisi: Int,
)

/**
 * Uygulanmaya hazır toplu sonuç.
 *
 * [atlananlar] boş değilse kullanıcıya **gösterilmelidir** (spec 008 T51):
 * sessizce yutulursa işin eksik uygulandığı fark edilmez.
 */
internal data class TopluSonuc(
    val dosyalar: List<DosyaSonucu>,
    val atlananlar: List<String>,
) {
    val bosMu: Boolean get() = dosyalar.isEmpty()
    val toplamDegisiklik: Int get() = dosyalar.sumOf { it.degisiklikSayisi }
}

/**
 * Düzenleme planını dosyalara uygular (spec 008).
 *
 * Üç aşama ve aralarındaki sınır önemli:
 *
 * 1. [hesapla] — yeni içerikleri **bellekte** üretir, hiçbir şeye dokunmaz
 * 2. [onizle] — kullanıcıya gösterir
 * 3. [uygula] — hepsini **tek işlemde** yazar
 *
 * Hesaplama uygulamadan tamamen önce bitiyor. Yarısı uygulanmış çok dosyalı bir
 * değişiklik, hiç uygulanmamış olmasından kötüdür: kod derlenmez ve nerede
 * kalındığı belli olmaz.
 */
internal object TopluUygulayici {

    /**
     * Planı yeni dosya içeriklerine çevirir. **Hiçbir dosyaya yazmaz.**
     *
     * Uygulanamayan düzenleme sessizce atılmaz, [TopluSonuc.atlananlar] içinde
     * bildirilir.
     */
    suspend fun hesapla(project: Project, plan: DuzenlemePlani): TopluSonuc = readAction {
        val sonuclar = mutableListOf<DosyaSonucu>()
        val atlananlar = mutableListOf<String>()

        for ((yol, duzenlemeler) in plan.dosyaBazinda()) {
            val dosya = bul(project, yol)
            if (dosya == null) {
                atlananlar += "$yol — dosya bulunamadı"
                continue
            }
            val eski: String = runCatching { VfsUtilCore.loadText(dosya) }.getOrNull() ?: run {
                atlananlar += "$yol — okunamadı"
                null
            } ?: continue

            var icerik: String = eski
            var uygulanan = 0
            for (d in duzenlemeler) {
                when {
                    d.eski !in icerik ->
                        // Önceki bir düzenleme metni değiştirmiş veya model
                        // birebir olmayan metin vermiş olabilir.
                        atlananlar += "$yol — aranan metin bulunamadı: ${kisalt(d.eski)}"
                    else -> {
                        icerik = icerik.replaceFirst(d.eski, d.yeni)
                        uygulanan++
                    }
                }
            }

            if (uygulanan > 0) {
                sonuclar += DosyaSonucu(yol, dosya, eski, icerik, uygulanan)
            }
        }
        TopluSonuc(sonuclar, atlananlar)
    }

    /**
     * Çok dosyalı diff önizlemesi — **ayrı pencerede** (kipsiz çerçeve).
     *
     * İlk sürüm editör sekmesi kullanıyordu; kullanıcı 003'teki tek dosyalık
     * "Göster" gibi pencere bekledi (009 T90, 2026-10-23). Çerçeve kipsiz: açıkken
     * projede gezinilebilir. Zincirin ileri/geri gezinmesi dosyalar arasında geçer.
     *
     * Başlıklara değişiklik sayısı yazılıyor: on dosyalık bir listede hangi
     * dosyada ne kadar iş olduğunu görmek, yalnızca yolları görmekten iyi.
     *
     * ⚠️ Bu, solda dosya ağacı olan gerçek bir "değişiklikler" görünümü değil.
     * O görünüm (`SimpleChangesBrowser`) VCS modülünde ve eklenti yalnızca
     * platform'a bağlı; almak için VCS'e isteğe bağlı bağımlılık gerekir.
     *
     * EDT'de çağrılmalıdır.
     */
    fun onizle(
        project: Project,
        sonuc: TopluSonuc,
        /** Açılan pencere; uygulanınca bayat kalır, çağıran kapatır (003'teki gibi). */
        pencereAlindi: (com.intellij.openapi.ui.WindowWrapper) -> Unit = {},
    ): Boolean {
        if (sonuc.bosMu) return false
        val fabrika = DiffContentFactory.getInstance()

        val istekler = sonuc.dosyalar.map { d ->
            SimpleDiffRequest(
                "${d.yol}  —  ${d.degisiklikSayisi} değişiklik",
                fabrika.create(project, d.eskiIcerik, d.dosya.fileType),
                fabrika.create(project, d.yeniIcerik, d.dosya.fileType),
                "Mevcut",
                "Önerilen",
            )
        }

        val zincir = SimpleDiffRequestChain(istekler)
        // Ayrı pencere, editör sekmesi değil: 003'teki tek dosyalık "Göster" ile aynı
        // davranış; sekme kod alanını kaplıyordu (T90 kullanıcı bulgusu, 2026-10-23).
        com.intellij.diff.DiffManager.getInstance().showDiff(
            project,
            zincir,
            com.intellij.diff.DiffDialogHints(com.intellij.openapi.ui.WindowWrapper.Mode.FRAME, null, pencereAlindi),
        )
        return true
    }

    /**
     * Hepsini **tek geri alma adımında** uygular.
     *
     * @return uygulandıysa null; bir dosya bu arada değiştiyse hata metni
     *   (ve **hiçbir dosyaya dokunulmaz**).
     *
     * Bayatlık kontrolü uygulamadan hemen önce yapılıyor: [hesapla] ile [uygula]
     * arasında kullanıcı veya başka bir araç dosyayı değiştirmiş olabilir.
     * Böyle bir durumda kısmen uygulamak yerine **hiç uygulamıyoruz** —
     * "ya tamamen ya hiç".
     *
     * EDT'de, yazma erişimi olmadan çağrılmalıdır.
     */
    fun uygula(project: Project, sonuc: TopluSonuc, komutAdi: String): String? {
        if (sonuc.bosMu) return null

        val yonetici = FileDocumentManager.getInstance()

        // 1) Bayatlık: hepsini yazmadan ÖNCE kontrol et.
        for (d in sonuc.dosyalar) {
            val belge = yonetici.getDocument(d.dosya) ?: return "${d.yol} — belge açılamadı"
            if (belge.text != d.eskiIcerik) {
                return "${d.yol} — dosya bu arada değişti. Hiçbir değişiklik uygulanmadı."
            }
        }

        // 2) Tek komut → tek geri alma adımı, kaç dosya olursa olsun.
        WriteCommandAction.writeCommandAction(project)
            .withName(komutAdi)
            .withGroupId(GRUP)
            .run<RuntimeException> {
                for (d in sonuc.dosyalar) {
                    yonetici.getDocument(d.dosya)?.setText(d.yeniIcerik)
                }
            }
        return null
    }

    // ---- yardımcılar ----

    private fun bul(project: Project, yol: String): VirtualFile? {
        if (yol.split('/', '\\').any { it == ".." }) return null
        for (kok in ProjectRootManager.getInstance(project).contentRoots) {
            val hedef = kok.findFileByRelativePath(yol) ?: continue
            if (hedef.path.startsWith(kok.path) && !hedef.isDirectory) return hedef
        }
        return null
    }

    private fun kisalt(metin: String): String =
        metin.replace(Regex("\\s+"), " ").let { if (it.length > 50) it.take(47) + "…" else it }

    /** Geri almanın tek adımda toplanmasını sağlayan kimlik. */
    private const val GRUP = "acp.toplu.duzenleme"

}
