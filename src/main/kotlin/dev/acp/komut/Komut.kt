package dev.acp.komut

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path

/**
 * Doğrulama komutunun saf katmanı (spec 009).
 *
 * **Saf:** `com.intellij` importu yok (ArchitectureTest). Komutu model seçmez;
 * proje türünden tahmin edilir veya kullanıcı yazar, burada denetlenir.
 */

internal sealed interface KomutSonucu {
    data class Basarili(val cikti: String) : KomutSonucu
    data class Basarisiz(val cikisKodu: Int, val cikti: String) : KomutSonucu
    data class ZamanAsimi(val sureMs: Long) : KomutSonucu
    data class Bulunamadi(val program: String) : KomutSonucu
    /** Program var ama başlatılamadı (çalıştırma izni yok vb.) — bulunamadı değil. */
    data class Baslatilamadi(val program: String, val sebep: String) : KomutSonucu
    /** Komut tahmin edilemedi ve kullanıcı tanımlamadı. */
    data object Tanimsiz : KomutSonucu
    data class Reddedildi(val sebep: String) : KomutSonucu
    data object Atlandi : KomutSonucu
    data object Durduruldu : KomutSonucu
}

/** Proje kökündeki dosyalardan komut tahmini; ilk eşleşen kazanır (plan tablosu). */
internal object KomutTahmini {

    private val mapper = ObjectMapper()

    fun tahmin(kokDosyalari: Set<String>, packageJson: String?, windows: Boolean): List<String>? {
        fun w(unix: String, win: String) = if (windows) win else unix
        return when {
            // Sarmalayıcı, projenin istediği araç sürümünü getirir.
            "mvnw" in kokDosyalari -> listOf(w("./mvnw", "mvnw.cmd"), "-q", "compile")
            "pom.xml" in kokDosyalari -> listOf(w("mvn", "mvn.cmd"), "-q", "compile")
            "gradlew" in kokDosyalari -> listOf(w("./gradlew", "gradlew.bat"), "build", "-q")
            "build.gradle" in kokDosyalari || "build.gradle.kts" in kokDosyalari ->
                listOf(w("gradle", "gradle.bat"), "build", "-q")
            "package.json" in kokDosyalari -> {
                val betikler = runCatching { mapper.readTree(packageJson.orEmpty()).path("scripts") }.getOrNull()
                val npm = w("npm", "npm.cmd")
                when {
                    betikler?.has("build") == true -> listOf(npm, "run", "build")
                    betikler?.has("test") == true -> listOf(npm, "test")
                    else -> null
                }
            }
            else -> null
        }
    }
}

/** Kullanıcının yazdığı komutu argümanlara böler; tek ve çift tırnak gruplar. */
internal object KomutSatiri {

    fun ayristir(metin: String): List<String> {
        val sonuc = mutableListOf<String>()
        val parca = StringBuilder()
        var tirnak: Char? = null
        var parcaVar = false
        for (c in metin) {
            when {
                tirnak != null && c == tirnak -> tirnak = null
                tirnak != null -> parca.append(c)
                c == '"' || c == '\'' -> { tirnak = c; parcaVar = true }
                c.isWhitespace() -> if (parcaVar || parca.isNotEmpty()) {
                    sonuc += parca.toString(); parca.setLength(0); parcaVar = false
                }
                else -> parca.append(c)
            }
        }
        if (parcaVar || parca.isNotEmpty()) sonuc += parca.toString()
        return sonuc
    }
}

/**
 * Komut proje sınırının dışına çıkamaz (spec 009 H3).
 *
 * Kabuk kullanılmadığı için `&&`, `|`, `>` zaten çalışmaz; yine de reddedilir ki
 * kullanıcı neden çalışmadığını bilsin. Sınır: başlangıç dizini ve **verilen
 * yollar**. Derleme aracının kendi yazdığı yerler (paket deposu) kapsam dışı
 * (spec kararı).
 */
internal object KomutDenetimi {

    private val kabukIsaretleri = listOf("&&", "||", ";", "|", ">", "<", "`", "$(")

    /** null = geçerli; değilse kullanıcıya gösterilecek sebep. */
    fun denetle(argumanlar: List<String>, kok: Path): String? {
        if (argumanlar.isEmpty() || argumanlar.first().isBlank()) return "komut boş"
        val k = kok.toAbsolutePath().normalize()
        for (a in argumanlar) {
            kabukIsaretleri.firstOrNull { it in a }?.let {
                return "kabuk işareti kullanılamaz ('$it'); komut kabuk üzerinden çalışmaz"
            }
            if (a.split('/', '\\').any { it == ".." }) return "proje dışına çıkan yol: $a"
            if (mutlakMi(a)) {
                val yol = runCatching { Path.of(a).toAbsolutePath().normalize() }.getOrNull()
                if (yol == null || !yol.startsWith(k)) return "proje dizini dışı yol: $a"
            }
        }
        return null
    }

    private fun mutlakMi(a: String) =
        a.startsWith("/") || a.startsWith("\\") || Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(a)
}

/**
 * Modele giden çıktı (spec 009): hata satırları korunur, gerisi sondan.
 *
 * Derleme çıktısı on binlerce satır olabilir; tamamı bağlamı doldurur. Hata
 * satırı çoğu zaman ortadadır (Maven özeti sondadır, asıl hata yukarıda).
 */
internal object CiktiKirpici {

    private val hataDeseni = Regex(
        "(?i)(\\berror\\b|\\[error]|\\bfail|exception|^e: |cannot find|unresolved|undefined|not found|\\bwarning:)",
    )

    fun kirp(cikti: String, maxKarakter: Int = 8_000): String {
        if (cikti.length <= maxKarakter) return cikti
        val satirlar = cikti.lines()
        val baslik = "... (çıktı kırpıldı; ${satirlar.size} satırın hata satırları ve sonu)\n"
        val butce = maxKarakter - baslik.length

        val hatalar = StringBuilder()
        for (s in satirlar) {
            if (!hataDeseni.containsMatchIn(s)) continue
            // Sığmayan satır atlanır, sonrakiler denenir (bulgu 10).
            if (hatalar.length + s.length + 1 > butce / 2) continue
            hatalar.append(s).append('\n')
        }
        val ayrac = if (hatalar.isEmpty()) "" else "--- son satırlar ---\n"
        val kalan = butce - hatalar.length - ayrac.length
        val son = StringBuilder()
        for (s in satirlar.asReversed()) {
            if (son.length + s.length + 1 > kalan) {
                // Tek dev satır (küçültülmüş JS) hiç sığmasa bile sonundan bir parça gelsin.
                if (son.isEmpty()) son.append(s.takeLast(kalan))
                break
            }
            son.insert(0, s + "\n")
        }
        return (baslik + hatalar + ayrac + son).trimEnd().take(maxKarakter)
    }
}
