package dev.acp.settings

import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 001/T22 — H1: "Erişim anahtarı işletim sisteminin güvenli saklama alanında
 * tutulur; ayar dosyasında düz metin olarak bulunmaz."
 */
class AcpSecretsTest : BasePlatformTestCase() {

    private val sunucuA = "http://sunucu-a:8000"
    private val sunucuB = "http://sunucu-b:8000"

    override fun tearDown() {
        try {
            AcpSecrets.setApiKey(sunucuA, null)
            AcpSecrets.setApiKey(sunucuB, null)
        } finally {
            super.tearDown()
        }
    }

    fun testAnahtarYazilipOkunabiliyor() {
        AcpSecrets.setApiKey(sunucuA, "gizli-anahtar-123")
        assertEquals("gizli-anahtar-123", AcpSecrets.getApiKey(sunucuA))
    }

    fun testHicYazilmamisAnahtarNullDoner() {
        assertNull(AcpSecrets.getApiKey("http://hic-kullanilmamis:9999"))
    }

    fun testFarkliSunucularinAnahtarlariKarismaz() {
        // İki sunucu arasında geçişte her ikisinin anahtarı da korunmalı.
        AcpSecrets.setApiKey(sunucuA, "anahtar-A")
        AcpSecrets.setApiKey(sunucuB, "anahtar-B")
        assertEquals("anahtar-A", AcpSecrets.getApiKey(sunucuA))
        assertEquals("anahtar-B", AcpSecrets.getApiKey(sunucuB))
    }

    fun testNullYazmakAnahtariSiler() {
        AcpSecrets.setApiKey(sunucuA, "silinecek")
        AcpSecrets.setApiKey(sunucuA, null)
        assertNull(AcpSecrets.getApiKey(sunucuA))
    }

    fun testBosAnahtarSilmeSayilir() {
        AcpSecrets.setApiKey(sunucuA, "bir-sey")
        AcpSecrets.setApiKey(sunucuA, "   ")
        assertNull(AcpSecrets.getApiKey(sunucuA))
    }

    /**
     * GÜVENLİK REGRESYONU — bu testin varlık sebebi budur.
     *
     * Biri `AcpSettings.State`'e `var apiKey by string()` eklerse anahtar düz
     * metin olarak `acp.xml`'e, oradan da yedeklere ve ayar senkronizasyonuna
     * yazılır. Sessizce olur ve fark edilmez. Bu test o anda kırmızıya döner.
     */
    fun testAnahtarAyarXmlineASLAYazilmaz() {
        val anahtar = "cok-gizli-anahtar-ASLA-XMLDE-OLMAMALI"
        AcpSecrets.setApiKey(sunucuA, anahtar)

        val durum = AcpSettings.State().apply {
            baseUrl = sunucuA
            model = "qwen"
            maxTokens = 4096
        }
        val xml = JDOMUtil.write(serialize(durum)!!)

        assertFalse(
            "API anahtarı ayar dosyasına sızmış! XML:\n$xml",
            xml.contains(anahtar),
        )
        // Alan adının kendisi de bulunmamalı — boş bile olsa orada durmamalı.
        assertFalse("apiKey alanı State'e eklenmiş: $xml", xml.contains("apiKey", ignoreCase = true))
    }
}
