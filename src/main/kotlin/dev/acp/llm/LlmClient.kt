package dev.acp.llm

import kotlinx.coroutines.flow.Flow

/**
 * Model sunucusuyla konuşma arayüzü.
 *
 * `Flow<ChatDelta>` döndürmesi bilinçli: ham metin yerine tiplenmiş delta'lar
 * taşındığı için, ileride araç çağırma eklenirken bu imza değişmeyecek —
 * yeni bir `ChatDelta` varyantı eklemek yetecek.
 *
 * Uygulamalar saf tutulur: IDE'ye bağlı hiçbir şey bilmezler, hazır bir
 * `HttpClient` enjekte edilir.
 */
internal interface LlmClient {

    /** Yanıtı parça parça akıtır. Toplayan coroutine iptal edilirse istek düşer. */
    fun streamChat(request: ChatRequest): Flow<ChatDelta>

    /** Sunucudaki modelleri listeler — "bağlantıyı test et" bunu kullanır. */
    suspend fun listModels(): List<ModelInfo>

    /**
     * Ham tamamlama — satır içi öneri için. Akışsız: tek satırlık bir öneride
     * akış kazanç sağlamaz, yalnızca karmaşıklık ekler.
     */
    suspend fun complete(request: CompletionRequest): CompletionResult
}
