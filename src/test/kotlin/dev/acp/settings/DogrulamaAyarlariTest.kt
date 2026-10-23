package dev.acp.settings

import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Doğrulama komutu ayarları (spec 009 T20). */
class DogrulamaAyarlariTest : BasePlatformTestCase() {

    fun testZamanAsimiVarsayilani() {
        assertEquals(300_000, AcpSettings.State().agentKomutZamanAsimiMs)
    }

    fun testProjeAyariVarsayilanBos() {
        // Boş = tahmin edilir.
        assertEquals("", AcpProjeAyarlari.State().dogrulamaKomutu.orEmpty())
    }

    fun testProjeAyariProjeBasinaVeKalici() {
        // "IDE yeniden başlatıldığında korunur" kriterinin otomatik karşılığı.
        val ayar = AcpProjeAyarlari.getInstance(project)
        assertSame(ayar, AcpProjeAyarlari.getInstance(project))
        val xml = JDOMUtil.write(serialize(AcpProjeAyarlari.State().apply { dogrulamaKomutu = "mvn -q test" })!!)
        assertTrue(xml, "mvn -q test" in xml)
    }

    fun testTahminProjeKokundenYapilir() {
        myFixture.addFileToProject("pom.xml", "<project/>")
        assertEquals(listOf("mvn", "-q", "compile"), AcpProjeAyarlari.getInstance(project).tahmin(windows = false))
    }

    fun testKullaniciKomutuTahmindenOnce() {
        myFixture.addFileToProject("pom.xml", "<project/>")
        val ayar = AcpProjeAyarlari.getInstance(project)
        try {
            ayar.state.dogrulamaKomutu = "  mvn -q test  "
            assertEquals(listOf("mvn", "-q", "test"), ayar.komut(windows = false))
            ayar.state.dogrulamaKomutu = ""
            assertEquals(listOf("mvn", "-q", "compile"), ayar.komut(windows = false))
        } finally {
            ayar.state.dogrulamaKomutu = ""
        }
    }
}
