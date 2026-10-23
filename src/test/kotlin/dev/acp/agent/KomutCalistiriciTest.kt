package dev.acp.agent

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.komut.KomutSonucu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.nio.file.Files
import java.nio.file.Path

/**
 * Komut çalıştırıcı (spec 009 T30). Unix'te koşar; Windows'ta atlanır.
 *
 * Testteki `sh -c` yalnızca bir süreç AĞACI kurmak içindir — kullanıcı komutu
 * hiçbir zaman kabuk üzerinden çalışmaz (KomutDenetimi).
 */
class KomutCalistiriciTest : BasePlatformTestCase() {

    private lateinit var kok: Path

    override fun setUp() {
        super.setUp()
        kok = Files.createTempDirectory("acp-komut")
    }

    override fun tearDown() {
        try { kok.toFile().deleteRecursively() } finally { super.tearDown() }
    }

    private fun baslat(arg: List<String>, zamanAsimi: Long = 10_000): Deferred<KomutSonucu> =
        CoroutineScope(Dispatchers.Default).async { KomutCalistirici(zamanAsimi).calistir(arg, kok) }

    private fun kos(arg: List<String>, zamanAsimi: Long = 10_000): KomutSonucu {
        val is_ = baslat(arg, zamanAsimi)
        PlatformTestUtil.waitWithEventsDispatching("komut bitmedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    fun testBasariliVeCiktiProjeKokunde() {
        if (SystemInfo.isWindows) return
        val s = kos(listOf("pwd"))
        assertTrue(s.toString(), s is KomutSonucu.Basarili)
        // macOS: /var → /private/var bağlantısı; iki taraf da gerçek yola çevrilir.
        assertEquals(kok.toRealPath(), Path.of((s as KomutSonucu.Basarili).cikti.trim()).toRealPath())
    }

    fun testBasarisizCikisKodu() {
        if (SystemInfo.isWindows) return
        assertEquals(KomutSonucu.Basarisiz(1, ""), kos(listOf("false")))
    }

    fun testHataCiktisiDaYakalanir() {
        if (SystemInfo.isWindows) return
        val s = kos(listOf("ls", "olmayan-dosya-xyz")) as KomutSonucu.Basarisiz
        assertTrue(s.cikti, "olmayan-dosya-xyz" in s.cikti)
    }

    fun testProgramYoksaBulunamadi() {
        assertEquals(KomutSonucu.Bulunamadi("olmayan-program-xyz"), kos(listOf("olmayan-program-xyz")))
    }

    fun testSarmalayiciProjeKokundenBulunur() {
        if (SystemInfo.isWindows) return
        val betik = kok.resolve("mvnw")
        Files.writeString(betik, "#!/bin/sh\necho sarmalayici\n")
        betik.toFile().setExecutable(true)
        val s = kos(listOf("./mvnw")) as KomutSonucu.Basarili
        assertEquals("sarmalayici", s.cikti.trim())
    }

    fun testKokteAyniAdliDosyaSistemProgramininYerineGecmez() {
        if (SystemInfo.isWindows) return
        // Gözden geçirme bulgusu 1: kökte `echo` adlı çalıştırılabilir dosya.
        val sahte = kok.resolve("echo")
        Files.writeString(sahte, "#!/bin/sh\necho SAHTE\n")
        sahte.toFile().setExecutable(true)
        val s = kos(listOf("echo", "gercek")) as KomutSonucu.Basarili
        assertEquals("gercek", s.cikti.trim())
    }

    fun testCalistirmaIzniYoksaBulunamadiDenmez() {
        if (SystemInfo.isWindows) return
        // Gözden geçirme bulgusu 6: Windows'tan gelmiş, çalıştırma biti olmayan mvnw.
        Files.writeString(kok.resolve("mvnw"), "#!/bin/sh\necho x\n")
        val s = kos(listOf("./mvnw"))
        assertTrue(s.toString(), s is KomutSonucu.Baslatilamadi)
        assertTrue((s as KomutSonucu.Baslatilamadi).sebep.isNotBlank())
    }

    fun testEkOrtamKomutaGecer() {
        if (SystemInfo.isWindows) return
        // T90 bulgusu: proje SDK'sı JAVA_HOME olarak verilmeli (kabuğun JDK'sı değil).
        val is_ = CoroutineScope(Dispatchers.Default).async {
            KomutCalistirici(10_000, ekOrtam = mapOf("JAVA_HOME" to "/proje/jdk")).calistir(listOf("printenv", "JAVA_HOME"), kok)
        }
        PlatformTestUtil.waitWithEventsDispatching("komut bitmedi", { is_.isCompleted }, 20)
        assertEquals("/proje/jdk", (is_.getCompleted() as KomutSonucu.Basarili).cikti.trim())
    }

    fun testZamanAsimindaSurecOldurulur() {
        if (SystemInfo.isWindows) return
        val bas = System.currentTimeMillis()
        assertEquals(KomutSonucu.ZamanAsimi(500), kos(listOf("sleep", "30"), zamanAsimi = 500))
        assertTrue("zaman aşımı beklemedi", System.currentTimeMillis() - bas < 5_000)
    }

    fun testIptaldeAltSurecDeOlur() {
        if (SystemInfo.isWindows) return
        val pidDosyasi = kok.resolve("pid")
        val is_ = baslat(listOf("sh", "-c", "sleep 30 & echo $! > ${pidDosyasi}; wait"))
        PlatformTestUtil.waitWithEventsDispatching("alt süreç başlamadı", { Files.exists(pidDosyasi) && Files.size(pidDosyasi) > 0 }, 10)
        val pid = Files.readString(pidDosyasi).trim().toLong()
        assertTrue(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))

        is_.cancel()
        PlatformTestUtil.waitWithEventsDispatching("alt süreç yaşıyor", {
            !ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
        }, 10)
    }

    fun testDevCiktiBellegiDoldurmaz() {
        if (SystemInfo.isWindows) return
        // ~20 MB çıktı; yakalama sınırı (2 MB) aşılmamalı, son satır korunmalı.
        val s = kos(listOf("sh", "-c", "yes satir | head -n 2000000; echo SON_SATIR")) as KomutSonucu.Basarili
        assertTrue("yakalama sınırı aşıldı: ${s.cikti.length}", s.cikti.length <= 2_100_000)
        assertTrue("son satır kayboldu", s.cikti.trimEnd().endsWith("SON_SATIR"))
    }
}
