package dev.acp.llm

/**
 * LLM katmanının veri tipleri.
 *
 * Bu paket saftır: `com.intellij.*` importu YASAKTIR (ArchitectureTest zorlar).
 * Böylece IDE başlatmadan, saniyeler içinde test edilebilir.
 */

data class ChatMessage(
    val role: Role,
    val content: String,
    /**
     * Asistan araç çağırdıysa (spec 008). Yeni alanlar **varsayılan değerli**:
     * mevcut çağrılar ve testler değişmeden derlenir.
     */
    val toolCalls: List<AracCagrisi> = emptyList(),
    /** Rol [Role.TOOL] ise hangi çağrıya yanıt olduğu. */
    val toolCallId: String? = null,
) {
    /** [wire] protokoldeki karşılığı; enum adı değişse bile gövde bozulmasın diye ayrı. */
    enum class Role(val wire: String) {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),

        /** Araç sonucu — spec 008. */
        TOOL("tool"),
    }
}

/**
 * Modele sunulan bir araç (spec 008).
 *
 * [parametreler] JSON Schema'dır ve `JsonNode` düzeyinde tutulur — veri sınıfı
 * yansıması yok (Altın Kural 1: Jackson sürümü bizim kontrolümüzde değil).
 */
data class AracTanimi(
    val ad: String,
    val aciklama: String,
    val parametreler: com.fasterxml.jackson.databind.JsonNode,
)

/**
 * Modelin yapmak istediği araç çağrısı.
 *
 * [argumanlarJson] ham metindir; ayrıştırma aracı çalıştıran tarafın işi.
 * Model geçersiz JSON üretebilir ve bu bir **protokol hatası değil**, modele
 * bildirilecek bir araç hatasıdır.
 */
data class AracCagrisi(val id: String, val ad: String, val argumanlarJson: String)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val stop: List<String> = emptyList(),
    /**
     * SGLang'e özgü alanlar (`chat_template_kwargs`, `separate_reasoning` gibi)
     * tipi genişletmeden gövdeye eklenir.
     */
    val extraBody: Map<String, Any?> = emptyMap(),
    /**
     * Modele sunulan araçlar (spec 008). **Boşsa gövdeye hiç yazılmaz** —
     * araç kullanmayan istekler birebir eskisi gibi kalır.
     *
     * **Neden en sonda:** araya eklenseydi konumsal çağrı yapan mevcut kod
     * sessizce yanlış parametreye bağlanırdı. (Derleyici bu durumda yakaladı,
     * ama tip uyumlu olsaydı yakalamazdı.)
     */
    val tools: List<AracTanimi> = emptyList(),
)

/**
 * Ham tamamlama isteği — `/v1/completions` ucuna gider, `/v1/chat/completions`'a değil.
 *
 * Ayrı bir tip olmasının sebebi FIM: doldurma belirteçleri (`<|fim_prefix|>` gibi)
 * **ham prompt** içinde gitmeli. Sohbet ucu araya kendi şablonunu sokar ve
 * belirteçleri bozar; model onları düz metin sanar.
 */
data class CompletionRequest(
    val model: String,
    val prompt: String,
    val maxTokens: Int,
    val temperature: Double = 0.0,
    val stop: List<String> = emptyList(),
    val extraBody: Map<String, Any?> = emptyMap(),
)

/**
 * Ham tamamlama yanıtı.
 *
 * [matchedStop] SGLang'e özgü ve tanı için değerli: model bir **özel belirteçte**
 * durduysa dolu gelir. FIM belirteçlerini gerçekten tanıdığının kanıtı budur —
 * [text] doğru görünse bile bu alan boşsa model belirteçleri düz metin sanıyor
 * olabilir. Yeni bir model ailesi denenirken buraya bakılır.
 */
data class CompletionResult(
    val text: String,
    val finishReason: FinishReason,
    val usage: Usage? = null,
    val matchedStop: String? = null,
)

enum class FinishReason { STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER, UNKNOWN }

data class Usage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

/**
 * Akıştan gelen tek bir parça.
 *
 * `Flow<ChatDelta>` (ham `Flow<String>` yerine) bilinçli bir seçim: v2'de
 * araç çağırma eklenirken buraya yeni bir varyant eklemek yetecek, imzalar
 * değişmeyecek. v1 tüketicileri `filterIsInstance<Text>()` ile çalışmaya devam eder.
 */
sealed interface ChatDelta {
    /** Kullanıcıya gösterilecek yanıt metni. */
    data class Text(val text: String) : ChatDelta

    /** Qwen3'ün düşünme bloğu. UI'da katlanmış, soluk bir bölümde gösterilir. */
    data class Reasoning(val text: String) : ChatDelta

    /** Akışın sonu. `usage` yalnızca sunucu `stream_options.include_usage` desteklerse dolu gelir. */
    data class Finish(val reason: FinishReason, val usage: Usage? = null) : ChatDelta

    /**
     * Modelin tamamlanmış bir araç çağrısı (spec 008).
     *
     * Akışta parça parça gelir; istemci parçaları birleştirip **yalnızca
     * tamamlandığında** yayınlar. Yarım JSON tüketiciye ulaşmaz.
     */
    data class ToolCall(val cagri: AracCagrisi) : ChatDelta
}

/**
 * Akışta gelen bir araç çağrısı parçası (protokol ayrıntısı).
 *
 * OpenAI akışında araç argümanları JSON metni olarak **bölünerek** gelir ve
 * `index` ile hangi çağrıya ait olduğu belirtilir. Yarım JSON ayrıştırılamaz,
 * bu yüzden parçalar birleştirilene kadar tüketiciye verilmez.
 */
internal data class ToolCallParcasi(
    val index: Int,
    val id: String? = null,
    val ad: String? = null,
    val argumanParcasi: String? = null,
)

/** `/v1/models` yanıtındaki bir model. */
data class ModelInfo(val id: String, val ownedBy: String? = null)
