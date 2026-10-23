package dev.acp.llm.openai

import dev.acp.fake.FakeSglangServer
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.FinishReason
import dev.acp.llm.LlmError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.http.HttpClient
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 001/T31–T34 — akışlı sohbet (H3). */
class OpenAiLlmClientStreamTest {

    private fun istemci(sunucu: FakeSglangServer, anahtar: String? = null) =
        OpenAiLlmClient(HttpClient.newHttpClient(), sunucu.baseUrl, anahtar, 10_000)

    private fun istek() = ChatRequest(
        model = "test-model",
        messages = listOf(ChatMessage(ChatMessage.Role.USER, "merhaba")),
    )

    private fun metin(deltalar: List<ChatDelta>) =
        deltalar.filterIsInstance<ChatDelta.Text>().joinToString("") { it.text }

    // ---- T31: akış ---------------------------------------------------------

    @Test
    fun `tokenlar sirayla metin deltasi olarak akar`() = runBlocking {
        FakeSglangServer(tokens = listOf("Mer", "haba", " dünya")).start().use { s ->
            val deltalar = istemci(s).streamChat(istek()).toList()
            assertEquals("Merhaba dünya", metin(deltalar))
        }
    }

    @Test
    fun `akis bitis deltasiyla sonlanir`() = runBlocking {
        FakeSglangServer().start().use { s ->
            val bitis = istemci(s).streamChat(istek()).toList().last()
            assertEquals(ChatDelta.Finish(FinishReason.STOP), bitis)
        }
    }

    @Test
    fun `istek govdesi sunucuya dogru gider`() = runBlocking {
        FakeSglangServer().start().use { s ->
            istemci(s).streamChat(istek()).toList()
            val govde = s.lastRequestBody!!
            assertTrue(govde.contains("test-model"), "model yok: $govde")
            assertTrue(govde.contains("merhaba"), "mesaj yok: $govde")
            assertTrue(govde.contains("\"stream\":true"), "akış istenmemiş: $govde")
            assertTrue(govde.contains("include_usage"), "usage istenmemiş: $govde")
        }
    }

    @Test
    fun `heartbeat satirlari akisi bozmaz`() = runBlocking {
        FakeSglangServer(
            tokens = listOf("a", "b"),
            mode = FakeSglangServer.Mode.HEARTBEATS,
        ).start().use { s ->
            assertEquals("ab", metin(istemci(s).streamChat(istek()).toList()))
        }
    }

    @Test
    fun `usage parcasi bitis deltasina tasinir`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.USAGE).start().use { s ->
            val bitisler = istemci(s).streamChat(istek()).toList()
                .filterIsInstance<ChatDelta.Finish>()
            assertTrue(bitisler.any { it.usage?.totalTokens == 15 }, "usage taşıyan bitiş yok")
        }
    }

    // ---- T34: düşünme bölümü ----------------------------------------------

    @Test
    fun `sunucu ayri alanda gonderirse dusunme deltasi uretilir`() = runBlocking {
        FakeSglangServer(
            tokens = listOf("cevap"),
            mode = FakeSglangServer.Mode.REASONING,
        ).start().use { s ->
            val deltalar = istemci(s).streamChat(istek()).toList()
            val dusunme = deltalar.filterIsInstance<ChatDelta.Reasoning>()
            assertTrue(dusunme.isNotEmpty(), "düşünme deltası yok")
            assertEquals("cevap", metin(deltalar))
        }
    }

    @Test
    fun `sunucu cevap icinde gonderirse dusunme ayiklanir`() = runBlocking {
        // Sunucu düşünce ayrıştırıcısı olmadan başlatılmışsa bu yol işler.
        FakeSglangServer(
            tokens = listOf("<think>", "düşünüyorum", "</think>", "asıl cevap"),
        ).start().use { s ->
            val deltalar = istemci(s).streamChat(istek()).toList()
            assertEquals("asıl cevap", metin(deltalar))
            assertEquals(
                "düşünüyorum",
                deltalar.filterIsInstance<ChatDelta.Reasoning>().joinToString("") { it.text },
            )
        }
    }

    // ---- T32: hatalar ------------------------------------------------------

    @Test
    fun `401 Unauthorized verir`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.UNAUTHORIZED).start().use { s ->
            assertFailsWith<LlmError.Unauthorized> { istemci(s).streamChat(istek()).toList() }
            Unit
        }
    }

    @Test
    fun `500 ServerError verir`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.SERVER_ERROR).start().use { s ->
            assertFailsWith<LlmError.ServerError> { istemci(s).streamChat(istek()).toList() }
            Unit
        }
    }

    @Test
    fun `400 BadRequest verir`() = runBlocking {
        FakeSglangServer(mode = FakeSglangServer.Mode.BAD_REQUEST).start().use { s ->
            assertFailsWith<LlmError.BadRequest> { istemci(s).streamChat(istek()).toList() }
            Unit
        }
    }

    @Test
    fun `yarida kesilen akista gelen metin korunur ve hata bildirilir`() = runBlocking {
        FakeSglangServer(
            tokens = listOf("bir", "iki", "üç", "dört"),
            mode = FakeSglangServer.Mode.TRUNCATE,
        ).start().use { s ->
            val alinan = mutableListOf<ChatDelta>()
            var hata: Throwable? = null

            istemci(s).streamChat(istek())
                .catch { hata = it }
                .toList(alinan)

            // H3: "Bağlantı yanıt ortasında koparsa o ana kadar gelen metin kaybolmaz."
            assertTrue(metin(alinan).isNotEmpty(), "hiç metin gelmemiş")
            assertTrue(hata is LlmError.StreamTruncated, "hata bildirilmemiş")
        }
    }

    // ---- T33: iptal --------------------------------------------------------

    @Test
    fun `iptal sunucuya ulasir ve baglanti dusar`() = runBlocking {
        FakeSglangServer(
            tokens = List(500) { "token " },
            tokenDelayMs = 10,
        ).start().use { s ->
            val alinan = mutableListOf<ChatDelta>()
            val job = launch(Dispatchers.Default) {
                istemci(s).streamChat(istek()).collect { alinan.add(it) }
            }

            // Akış gerçekten başlasın.
            withTimeout(5_000) {
                while (alinan.isEmpty()) delay(10)
            }
            job.cancelAndJoin()

            // Asıl kanıt: sunucu bağlantının koptuğunu gördü, yani üretimi durdurdu.
            assertTrue(
                s.disconnectLatch.await(5, TimeUnit.SECONDS),
                "sunucu kopmayı görmedi — paylaşılan donanım boşa çalışmaya devam ederdi",
            )
            assertTrue(s.clientDisconnected)
        }
    }
}
