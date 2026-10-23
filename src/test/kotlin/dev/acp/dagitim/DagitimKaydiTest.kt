package dev.acp.dagitim

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dağıtım kaydı paketten üretilir (spec 005 T20-T21).
 *
 * Gradle'ın `dagitimKaydiOrnek` görevinin GERÇEK çıktısını okur — test görevi ona
 * bağlı. Kaynak, pakete giren yamalı `plugin.xml`; böylece kayıt ile paket
 * ayrışamaz (spec Davranış Kuralı 3). Adres örnek: kurum adresi koda gömülmez.
 */
class DagitimKaydiTest {

    private val kayit = File(System.getProperty("acp.dagitimKaydi") ?: error("acp.dagitimKaydi verilmedi"))
    private val belge by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(kayit).documentElement
    }
    private val eklenti by lazy { belge.getElementsByTagName("plugin").item(0) as org.w3c.dom.Element }

    @Test
    fun `gecerli xml ve tek eklenti`() {
        assertEquals("plugins", belge.tagName)
        assertEquals(1, belge.getElementsByTagName("plugin").length)
    }

    @Test
    fun `kimlik asla degismez`() {
        assertEquals("dev.acp.assistant", eklenti.getAttribute("id"))
    }

    @Test
    fun `surum gradle properties ten gelir`() {
        // T21: sürüm gradle.properties'te değişince kayıtta da değişir.
        val p = Properties().apply { File("gradle.properties").reader(Charsets.UTF_8).use { load(it) } }
        val surum = p.getProperty("version") ?: error("gradle.properties'te version yok")
        assertEquals(surum, eklenti.getAttribute("version"))
    }

    @Test
    fun `paket adresi kok artı zip adi`() {
        val url = eklenti.getAttribute("url")
        assertTrue(url.startsWith("https://depo.ornek.invalid/acp/"), url)
        assertTrue(url.endsWith("-${eklenti.getAttribute("version")}.zip"), url)
    }

    @Test
    fun `uyumlu ide araligi acik uclu`() {
        val ide = eklenti.getElementsByTagName("idea-version").item(0) as org.w3c.dom.Element
        assertEquals("261", ide.getAttribute("since-build"))
        assertTrue(ide.getAttribute("until-build").isEmpty(), "until-build yazılmış: kayıt yeni IDE'leri dışlar")
    }

    @Test
    fun `gorunen ad pakettekiyle ayni`() {
        val p = Properties().apply { File("gradle.properties").reader(Charsets.UTF_8).use { load(it) } }
        assertEquals(p.getProperty("acp.displayName"), eklenti.getElementsByTagName("name").item(0)?.textContent)
    }

    @Test
    fun `kimlik bilgisi kayda sizmaz`() {
        val metin = kayit.readText()
        assertNull(listOf("ACP_REPO_TOKEN", "ACP_REPO_USER", "password", "token").firstOrNull { it in metin })
    }
}
