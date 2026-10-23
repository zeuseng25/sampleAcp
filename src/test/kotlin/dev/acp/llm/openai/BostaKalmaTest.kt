package dev.acp.llm.openai

import dev.acp.fake.FakeSglangServer
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.LlmError
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 003/T72 — akış ortasında sunucu susarsa bağlantı kesilmeli.
 *
 * Neden ayrı bir korumaya ihtiyaç var: `HttpRequest.timeout()` yalnızca yanıt
 * **başlığına** kadar geçerli. Sunucu başlıkları gönderip sonra susarsa hiçbir
 * zaman aşımı devreye girmez ve istemci **sonsuza kadar** bekler. Kullanıcı bunu
 * yaşadı: panel "yanıt geliyor" gösterip öylece kalıyordu.
 */
class BostaKalmaTest {

    private fun istek() = ChatRequest(
        model = "test",
        messages = listOf(ChatMessage(ChatMessage.Role.USER, "merhaba")),
    )

    @Test
    fun `sunucu akis ortasinda susarsa zaman asimi olur`() = runBlocking {
        FakeSglangServer(
            tokens = listOf("bir", "iki", "üç", "dört"),
            mode = FakeSglangServer.Mode.SESSIZ_KAL,
        ).start().use { sunucu ->
            val istemci = OpenAiLlmClient(
                http = HttpClient.newHttpClient(),
                baseUrl = sunucu.baseUrl,
                apiKey = null,
                readTimeoutMs = 30_000,
                idleTimeoutMs = 1_500, // testin hızlı bitmesi için kısa
            )

            val alinan = mutableListOf<ChatDelta>()
            var hata: Throwable? = null

            val basladi = System.currentTimeMillis()
            istemci.streamChat(istek()).catch { hata = it }.toList(alinan)
            val gecen = System.currentTimeMillis() - basladi

            assertTrue(hata is LlmError.Timeout, "boşta kalma zaman aşımı oluşmadı: $hata")
            assertEquals(LlmError.Timeout.Phase.IDLE, (hata as LlmError.Timeout).phase)

            // Sonsuza kadar beklememeli: sahte sunucu 60 sn susuyor.
            assertTrue(gecen < 15_000, "zaman aşımı çok geç oldu: ${gecen}ms")

            // Susmadan önce gelen metin kaybolmamalı.
            val metin = alinan.filterIsInstance<ChatDelta.Text>().joinToString("") { it.text }
            assertTrue(metin.isNotEmpty(), "susmadan önce gelen metin kaybolmuş")
        }
    }

    @Test
    fun `normal akista gozcu devreye girmez`() = runBlocking {
        // Yavaş ama sürekli veri gönderen sunucu kesilmemeli.
        FakeSglangServer(
            tokens = List(10) { "token " },
            tokenDelayMs = 120,
        ).start().use { sunucu ->
            val istemci = OpenAiLlmClient(
                http = HttpClient.newHttpClient(),
                baseUrl = sunucu.baseUrl,
                apiKey = null,
                readTimeoutMs = 30_000,
                idleTimeoutMs = 1_000, // token aralığından uzun ama toplam süreden kısa
            )

            val deltalar = istemci.streamChat(istek()).toList()
            val metin = deltalar.filterIsInstance<ChatDelta.Text>().joinToString("") { it.text }

            // Toplam süre (~1,2 sn) boşta kalma süresini aşıyor ama araları aşmıyor:
            // gözcü toplam süreye değil, SESSİZLİĞE bakmalı.
            assertEquals("token ".repeat(10), metin)
        }
    }

    @Test
    fun `heartbeat gonderen sunucu bosta sayilmaz`() = runBlocking {
        // Vekil sunucular yorum satırı gönderir; bunlar delta üretmez ama
        // bağlantının canlı olduğunu gösterir.
        FakeSglangServer(
            tokens = List(6) { "t" },
            mode = FakeSglangServer.Mode.HEARTBEATS,
            tokenDelayMs = 100,
        ).start().use { sunucu ->
            val istemci = OpenAiLlmClient(
                http = HttpClient.newHttpClient(),
                baseUrl = sunucu.baseUrl,
                apiKey = null,
                readTimeoutMs = 30_000,
                idleTimeoutMs = 1_000,
            )
            val metin = istemci.streamChat(istek()).toList()
                .filterIsInstance<ChatDelta.Text>().joinToString("") { it.text }
            assertEquals("tttttt", metin)
        }
    }
}
