package dev.acp.anlama

import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import dev.acp.agent.ProjeYollari
import dev.acp.llm.agent.IliskiSonucu
import dev.acp.llm.agent.SembolIstegi
import dev.acp.llm.agent.Yer

/**
 * [IliskiArayici]'nın IDE uygulaması (spec 010).
 *
 * **Yalnızca `platform.lang` API'leri** — `ReferencesSearch`, `DefinitionsScopedSearch`,
 * `PsiNamedElement`. Dile özgü hiçbir şey yok (Altın Kural 3); her dil kendi
 * referans çözümünü bu API'lere bağlıyor. Deneme (2026-10-22, gerçek IDE): Java,
 * JavaScript ve Kotlin'de aynı kod çalıştı; aynı adlı ilgisiz metotlar ayrıldı.
 *
 * **Test ortamında dil eklentisi yok** (dosyalar düz metin açılıyor), bu yüzden
 * davranış birim testiyle değil, sandbox'taki iç eylemle doğrulanır (T32).
 */
internal class PsiIliskiArayici(private val project: Project) : IliskiArayici {

    override fun hazirMi(): Boolean = !DumbService.isDumb(project)

    override suspend fun kullanimlar(s: SembolIstegi, max: Int): IliskiSonucu = okuma {
        sec(s) { hedef ->
            val yerler = mutableListOf<Yer>()
            var toplam = 0
            ReferencesSearch.search(hedef, GlobalSearchScope.projectScope(project)).forEach(Processor { ref ->
                toplam++
                if (yerler.size < max) yer(ref.element, kendisi = false)?.let { yerler += it }
                // Sınırın çok üstünde saymayı sürdürmek büyük projede dakikalar alır.
                toplam < max * 10
            })
            IliskiSonucu.Bulundu(yerler, toplam)
        }
    }

    override suspend fun uygulamalar(s: SembolIstegi, max: Int): IliskiSonucu = okuma {
        sec(s) { hedef ->
            val yerler = mutableListOf<Yer>()
            var toplam = 0
            DefinitionsScopedSearch.search(hedef, GlobalSearchScope.projectScope(project)).forEach(Processor { tanim ->
                toplam++
                if (yerler.size < max) yer(tanim, kendisi = true)?.let { yerler += it }
                toplam < max * 10
            })
            IliskiSonucu.Bulundu(yerler, toplam)
        }
    }

    override suspend fun tanim(yol: String, satir: Int, ad: String): IliskiSonucu = okuma {
        val dosya = psi(yol) ?: return@okuma IliskiSonucu.Yok("dosya bulunamadı: $yol")
        val belge = PsiDocumentManager.getInstance(project).getDocument(dosya)
            ?: return@okuma IliskiSonucu.Yok("dosya okunamadı: $yol")
        if (satir < 1 || satir > belge.lineCount) {
            return@okuma IliskiSonucu.Yok("$yol dosyasında $satir. satır yok (${belge.lineCount} satır)")
        }
        val bas = belge.getLineStartOffset(satir - 1)
        val metin = belge.charsSequence.subSequence(bas, belge.getLineEndOffset(satir - 1)).toString()

        for (i in gecisler(metin, ad)) {
            val ofset = bas + i
            val ref = dosya.findReferenceAt(ofset)
            val hedefler = when (ref) {
                null -> emptyList()
                is PsiPolyVariantReference -> ref.multiResolve(false).mapNotNull { it.element }
                else -> listOfNotNull(ref.resolve())
            }
            if (hedefler.isNotEmpty()) {
                return@okuma IliskiSonucu.Bulundu(
                    hedefler.mapNotNull { yer(it.navigationElement, kendisi = true) }, hedefler.size,
                )
            }
            // Referans değil, tanımın kendisi olabilir.
            val tanim = PsiTreeUtil.getParentOfType(dosya.findElementAt(ofset), PsiNamedElement::class.java, false)
            if (tanim != null && tanim !is PsiFile && tanim.name == ad) {
                return@okuma IliskiSonucu.Bulundu(listOfNotNull(yer(tanim, kendisi = true)), 1)
            }
        }
        IliskiSonucu.Yok("'$ad' $yol:$satir satırında çözülemedi")
    }

    // ---- iç ----

    /**
     * Dizin hazır VE editördeki değişiklikler PSI'ya işlenmiş olarak okur.
     *
     * Ofsetler belgeden, referanslar PSI'dan geliyor; ikisi ayrışırsa satırlar
     * kayar veya "çözülemedi" döner (gözden geçirme bulgusu 2).
     */
    private suspend fun <T> okuma(blok: () -> T): T = constrainedReadAction(
        ReadConstraint.inSmartMode(project),
        ReadConstraint.withDocumentsCommitted(project),
    ) { blok() }

    private fun psi(yol: String): PsiFile? =
        ProjeYollari.bul(project, yol)?.let { PsiManager.getInstance(project).findFile(it) }

    /** Dosyadaki adlı tanımlar arasından hedefi seçer; belirsizlik sessizce çözülmez (spec H2). */
    private fun sec(s: SembolIstegi, ara: (PsiElement) -> IliskiSonucu): IliskiSonucu {
        val dosya = psi(s.yol) ?: return IliskiSonucu.Yok("dosya bulunamadı: ${s.yol}")
        val tumu = PsiTreeUtil.collectElementsOfType(dosya, PsiNamedElement::class.java)
            .filter { it !is PsiFile && !it.name.isNullOrBlank() }
        val adaylar = tumu.filter { it.name == s.ad }

        if (adaylar.isEmpty()) {
            val benzer = benzerAdlar(s.ad, tumu.mapNotNull { it.name })
            return IliskiSonucu.Yok(
                "'${s.ad}' ${s.yol} dosyasında tanımlı değil" +
                    if (benzer.isEmpty()) "" else ". Benzer: ${benzer.joinToString(", ")}",
            )
        }
        val secilen = if (s.satir != null) adaylar.filter { satirNo(it) == s.satir } else adaylar
        return when (secilen.size) {
            1 -> ara(secilen.single())
            else -> IliskiSonucu.Belirsiz(adaylar.mapNotNull { yer(it, kendisi = true) })
        }
    }

    private fun satirNo(e: PsiElement): Int? {
        val belge = PsiDocumentManager.getInstance(project).getDocument(e.containingFile ?: return null) ?: return null
        return belge.getLineNumber(e.textOffset.coerceIn(0, belge.textLength)) + 1
    }

    /**
     * @param kendisi true: [e]'nin kendi adı kapsayana dahil ("Kalem.calis");
     *   false: [e] bir kullanım, içinde bulunduğu tanım aranır.
     */
    private fun yer(e: PsiElement, kendisi: Boolean): Yer? {
        val dosya = e.containingFile ?: return null
        val vf = dosya.virtualFile ?: return null
        val belge = PsiDocumentManager.getInstance(project).getDocument(dosya) ?: return null
        val satir = belge.getLineNumber(e.textOffset.coerceIn(0, belge.textLength))
        val metin = belge.charsSequence
            .subSequence(belge.getLineStartOffset(satir), belge.getLineEndOffset(satir)).toString()
        return Yer("${ProjeYollari.goreli(project, vf)}:${satir + 1}", kapsayan(e, kendisi), metin)
    }

    /** En yakın iki adlı ata: "Sinif.metot". Dosya düzeyindeyse null. */
    private fun kapsayan(e: PsiElement, kendisi: Boolean): String? {
        val adlar = mutableListOf<String>()
        var p: PsiElement? = if (kendisi) e else e.parent
        while (p != null && p !is PsiFile && adlar.size < 2) {
            // Yerel değişken/parametre "kapsayan" değildir: `int x = a.calis()` → `oku.x`
            // değil `Tuketici.oku` (gözden geçirme bulgusu 4). Ölçüt dilden bağımsız:
            // kapsamı dosyadan DAR olan (bir gövdenin içinde kalan) ad. Dosyaya özel
            // fonksiyonlar (dışa aktarılmamış JS fonksiyonu) kapsayan olarak kalır.
            val yerel = p is PsiNamedElement && p !== e &&
                (p.useScope as? LocalSearchScope)?.scope?.none { it is PsiFile } == true
            if (p is PsiNamedElement && !yerel) p.name?.takeIf { it.isNotBlank() }?.let { adlar += it }
            p = p.parent
        }
        return adlar.takeIf { it.isNotEmpty() }?.reversed()?.joinToString(".")
    }

    private companion object {
        /** [ad]'ın satırdaki tam-kelime geçişlerinin başlangıçları. */
        fun gecisler(satir: String, ad: String): List<Int> {
            if (ad.isEmpty()) return emptyList()
            val sonuc = mutableListOf<Int>()
            var i = satir.indexOf(ad)
            while (i >= 0) {
                val once = satir.getOrNull(i - 1)
                val sonra = satir.getOrNull(i + ad.length)
                if ((once == null || !Character.isJavaIdentifierPart(once)) &&
                    (sonra == null || !Character.isJavaIdentifierPart(sonra))
                ) {
                    sonuc += i
                }
                i = satir.indexOf(ad, i + 1)
            }
            return sonuc
        }

        fun benzerAdlar(ad: String, adlar: List<String>): List<String> {
            val k = ad.lowercase()
            return adlar.distinct()
                .filter { val a = it.lowercase(); a.contains(k) || k.contains(a) || mesafe(a, k) <= 2 }
                .take(5)
        }

        fun mesafe(a: String, b: String): Int {
            val d = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var onceki = d[0]
                d[0] = i
                for (j in 1..b.length) {
                    val gecici = d[j]
                    d[j] = minOf(d[j] + 1, d[j - 1] + 1, onceki + if (a[i - 1] == b[j - 1]) 0 else 1)
                    onceki = gecici
                }
            }
            return d[b.length]
        }
    }
}
