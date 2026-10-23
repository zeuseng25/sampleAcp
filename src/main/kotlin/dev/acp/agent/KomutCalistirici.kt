package dev.acp.agent

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import dev.acp.komut.KomutSonucu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Doğrulama komutunu çalıştırır (spec 009).
 *
 * - **Kabuk yok:** argüman listesi doğrudan sürece gider; `&&`, `|`, `>` anlamsız.
 * - **Proje kökünde başlar**, ortam IDE'ninkidir (kurum vekil ayarları görünür).
 * - **Standart girdi kapalı:** girdi bekleyen komut (izleme kipi) takılmaz, zaman aşımı yakalar.
 * - **Zaman aşımı veya iptalde süreç AĞACI öldürülür** — `mvn`'in başlattığı JVM'ler dahil.
 *   Gradle daemon'ı bilinçli olarak ayrık yaşar; kabul edilen sınır (plan).
 * - **Yakalanan çıktı tavanlı:** baştan ve sondan yarısı; ortası atılır. Modele giden
 *   kısmı `CiktiKirpici` ayrıca kısaltır.
 */
internal class KomutCalistirici(
    private val zamanAsimiMs: Long,
    private val maxCikti: Int = 2_000_000,
    /** IDE ortamının üstüne yazılır; örn. projenin SDK'sı JAVA_HOME olarak (T90 bulgusu). */
    private val ekOrtam: Map<String, String> = emptyMap(),
) : Calistirici {

    override suspend fun calistir(argumanlar: List<String>, kok: Path): KomutSonucu = withContext(Dispatchers.IO) {
        val komut = GeneralCommandLine(listOf(program(argumanlar.first(), kok)) + argumanlar.drop(1))
            .withWorkDirectory(kok.toFile())
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(ekOrtam)
            .withRedirectErrorStream(true)
            .withCharset(CIKTI_KARAKTER_KUMESI)

        val surec = try {
            komut.createProcess()
        } catch (e: ExecutionException) {
            // "error=2" = dosya yok; gerisi (izin yok, iş dizini yok) "bulunamadı" değildir (bulgu 6).
            val mesaj = e.cause?.message ?: e.message.orEmpty()
            // Biçim platforma göre değişiyor: "error=2," (Linux/Windows), "error: 2 (" (macOS).
            return@withContext if (Regex("""error[=:]\s*2\b""").containsMatchIn(mesaj)) {
                KomutSonucu.Bulunamadi(argumanlar.first())
            } else {
                KomutSonucu.Baslatilamadi(argumanlar.first(), mesaj.substringAfterLast(": ").ifBlank { mesaj })
            }
        }
        runCatching { surec.outputStream.close() }

        val cikti = TavanliCikti(maxCikti)
        val okuyucu = thread(isDaemon = true, name = "acp-komut-cikti") {
            runCatching {
                surec.inputStream.reader(CIKTI_KARAKTER_KUMESI).use { r ->
                    val tampon = CharArray(8_192)
                    while (true) {
                        val n = r.read(tampon)
                        if (n < 0) break
                        cikti.ekle(tampon, n)
                    }
                }
            }
        }

        try {
            val bitti = runInterruptible { surec.waitFor(zamanAsimiMs, TimeUnit.MILLISECONDS) }
            if (!bitti) {
                oldur(surec)
                return@withContext KomutSonucu.ZamanAsimi(zamanAsimiMs)
            }
            okuyucu.join(2_000)
            val metin = cikti.metin()
            if (surec.exitValue() == 0) KomutSonucu.Basarili(metin) else KomutSonucu.Basarisiz(surec.exitValue(), metin)
        } finally {
            // İptal (Durdur) dahil her çıkışta: yaşayan süreç bırakılmaz.
            if (surec.isAlive) oldur(surec)
        }
    }

    /**
     * Yalnızca açıkça göreli program (`./mvnw`) ve Windows sarmalayıcıları proje
     * kökünden çözülür. Çıplak ad (`npm`) PATH'tendir: kökteki aynı adlı bir dosya
     * onun yerine geçmemeli — onay metni başka bir şeyi gösterirdi (bulgu 1).
     */
    private fun program(ad: String, kok: Path): String {
        val goreli = ad.removePrefix("./").removePrefix(".\\")
        val kokten = ad != goreli || ad in WINDOWS_SARMALAYICILARI
        return if (kokten && Files.isRegularFile(kok.resolve(goreli))) kok.resolve(goreli).toString() else ad
    }

    private fun oldur(surec: Process) {
        surec.toHandle().descendants().forEach { it.destroyForcibly() }
        surec.destroyForcibly()
    }

    private companion object {
        val WINDOWS_SARMALAYICILARI = setOf("mvnw.cmd", "gradlew.bat")

        /**
         * Alt süreçler borulara işletim sisteminin kodlamasıyla yazar. JDK 18'den beri
         * `defaultCharset()` her yerde UTF-8 (JEP 400); Türkçe Windows'ta (cp1254)
         * derleyici mesajları bozulurdu (bulgu 9).
         */
        val CIKTI_KARAKTER_KUMESI: Charset =
            System.getProperty("native.encoding")?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charset.defaultCharset()
    }

    /** Baştan ve sondan en fazla yarısı kadar tutar. */
    private class TavanliCikti(private val max: Int) {
        private val bas = StringBuilder()
        private val son = StringBuilder()
        private var atildi = false

        @Synchronized
        fun ekle(c: CharArray, n: Int) {
            if (bas.length < max / 2) {
                val sigan = minOf(n, max / 2 - bas.length)
                bas.appendRange(c, 0, sigan)
                if (sigan < n) son.appendRange(c, sigan, n)
            } else {
                son.appendRange(c, 0, n)
            }
            if (son.length > max / 2) {
                son.delete(0, son.length - max / 2)
                atildi = true
            }
        }

        @Synchronized
        fun metin(): String = if (atildi) "$bas\n... (çıktının ortası atıldı)\n$son" else "$bas$son"
    }
}
