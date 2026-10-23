package dev.acp.net

import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import dev.acp.settings.AcpSettings
import java.net.ProxySelector
import java.net.http.HttpClient
import java.time.Duration

/**
 * Model sunucusuna gidecek HTTP istemcisini kurar.
 *
 * **Eklenti kendi vekil veya sertifika çözümünü kurmaz.** Kullanıcı IDE'de neyi
 * ayarladıysa o geçerlidir; burada yapılan tek şey JDK'nın istemcisini o ayarlara
 * bağlamak.
 *
 * Bu bağlama neden gerekli: `java.net.http.HttpClient` JDK'nın sınıfıdır,
 * IntelliJ'i tanımaz. Açıkça söylenmezse IDE'nin vekil ayarını ve güvendiği
 * sertifikaları **görmezden gelir**, kendi varsayılanlarıyla gider. Sonuç,
 * kurumsal ağda anlaşılmaz bir `SSLHandshakeException` olur.
 *
 * Sertifika doğrulamasını atlama seçeneği **bilinçli olarak yoktur**. Kurumsal
 * iç CA ile imzalı sunucular, sertifika IDE'ye tanıtılarak çalışır — IDE zaten
 * bilinmeyen sertifikada kullanıcıya sorar ve kabul edileni hatırlar.
 */
internal object HttpClientFactory {

    fun create(state: AcpSettings.State): HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(state.connectTimeoutMs.toLong()))
            .proxy(ideVekilSecici())
            .sslContext(CertificateManager.getInstance().sslContext)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    /** IDE'nin vekil yapılandırmasını uygulayan seçici. */
    fun ideVekilSecici(): ProxySelector = JdkProxyProvider.getInstance().proxySelector
}
