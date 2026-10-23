package dev.acp.settings

import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 001/T21 — H1: "Ayarlar IDE yeniden başlatıldığında korunur" ve
 * "hiçbiri koda gömülü değildir".
 */
class AcpSettingsTest : BasePlatformTestCase() {

    fun testSunucuVeModelVarsayilaniBostur() {
        // Altın Kural 6: kuruma özgü bilgi pakete gömülmez.
        val durum = AcpSettings.State()
        assertEquals("", durum.baseUrl)
        assertEquals("", durum.model)
    }

    fun testAyarlanabilirDegerlerinMakulVarsayilanlariVar() {
        val durum = AcpSettings.State()
        assertEquals(8000, durum.contextTokenBudget)
        assertEquals(2048, durum.maxTokens)
        assertEquals(200, durum.temperatureMilli)
        assertEquals(10_000, durum.connectTimeoutMs)
        assertEquals(180_000, durum.readTimeoutMs)
        assertFalse(durum.enableThinking)
    }

    fun testSicaklikBindeDegerdenOndalikliyaCevrilir() {
        // Binde tamsayı saklıyoruz: yerel ondalık ayracından bağımsız olsun diye.
        val durum = AcpSettings.State().apply { temperatureMilli = 250 }
        assertEquals(0.25, durum.temperature, 0.0001)
    }

    fun testServisAlinabiliyor() {
        assertNotNull(AcpSettings.getInstance())
    }

    fun testDegerlerXmlEYaziliyor() {
        // "IDE yeniden başlayınca korunur" kriterinin otomatik karşılığı:
        // değerlerin gerçekten serileştirildiğini doğruluyoruz.
        val durum = AcpSettings.State().apply {
            baseUrl = "http://sunucu:8000"
            model = "qwen-coder"
            contextTokenBudget = 12_000
        }
        val xml = JDOMUtil.write(serialize(durum)!!)
        assertTrue("baseUrl yazılmamış: $xml", xml.contains("http://sunucu:8000"))
        assertTrue("model yazılmamış: $xml", xml.contains("qwen-coder"))
        assertTrue("bütçe yazılmamış: $xml", xml.contains("12000"))
    }

    fun testTamamlamaAyarlariXmlEYaziliyor() {
        // 006/H3: "Açık/kapalı durumu IDE yeniden başlatıldığında korunur."
        val durum = AcpSettings.State().apply {
            completionFormat = "QWEN"
            completionModel = "kucuk-coder"
            completionDebounceMs = 450
        }
        val xml = JDOMUtil.write(serialize(durum)!!)
        assertTrue("biçim yazılmamış: $xml", xml.contains("QWEN"))
        assertTrue("tamamlama modeli yazılmamış: $xml", xml.contains("kucuk-coder"))
        assertTrue("gecikme yazılmamış: $xml", xml.contains("450"))
    }

    fun testTamamlamaVarsayilanKapaliVeXmlEYazilmaz() {
        // Varsayılan KAPALI olduğu için dokunulmamış kurulumda hiç iz bırakmaz.
        assertEquals("KAPALI", AcpSettings.State().completionFormat)
        val xml = serialize(AcpSettings.State().apply { maxTokens = 1 })?.let { JDOMUtil.write(it) }
        assertFalse("dokunulmamış tamamlama ayarı yazılmış: $xml", xml!!.contains("completionFormat"))
    }

    fun testVarsayilanKalanAlanlarXmlSisirmez() {
        // BaseState yalnızca varsayılandan farklı alanları yazar; hepsi varsayılansa
        // hiç eleman üretmez. Yani acp.xml gereksiz yere şişmez ve varsayılanı
        // ileride değiştirmek eski kurulumları da etkiler.
        assertNull(serialize(AcpSettings.State()))
    }

    fun testSadeceDegistirilenAlanYazilir() {
        val xml = JDOMUtil.write(serialize(AcpSettings.State().apply { maxTokens = 999 })!!)
        assertTrue("değiştirilen alan yok: $xml", xml.contains("999"))
        assertFalse("dokunulmamış alan da yazılmış: $xml", xml.contains("contextTokenBudget"))
    }

    fun testYapilandirilmamisDurumAnlasiliyor() {
        // H1 son kriter: hiçbir ayar girilmemişken eklenti çökmez.
        assertFalse(AcpSettings.State().yapilandirildi())
        assertFalse(AcpSettings.State().apply { baseUrl = "http://h" }.yapilandirildi())
        assertTrue(
            AcpSettings.State().apply { baseUrl = "http://h"; model = "m" }.yapilandirildi()
        )
    }
}
