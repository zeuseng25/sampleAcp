package dev.acp.llm.openai

import com.fasterxml.jackson.databind.ObjectMapper
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.FinishReason
import dev.acp.llm.LlmError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T15–T17. JSON eşlemesi elle yazılıyor (platformun Jackson'ı, `JsonNode` seviyesinde) —
 * gerekçe: plan.md "JSON/HTTP yığını" tablosu.
 */
class OpenAiCodecTest {

    private val codec = OpenAiCodec()
    private val mapper = ObjectMapper()

    private fun encode(request: ChatRequest, stream: Boolean = true) =
        mapper.readTree(codec.encodeRequest(request, stream))

    private fun istek(
        model: String = "qwen",
        mesajlar: List<ChatMessage> = listOf(ChatMessage(ChatMessage.Role.USER, "merhaba")),
        temperature: Double? = null,
        maxTokens: Int? = null,
        stop: List<String> = emptyList(),
        extraBody: Map<String, Any?> = emptyMap(),
    ) = ChatRequest(model, mesajlar, temperature, maxTokens, stop, extraBody)

    // ---- encodeRequest ---------------------------------------------------

    @Test
    fun `zorunlu alanlar govdede`() {
        val j = encode(istek())
        assertEquals("qwen", j["model"].asText())
        assertTrue(j["stream"].asBoolean())
        assertEquals(1, j["messages"].size())
        assertEquals("user", j["messages"][0]["role"].asText())
        assertEquals("merhaba", j["messages"][0]["content"].asText())
    }

    @Test
    fun `akis modunda usage istenir`() {
        // SGLang bunu görmezse son usage parçasını hiç göndermez.
        assertTrue(encode(istek(), stream = true)["stream_options"]["include_usage"].asBoolean())
    }

    @Test
    fun `akis kapaliyken stream_options gonderilmez`() {
        val j = encode(istek(), stream = false)
        assertFalse(j["stream"].asBoolean())
        assertFalse(j.has("stream_options"))
    }

    @Test
    fun `bos opsiyonel alanlar hic gonderilmez`() {
        val j = encode(istek())
        assertFalse(j.has("temperature"))
        assertFalse(j.has("max_tokens"))
        assertFalse(j.has("stop"))
    }

    @Test
    fun `dolu opsiyonel alanlar gonderilir`() {
        val j = encode(istek(temperature = 0.2, maxTokens = 2048, stop = listOf("</son>")))
        assertEquals(0.2, j["temperature"].asDouble())
        assertEquals(2048, j["max_tokens"].asInt())
        assertEquals("</son>", j["stop"][0].asText())
    }

    @Test
    fun `roller dogru esleniyor`() {
        val j = encode(
            istek(
                mesajlar = listOf(
                    ChatMessage(ChatMessage.Role.SYSTEM, "s"),
                    ChatMessage(ChatMessage.Role.USER, "u"),
                    ChatMessage(ChatMessage.Role.ASSISTANT, "a"),
                )
            )
        )
        assertEquals(listOf("system", "user", "assistant"), j["messages"].map { it["role"].asText() })
    }

    @Test
    fun `extraBody govdeye eklenir`() {
        // SGLang'e özgü alanlar tipi genişletmeden geçer.
        val j = encode(istek(extraBody = mapOf("chat_template_kwargs" to mapOf("enable_thinking" to false))))
        assertFalse(j["chat_template_kwargs"]["enable_thinking"].asBoolean())
    }

    @Test
    fun `turkce karakterler bozulmadan gider`() {
        val j = encode(istek(mesajlar = listOf(ChatMessage(ChatMessage.Role.USER, "ğüşiöç İĞŞ"))))
        assertEquals("ğüşiöç İĞŞ", j["messages"][0]["content"].asText())
    }

    // ---- decodeChunk -----------------------------------------------------

    private fun parca(delta: String, finish: String = "null") =
        """{"choices":[{"index":0,"delta":$delta,"finish_reason":$finish}]}"""

    @Test
    fun `content metin deltasi uretir`() {
        assertEquals(
            listOf(ChatDelta.Text("selam")),
            codec.decodeChunk(parca("""{"content":"selam"}"""))
        )
    }

    @Test
    fun `reasoning_content dusunme deltasi uretir`() {
        assertEquals(
            listOf(ChatDelta.Reasoning("düşünüyorum")),
            codec.decodeChunk(parca("""{"reasoning_content":"düşünüyorum"}"""))
        )
    }

    @Test
    fun `bos delta hicbir sey uretmez`() {
        assertEquals(emptyList(), codec.decodeChunk(parca("{}")))
    }

    @Test
    fun `bos string icerik yayilmaz`() {
        assertEquals(emptyList(), codec.decodeChunk(parca("""{"content":""}""")))
    }

    @Test
    fun `finish_reason bitis deltasi uretir`() {
        assertEquals(
            listOf(ChatDelta.Finish(FinishReason.STOP)),
            codec.decodeChunk(parca("{}", finish = "\"stop\""))
        )
    }

    @Test
    fun `length bitis nedeni taninir`() {
        assertEquals(
            listOf(ChatDelta.Finish(FinishReason.LENGTH)),
            codec.decodeChunk(parca("{}", finish = "\"length\""))
        )
    }

    @Test
    fun `bilinmeyen bitis nedeni UNKNOWN olur`() {
        assertEquals(
            listOf(ChatDelta.Finish(FinishReason.UNKNOWN)),
            codec.decodeChunk(parca("{}", finish = "\"yeni_bir_sey\""))
        )
    }

    @Test
    fun `choices bos olan usage parcasi cokmez ve usage tasir`() {
        // SGLang, stream_options.include_usage ile son parçayı böyle gönderir.
        val deltalar = codec.decodeChunk(
            """{"choices":[],"usage":{"prompt_tokens":11,"completion_tokens":4,"total_tokens":15}}"""
        )
        val bitis = deltalar.single() as ChatDelta.Finish
        assertEquals(11, bitis.usage?.promptTokens)
        assertEquals(4, bitis.usage?.completionTokens)
        assertEquals(15, bitis.usage?.totalTokens)
    }

    @Test
    fun `icerik ve bitis ayni parcada gelirse ikisi de uretilir`() {
        assertEquals(
            listOf(ChatDelta.Text("son"), ChatDelta.Finish(FinishReason.STOP)),
            codec.decodeChunk(parca("""{"content":"son"}""", finish = "\"stop\""))
        )
    }

    @Test
    fun `bozuk JSON Protocol hatasi verir`() {
        assertFailsWith<LlmError.Protocol> { codec.decodeChunk("{bu json degil") }
    }

    // ---- decodeModels ----------------------------------------------------

    @Test
    fun `model listesi ayristirilir`() {
        val modeller = codec.decodeModels(
            """{"object":"list","data":[
                 {"id":"Qwen/Qwen3-Coder","object":"model","owned_by":"sglang"},
                 {"id":"baska-model","object":"model"}]}"""
        )
        assertEquals(listOf("Qwen/Qwen3-Coder", "baska-model"), modeller.map { it.id })
        assertEquals("sglang", modeller[0].ownedBy)
    }

    @Test
    fun `bos model listesi bos doner`() {
        assertEquals(emptyList(), codec.decodeModels("""{"object":"list","data":[]}"""))
    }
}
