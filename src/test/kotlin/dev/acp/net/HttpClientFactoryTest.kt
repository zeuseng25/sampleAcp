package dev.acp.net

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.net.ssl.CertificateManager
import dev.acp.settings.AcpSettings
import java.time.Duration

/**
 * 001/T23 — H2: "Kurumsal ağdaki sertifika ve vekil sunucu yapılandırması
 * **ek ayar gerektirmeden** çalışır."
 *
 * Bu testlerin amacı tek bir şeyi kanıtlamak: istemci JDK'nın varsayılanlarına
 * düşmüyor, IDE'nin kendi ayarlarını kullanıyor. Eklenti kendi sertifika veya
 * vekil çözümünü kurmaz — kullanıcı IDE'de neyi ayarladıysa o geçerlidir.
 */
class HttpClientFactoryTest : BasePlatformTestCase() {

    fun testBaglantiZamanAsimiAyardanGelir() {
        val istemci = HttpClientFactory.create(
            AcpSettings.State().apply { connectTimeoutMs = 4321 }
        )
        assertEquals(Duration.ofMillis(4321), istemci.connectTimeout().orElse(null))
    }

    fun testIdeninVekilAyariKullaniliyor() {
        val istemci = HttpClientFactory.create(AcpSettings.State())
        assertTrue(
            "Vekil seçici bağlanmamış — istemci IDE'nin proxy ayarını görmezden gelir",
            istemci.proxy().isPresent,
        )
        assertSame(
            "IDE'ninki yerine başka bir vekil seçici kullanılmış",
            HttpClientFactory.ideVekilSecici(),
            istemci.proxy().get(),
        )
    }

    fun testIdeninSertifikaYoneticisiKullaniliyor() {
        val istemci = HttpClientFactory.create(AcpSettings.State())
        assertSame(
            "IDE'nin CertificateManager'ı yerine JDK varsayılanı kullanılmış — " +
                "kurumsal iç CA ile imzalı sunucuya bağlanılamaz",
            CertificateManager.getInstance().sslContext,
            istemci.sslContext(),
        )
    }
}
