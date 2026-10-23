package dev.acp.settings

import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Ajan ayarları (spec 008 T60, T61).
 *
 * En önemlisi: **varsayılan `ONIZLE`**. `DOGRUDAN`, 006/007'de konan "öneri asla
 * onay olmadan koda yazılmaz" kuralından bilinçli sapma ve kullanıcının açıkça
 * seçmesi gerekiyor.
 */
class AjanAyarlariTest : BasePlatformTestCase() {

    fun testVarsayilanKipOnizle() {
        assertEquals(AjanKipi.ONIZLE, AcpSettings.State().ajanKipi)
    }

    fun testVarsayilanKipOnayGerektiriyor() {
        // Kutudan çıktığı gibi hiçbir dosya onaysız değişmemeli.
        assertFalse(AcpSettings.State().ajanKipi == AjanKipi.DOGRUDAN)
    }

    fun testTaninmayanKipOnizleyeDuser() {
        // Elle düzenlenmiş veya eski sürümden kalmış acp.xml. Çökmek yerine
        // en güvenli tarafa düşülür: kod onay olmadan değişmez.
        val durum = AcpSettings.State().apply { agentUygulamaKipi = "BOYLE_BIR_KIP_YOK" }
        assertEquals(AjanKipi.ONIZLE, durum.ajanKipi)
    }

    fun testBosKipOnizleyeDuser() {
        assertEquals(AjanKipi.ONIZLE, AcpSettings.State().apply { agentUygulamaKipi = "" }.ajanKipi)
    }

    fun testDogrudanKipSecilebiliyor() {
        val durum = AcpSettings.State().apply { agentUygulamaKipi = AjanKipi.DOGRUDAN.name }
        assertEquals(AjanKipi.DOGRUDAN, durum.ajanKipi)
    }

    fun testSayisalVarsayilanlar() {
        val d = AcpSettings.State()
        assertEquals(20, d.agentMaxAdim)
        assertEquals(20, d.agentMaxDosya)
    }

    fun testKipXmlEYaziliyor() {
        // "IDE yeniden başlayınca korunur" kriterinin otomatik karşılığı.
        val xml = JDOMUtil.write(
            serialize(AcpSettings.State().apply { agentUygulamaKipi = "DOGRUDAN" })!!,
        )
        assertTrue("kip yazılmamış: $xml", xml.contains("DOGRUDAN"))
    }

    fun testDokunulmamisAjanAyariXmlSisirmez() {
        // Varsayılan ONIZLE olduğu için temiz kurulumda iz bırakmaz.
        val xml = JDOMUtil.write(serialize(AcpSettings.State().apply { maxTokens = 1 })!!)
        assertFalse("dokunulmamış kip yazılmış: $xml", xml.contains("agentUygulamaKipi"))
    }

    fun testGorunenAdlarTanimli() {
        // Ayar ekranında boş satır görünmemeli.
        AjanKipi.entries.forEach { assertTrue(it.name, it.gorunenAd.isNotBlank()) }
    }
}
