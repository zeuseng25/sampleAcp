package dev.acp.llm.agent

/**
 * IDE'nin anlam dizininden gelen ilişkiler (spec 010).
 *
 * Saf: sonucu IDE üretir, metne çevirmek burada — IDE'siz test edilebilsin.
 */
internal data class SembolIstegi(val yol: String, val ad: String, val satir: Int? = null)

/**
 * @property konum `yol:satir` (proje köküne göreli)
 * @property kapsayan içinde bulunduğu adlı eleman ("Kullanici.yaz"); dosya düzeyindeyse null
 */
internal data class Yer(val konum: String, val kapsayan: String?, val satirMetni: String)

internal sealed interface IliskiSonucu {
    data class Bulundu(val yerler: List<Yer>, val toplam: Int) : IliskiSonucu
    /** Aynı adla birden fazla aday; biri sessizce seçilmez (spec H2). */
    data class Belirsiz(val adaylar: List<Yer>) : IliskiSonucu
    data class Yok(val sebep: String) : IliskiSonucu
}

internal object IliskiBicimi {

    private const val SATIR_SINIRI = 160

    fun metin(s: IliskiSonucu): String = when (s) {
        is IliskiSonucu.Bulundu -> {
            if (s.yerler.isEmpty()) {
                "kullanım yok"
            } else {
                val fazla = s.toplam - s.yerler.size
                s.yerler.joinToString("\n", transform = ::satir) +
                    if (fazla > 0) "\n... ($fazla yer daha)" else ""
            }
        }
        is IliskiSonucu.Belirsiz ->
            "BELİRSİZ: bu adla birden fazla tanım var. Hangisini kastettiğini 'satir' ile belirt:\n" +
                s.adaylar.joinToString("\n", transform = ::satir)
        is IliskiSonucu.Yok -> "HATA: ${s.sebep}"
    }

    private fun satir(y: Yer): String {
        val m = y.satirMetni.trim().let { if (it.length > SATIR_SINIRI) it.take(SATIR_SINIRI - 1) + "…" else it }
        return "${y.konum} [${y.kapsayan ?: "dosya düzeyi"}] $m"
    }
}
