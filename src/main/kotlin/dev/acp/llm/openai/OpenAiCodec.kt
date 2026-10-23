package dev.acp.llm.openai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.acp.llm.ChatDelta
import dev.acp.llm.AracCagrisi
import dev.acp.llm.AracTanimi
import dev.acp.llm.ChatRequest
import dev.acp.llm.CompletionRequest
import dev.acp.llm.CompletionResult
import dev.acp.llm.FinishReason
import dev.acp.llm.LlmError
import dev.acp.llm.ModelInfo
import dev.acp.llm.ToolCallParcasi
import dev.acp.llm.Usage

/**
 * OpenAI-uyumlu istek/yanıt JSON eşlemesi.
 *
 * Jackson **yalnızca [JsonNode] seviyesinde** kullanılır: veri sınıfı yansıması,
 * anotasyon veya `jackson-module-kotlin` yok. Gerekçe: Jackson platformdan gelir,
 * sürümü bizim kontrolümüzde değildir. `readTree` / `ObjectNode.put` Jackson 2.0'dan
 * beri değişmedi, dolayısıyla sürüm sürüklenmesine bağışığız.
 *
 * Bu sınıf saftır — `com.intellij` importu yoktur (ArchitectureTest zorlar).
 */
internal class OpenAiCodec(private val mapper: ObjectMapper = ObjectMapper()) {

    fun encodeRequest(request: ChatRequest, stream: Boolean): String {
        val root = mapper.createObjectNode()
        root.put("model", request.model)

        val messages = root.putArray("messages")
        for (message in request.messages) {
            val m = messages.addObject()
                .put("role", message.role.wire)
                .put("content", message.content)
            // Araç alanları yalnızca DOLUYSA yazılır; araç kullanmayan istekler
            // birebir eskisi gibi kalır (spec 008 T22 gerileme koruması).
            message.toolCallId?.let { m.put("tool_call_id", it) }
            if (message.toolCalls.isNotEmpty()) {
                val dizi = m.putArray("tool_calls")
                for (c in message.toolCalls) {
                    dizi.addObject().put("id", c.id).put("type", "function")
                        .putObject("function").put("name", c.ad).put("arguments", gecerliArguman(c.argumanlarJson))
                }
            }
        }

        if (request.tools.isNotEmpty()) {
            val dizi = root.putArray("tools")
            for (t in request.tools) {
                val f = dizi.addObject().put("type", "function").putObject("function")
                f.put("name", t.ad).put("description", t.aciklama)
                f.set<ObjectNode>("parameters", t.parametreler)
            }
        }

        root.put("stream", stream)
        if (stream) {
            // Bu olmadan SGLang son usage parçasını hiç göndermez.
            root.putObject("stream_options").put("include_usage", true)
        }

        request.temperature?.let { root.put("temperature", it) }
        request.maxTokens?.let { root.put("max_tokens", it) }
        if (request.stop.isNotEmpty()) {
            val stop = root.putArray("stop")
            request.stop.forEach { stop.add(it) }
        }

        // Sunucuya özgü alanlar tipi genişletmeden geçer.
        for ((key, value) in request.extraBody) {
            root.set<ObjectNode>(key, mapper.valueToTree(value))
        }

        return mapper.writeValueAsString(root)
    }

    /**
     * vLLM geçmişteki argüman geçerli JSON değilse tüm isteği 400 ile reddeder
     * (T94). Bozuk argüman ham hâliyle aracın hata cevabında zaten anlatılıyor.
     */
    private fun gecerliArguman(ham: String): String =
        if (runCatching { mapper.readTree(ham) }.getOrNull()?.isObject == true) ham else "{}"

    /**
     * Tek bir SSE veri parçasını delta'lara çevirir.
     *
     * Not: `usage` yalnızca `choices` boş olan son parçada gelir; o durumda
     * bitiş nedeni bilinmez ([FinishReason.UNKNOWN]) ve akışı tüketen taraf
     * bunu daha önce gelen gerçek bitiş nedeniyle birleştirir.
     */
    fun decodeChunk(payload: String): List<ChatDelta> {
        val root = ayristir(payload)
        val out = mutableListOf<ChatDelta>()

        val choices = root.path("choices")
        if (choices.isArray && !choices.isEmpty) {
            val choice = choices[0]
            val delta = choice.path("delta")

            delta.path("reasoning_content").metin()?.let { out += ChatDelta.Reasoning(it) }
            delta.path("content").metin()?.let { out += ChatDelta.Text(it) }

            choice.path("finish_reason").metin()?.let {
                out += ChatDelta.Finish(bitisNedeni(it), usage(root))
            }
        } else {
            usage(root)?.let { out += ChatDelta.Finish(FinishReason.UNKNOWN, it) }
        }
        return out
    }

    fun encodeCompletion(request: CompletionRequest): String {
        val root = mapper.createObjectNode()
        root.put("model", request.model)
        root.put("prompt", request.prompt)
        root.put("max_tokens", request.maxTokens)
        root.put("temperature", request.temperature)
        // Akış yok: tek satırlık öneride parça parça almanın faydası yok.
        root.put("stream", false)
        if (request.stop.isNotEmpty()) {
            val stop = root.putArray("stop")
            request.stop.forEach { stop.add(it) }
        }
        for ((key, value) in request.extraBody) {
            root.set<ObjectNode>(key, mapper.valueToTree(value))
        }
        return mapper.writeValueAsString(root)
    }

    /**
     * `/v1/completions` yanıtını çözer.
     *
     * `choices[0].text` sohbetteki gibi `message.content` değil — ham tamamlama
     * ucunun biçimi farklı.
     */
    fun decodeCompletion(payload: String): CompletionResult {
        val root = ayristir(payload)
        val choices = root.path("choices")
        if (!choices.isArray || choices.isEmpty) {
            throw LlmError.Protocol("Tamamlama yanıtında choices yok")
        }
        val choice = choices[0]
        return CompletionResult(
            // Boş metin geçerli bir yanıttır (model önerecek bir şey bulamadı),
            // bu yüzden metin() değil doğrudan asText() — null'a düşmemeli.
            text = choice.path("text").let { if (it.isTextual) it.asText() else "" },
            finishReason = choice.path("finish_reason").metin()?.let { bitisNedeni(it) }
                ?: FinishReason.UNKNOWN,
            usage = usage(root),
            // SGLang sayı, bazı sunucular metin döndürüyor; ikisini de kabul et.
            matchedStop = choice.path("matched_stop").let {
                when {
                    it.isMissingNode || it.isNull -> null
                    else -> it.asText()
                }
            },
        )
    }

    /**
     * Akıştaki araç çağrısı parçalarını çıkarır.
     *
     * `decodeChunk`'tan ayrı: parçalar birleştirilmeden anlamlı bir delta
     * üretilemez, birleştirme ise durum gerektirir. Kodek durumsuz kalıyor,
     * biriktirme çağırana ait.
     */
    fun toolCallParcalari(payload: String): List<ToolCallParcasi> {
        val choices = ayristir(payload).path("choices")
        if (!choices.isArray || choices.isEmpty) return emptyList()
        val dizi = choices[0].path("delta").path("tool_calls")
        if (!dizi.isArray) return emptyList()

        return dizi.mapIndexed { sira, n ->
            ToolCallParcasi(
                // index yoksa dizideki sıra kullanılır; bazı sunucular göndermiyor.
                index = n.path("index").takeIf { it.isInt }?.asInt() ?: sira,
                id = n.path("id").metin(),
                ad = n.path("function").path("name").metin(),
                // Parça BOŞ olabilir ve bu anlamlıdır: metin() boşu null'a
                // çevirdiği için burada doğrudan okunuyor.
                argumanParcasi = n.path("function").path("arguments")
                    .takeIf { it.isTextual }?.asText(),
            )
        }
    }

    fun decodeModels(payload: String): List<ModelInfo> =
        ayristir(payload).path("data").mapNotNull { node ->
            node.path("id").metin()?.let { ModelInfo(it, node.path("owned_by").metin()) }
        }

    // ---- yardımcılar -----------------------------------------------------

    private fun ayristir(payload: String): JsonNode =
        try {
            mapper.readTree(payload)
        } catch (e: Exception) {
            throw LlmError.Protocol("JSON ayrıştırılamadı: ${e.message}")
        }

    /** Metin düğümüyse ve boş değilse değeri, aksi halde null. */
    private fun JsonNode.metin(): String? =
        if (isTextual && asText().isNotEmpty()) asText() else null

    private fun bitisNedeni(raw: String): FinishReason = when (raw) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.LENGTH
        "tool_calls" -> FinishReason.TOOL_CALLS
        "content_filter" -> FinishReason.CONTENT_FILTER
        else -> FinishReason.UNKNOWN
    }

    private fun usage(root: JsonNode): Usage? {
        val node = root.path("usage")
        if (!node.isObject) return null
        return Usage(
            promptTokens = node.path("prompt_tokens").takeIf { it.isInt }?.asInt(),
            completionTokens = node.path("completion_tokens").takeIf { it.isInt }?.asInt(),
            totalTokens = node.path("total_tokens").takeIf { it.isInt }?.asInt(),
        )
    }
}
