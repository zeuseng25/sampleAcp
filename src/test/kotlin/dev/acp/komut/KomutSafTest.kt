package dev.acp.komut

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec 009 saf katmanı: tahmin, ayrıştırma, denetim, kırpma (T10-T13). */
class KomutSafTest {

    // ---- T10: tahmin ----

    private fun tahmin(vararg dosya: String, pkg: String? = null, win: Boolean = false) =
        KomutTahmini.tahmin(dosya.toSet(), pkg, win)

    @Test
    fun `sarmalayici varsa o secilir`() {
        assertEquals(listOf("./mvnw", "-q", "compile"), tahmin("mvnw", "pom.xml"))
        assertEquals(listOf("./gradlew", "build", "-q"), tahmin("gradlew", "build.gradle.kts"))
    }

    @Test
    fun `sarmalayici yoksa arac`() {
        assertEquals(listOf("mvn", "-q", "compile"), tahmin("pom.xml"))
        assertEquals(listOf("gradle", "build", "-q"), tahmin("build.gradle"))
        assertEquals(listOf("gradle", "build", "-q"), tahmin("build.gradle.kts"))
    }

    @Test
    fun `npm build varsa build yoksa test`() {
        assertEquals(listOf("npm", "run", "build"), tahmin("package.json", pkg = """{"scripts":{"build":"tsc","test":"jest"}}"""))
        assertEquals(listOf("npm", "test"), tahmin("package.json", pkg = """{"scripts":{"test":"jest"}}"""))
        assertNull(tahmin("package.json", pkg = """{"name":"x"}"""))
        assertNull(tahmin("package.json", pkg = "bozuk json{"))
    }

    @Test
    fun `maven gradle'dan once`() {
        assertEquals(listOf("mvn", "-q", "compile"), tahmin("pom.xml", "build.gradle"))
    }

    @Test
    fun `windows karsiliklari`() {
        assertEquals(listOf("mvnw.cmd", "-q", "compile"), tahmin("mvnw", win = true))
        assertEquals(listOf("mvn.cmd", "-q", "compile"), tahmin("pom.xml", win = true))
        assertEquals(listOf("gradlew.bat", "build", "-q"), tahmin("gradlew", win = true))
        assertEquals(listOf("gradle.bat", "build", "-q"), tahmin("build.gradle", win = true))
        assertEquals(listOf("npm.cmd", "test"), tahmin("package.json", pkg = """{"scripts":{"test":"x"}}""", win = true))
    }

    @Test
    fun `hicbiri yoksa tanimsiz`() {
        assertNull(tahmin("README.md", "src"))
    }

    // ---- T11: ayrıştırma ----

    @Test
    fun `bosluk ve tirnak`() {
        assertEquals(listOf("mvn", "-q", "a b", "c d"), KomutSatiri.ayristir("""mvn  -q "a b" 'c d'"""))
        assertEquals(emptyList(), KomutSatiri.ayristir("   "))
    }

    // ---- T12: denetim ----

    private val kok = Path.of("/proje")

    @Test
    fun `gecerli komutlar`() {
        assertNull(KomutDenetimi.denetle(listOf("mvn", "-q", "compile"), kok))
        assertNull(KomutDenetimi.denetle(listOf("./gradlew", "test", "--tests", "*Foo*"), kok))
        assertNull(KomutDenetimi.denetle(listOf("npm", "run", "build", "/proje/alt"), kok))
    }

    @Test
    fun `red sebepleri`() {
        assertNotNull(KomutDenetimi.denetle(emptyList(), kok))
        for (kotu in listOf("&&", "||", ";", "|", ">", "<", "`", "$(")) {
            val sebep = KomutDenetimi.denetle(listOf("mvn", "a${kotu}b"), kok)
            assertTrue(sebep != null && "kabuk" in sebep, "'$kotu' geçti: $sebep")
        }
        assertTrue("dışı" in KomutDenetimi.denetle(listOf("mvn", "../baska"), kok)!!)
        assertTrue("dışı" in KomutDenetimi.denetle(listOf("cat", "/etc/passwd"), kok)!!)
        assertTrue("dışı" in KomutDenetimi.denetle(listOf("/bin/rm", "x"), kok)!!)
        assertTrue("dışı" in KomutDenetimi.denetle(listOf("mvn", "/proje/../etc"), kok)!!)
    }

    // ---- T13: kırpma ----

    @Test
    fun `tek uzun satir neredeyse bos donmez`() {
        // Gözden geçirme bulgusu 10: küçültülmüş JS gibi tek dev satır.
        val k = CiktiKirpici.kirp("x".repeat(20_000), 2_000)
        assertTrue(k.length in 1_000..2_000, "kırpılmış çıktı: ${k.length}")
    }

    @Test
    fun `sigmayan ilk hata satirindan sonrakiler kaybolmaz`() {
        val cikti = listOf("ERROR " + "y".repeat(5_000)) + (1..500).map { "bilgi $it" } + "ERROR kisa-ikinci-hata" + (1..500).map { "son $it" }
        val k = CiktiKirpici.kirp(cikti.joinToString("\n"), 2_000)
        assertTrue("kisa-ikinci-hata" in k, "sonraki hata satırı kayboldu")
    }

    @Test
    fun `kisa cikti aynen`() {
        assertEquals("tamam", CiktiKirpici.kirp("tamam", 100))
    }

    @Test
    fun `ortadaki hata satiri korunur sinir asilmaz`() {
        val satirlar = (1..2000).map { "bilgi satiri $it" }.toMutableList()
        satirlar[700] = "[ERROR] Foo.java:[12,5] cannot find symbol"
        val k = CiktiKirpici.kirp(satirlar.joinToString("\n"), 2_000)
        assertTrue("cannot find symbol" in k, "hata satırı kayboldu")
        assertTrue("bilgi satiri 2000" in k, "son satırlar kayboldu")
        assertTrue(k.length <= 2_000, "sınır aşıldı: ${k.length}")
        assertTrue("kırpıldı" in k)
    }
}
