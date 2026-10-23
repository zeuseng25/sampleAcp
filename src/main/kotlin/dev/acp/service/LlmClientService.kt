package dev.acp.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.acp.llm.LlmClient
import dev.acp.llm.ModelInfo
import dev.acp.llm.openai.OpenAiLlmClient
import dev.acp.net.HttpClientFactory
import dev.acp.settings.AcpSecrets
import dev.acp.settings.AcpSettings
import kotlinx.coroutines.CoroutineScope

/**
 * Model istemcisinin sahibi.
 *
 * Coroutine kapsamı kurucuya platform tarafından enjekte edilir; uygulama
 * kapanırken iptal edilir. `Application.getCoroutineScope()` kullanılmaz —
 * kullanımdan kaldırıldı ve eklenti sınıflarını sızdırıyor.
 */
@Service(Service.Level.APP)
internal class LlmClientService(val scope: CoroutineScope) {

    /**
     * Verilen ayarlarla bir istemci kurar.
     *
     * Ayarlar parametre olarak alınır çünkü "bağlantıyı test et" **kaydedilmiş
     * değerleri değil, ekranda o an yazan değerleri** kullanmalı (H2).
     */
    fun createClient(baseUrl: String, apiKey: String?): LlmClient {
        val durum = AcpSettings.getInstance().state
        return OpenAiLlmClient(
            http = HttpClientFactory.create(durum),
            baseUrl = baseUrl,
            apiKey = apiKey,
            readTimeoutMs = durum.readTimeoutMs.toLong(),
            idleTimeoutMs = durum.idleTimeoutMs.toLong(),
        )
    }

    suspend fun listModels(baseUrl: String, apiKey: String?): List<ModelInfo> =
        createClient(baseUrl, apiKey).listModels()

    /**
     * Satır içi tamamlama için istemci. Ayarlar eksikse `null` — çağıran sessizce
     * vazgeçer, kullanıcıya uyarı çıkarmaz (spec 006 Hata Durumları).
     *
     * Sohbet istemcisinden farkı **zaman aşımı**: burada `completionTimeoutMs`
     * kullanılıyor (varsayılan 3 sn), sohbetin 180 saniyesi değil. Geç gelen
     * öneri zaten atılacağı için uzun beklemek yalnızca kaynak tüketir.
     */
    suspend fun tamamlamaIstemcisi(): LlmClient? {
        val durum = AcpSettings.getInstance().state
        if (!durum.tamamlamaAcikMi()) return null
        val adres = durum.baseUrl?.takeIf { it.isNotBlank() } ?: return null

        return OpenAiLlmClient(
            http = HttpClientFactory.create(durum),
            baseUrl = adres,
            apiKey = AcpSecrets.getApiKey(adres),
            readTimeoutMs = durum.completionTimeoutMs.toLong(),
            idleTimeoutMs = durum.completionTimeoutMs.toLong(),
        )
    }

    companion object {
        fun getInstance(): LlmClientService = service()
    }
}
