package dev.acp.llm.openai

import com.fasterxml.jackson.databind.ObjectMapper
import dev.acp.llm.CompletionRequest
import dev.acp.llm.FinishReason
import dev.acp.llm.LlmError
import dev.acp.fake.FakeSglangServer
import dev.acp.llm.completion.FimBicimi
import dev.acp.llm.completion.FimPromptKurucu
import kotlinx.coroutines.runBlocking
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `/v1/completions` uçtan uca — sahte sunucuya karşı.
 *
 * Sahte sunucu kullanılıyor çünkü buradaki testlerin çoğu sunucunun
 * **bozulmasını** gerektiriyor (500, zaman aşımı). Gerçek bir sunucudan bunlar
 * istenemez; düzgün çalıştığı sürece o testler hiçbir şey doğrulamaz.
 * Gerçek sunucuya karşı karşılığı `CanliSunucuTest`'te.
 */
class OpenAiLlmClientCompletionTest {

    private val mapper = ObjectMapper()

    private fun istemci(sunucu: FakeSglangServer, zamanAsimiMs: Long = 5_000) =
        OpenAiLlmClient(
            http = HttpClient.newHttpClient(),
            baseUrl = sunucu.baseUrl,
            apiKey = null,
            readTimeoutMs = zamanAsimiMs,
        )

    private fun istek(prompt: String = "merhaba") =
        CompletionRequest(model = "test-coder", prompt = prompt, maxTokens = 64)

    // ---- T30: mutlu yol ---------------------------------------------------

    @Test
    fun `tamamlama yaniti coziluyor`() = runBlocking {
        FakeSglangServer(completionText = "a + b").start().use { sunucu ->
            val sonuc = istemci(sunucu).complete(istek())

            assertEquals("a + b", sonuc.text)
            assertEquals(FinishReason.STOP, sonuc.finishReason)
            assertEquals(4, sonuc.usage?.completionTokens)
            // T10'daki gerçek yanıtta bu alan doluydu; modelin FIM belirteçlerini
            // tanıdığının kanıtı. Çözülmediğini fark etmek zor olurdu.
            assertEquals("248044", sonuc.matchedStop)
        }
    }

    @Test
    fun `istek dogru uca ve dogru govdeyle gidiyor`() = runBlocking {
        FakeSglangServer(completionText = "x").start().use { sunucu ->
            val prompt = FimPromptKurucu.kur(FimBicimi.QWEN, "fun f() {\n    return ", "\n}")
            istemci(sunucu).complete(
                CompletionRequest(model = "test-coder", prompt = prompt, maxTokens = 32),
            )

            val govde = mapper.readTree(sunucu.lastRequestBody)
            assertEquals("test-coder", govde.path("model").asText())
            assertEquals(32, govde.path("max_tokens").asInt())
            // Sohbet ucundaki gibi "messages" DEĞİL — FIM belirteçleri ham
            // prompt'ta gitmeli, yoksa sohbet şablonu onları bozar.
            assertTrue(govde.path("messages").isMissingNode, "messages gönderilmemeli")
            assertEquals(prompt, govde.path("prompt").asText())
            assertTrue(govde.path("prompt").asText().contains("<|fim_prefix|>"))
            assertTrue(!govde.path("stream").asBoolean(), "tamamlama akışsız olmalı")
        }
    }

    @Test
    fun `bos metin gecerli yanittir`() = runBlocking {
        // Model önerecek bir şey bulamamış olabilir; bu hata değil.
        FakeSglangServer(completionText = "").start().use { sunucu ->
            assertEquals("", istemci(sunucu).complete(istek()).text)
        }
    }

    @Test
    fun `stop dizileri govdeye giriyor`() = runBlocking {
        FakeSglangServer(completionText = "x").start().use { sunucu ->
            istemci(sunucu).complete(
                CompletionRequest(
                    model = "m", prompt = "p", maxTokens = 8, stop = listOf("\n", "<|endoftext|>"),
                ),
            )
            val stop = mapper.readTree(sunucu.lastRequestBody).path("stop")
            assertEquals(2, stop.size())
            assertEquals("\n", stop[0].asText())
        }
    }

    // ---- T31: zaman aşımı -------------------------------------------------

    @Test
    fun `sunucu susarsa zaman asimi olur`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.SESSIZ_KAL).start().use { sunucu ->
            val basla = System.currentTimeMillis()
            assertFailsWith<LlmError.Timeout> {
                istemci(sunucu, zamanAsimiMs = 800).complete(istek())
            }
            val gecen = System.currentTimeMillis() - basla
            // Yazarken beklemek kabul edilemez: zaman aşımına gerçekten uyulmalı.
            assertTrue(gecen < 5_000, "Zaman aşımına uyulmadı, $gecen ms beklendi")
        }
    }

    // ---- T32: hatalar dışarı sızmıyor ------------------------------------

    @Test
    fun `sunucu 500 donerse ServerError firlatir`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.SERVER_ERROR).start().use { sunucu ->
            val hata = assertFailsWith<LlmError.ServerError> { istemci(sunucu).complete(istek()) }
            assertEquals(500, hata.status)
        }
    }

    @Test
    fun `401 Unauthorized olarak ayrilir`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.UNAUTHORIZED).start().use { sunucu ->
            assertFailsWith<LlmError.Unauthorized> { istemci(sunucu).complete(istek()) }
            Unit
        }
    }

    @Test
    fun `sunucu kapaliysa Connection hatasi`() = runBlocking {
        val sunucu = FakeSglangServer().start()
        val istemci = istemci(sunucu)
        sunucu.close()

        assertFailsWith<LlmError.Connection> { istemci.complete(istek()) }
        Unit
    }

    @Test
    fun `bozuk govde Protocol hatasi verir`() {
        val codec = OpenAiCodec()
        assertFailsWith<LlmError.Protocol> { codec.decodeCompletion("{}") }
        assertFailsWith<LlmError.Protocol> { codec.decodeCompletion("bu json degil") }
    }

    @Test
    fun `matched_stop yoksa null doner`() {
        // Bazı sunucular bu alanı hiç göndermiyor; eksikliği hata değil.
        val sonuc = OpenAiCodec().decodeCompletion(
            """{"choices":[{"text":"a","finish_reason":"length"}]}""",
        )
        assertEquals(null, sonuc.matchedStop)
        assertEquals(FinishReason.LENGTH, sonuc.finishReason)
    }
}
