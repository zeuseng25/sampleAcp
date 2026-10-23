package dev.acp.llm.openai

import dev.acp.fake.FakeSglangServer
import dev.acp.llm.LlmError
import kotlinx.coroutines.runBlocking
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 001/T30 — `listModels`, ayar ekranındaki "bağlantıyı test et" düğmesinin
 * altyapısı (H2).
 *
 * Saf test: IDE gerekmez, sahte sunucuya karşı koşar.
 */
class OpenAiLlmClientModelsTest {

    private fun istemci(sunucu: FakeSglangServer, anahtar: String? = null) =
        OpenAiLlmClient(
            http = HttpClient.newHttpClient(),
            baseUrl = sunucu.baseUrl,
            apiKey = anahtar,
            readTimeoutMs = 5_000,
        )

    @Test
    fun `model listesi doner`() = runBlocking {
        FakeSglangServer(modelIds = listOf("Qwen/Qwen3-Coder", "digeri")).start().use { s ->
            val modeller = istemci(s).listModels()
            assertEquals(listOf("Qwen/Qwen3-Coder", "digeri"), modeller.map { it.id })
            assertEquals("sglang", modeller[0].ownedBy)
        }
    }

    @Test
    fun `dogru anahtarla erisilir`() = runBlocking {
        FakeSglangServer(apiKey = "s3cret").start().use { s ->
            assertTrue(istemci(s, "s3cret").listModels().isNotEmpty())
        }
    }

    @Test
    fun `yanlis anahtar Unauthorized verir`() = runBlocking {
        FakeSglangServer(apiKey = "s3cret").start().use { s ->
            val hata = assertFailsWith<LlmError.Unauthorized> {
                istemci(s, "yanlis").listModels()
            }
            // Mesaj kullanıcıya gösterilecek; bağlantı hatasından ayırt edilebilmeli.
            assertTrue(hata.userMessage.contains("anahtar", ignoreCase = true))
        }
    }

    @Test
    fun `anahtar hic verilmediginde de Unauthorized ayirt edilir`() = runBlocking {
        FakeSglangServer(apiKey = "s3cret").start().use { s ->
            assertFailsWith<LlmError.Unauthorized> { istemci(s).listModels() }
        }
    }

    @Test
    fun `olmayan uc BadRequest verir`() = runBlocking {
        // Yanlış base URL'in tipik sonucu: sunucu ayakta ama uç bulunamıyor (404).
        FakeSglangServer().start().use { s ->
            val hata = assertFailsWith<LlmError.BadRequest> {
                OpenAiLlmClient(HttpClient.newHttpClient(), "${s.baseUrl}/olmayan", null, 5_000)
                    .listModels()
            }
            assertTrue(hata.userMessage.isNotBlank())
        }
    }

    @Test
    fun `ulasilamayan sunucu Connection verir`() = runBlocking {
        // 127.0.0.1:1 — kapalı port, bağlantı reddedilir.
        val hata = assertFailsWith<LlmError.Connection> {
            OpenAiLlmClient(HttpClient.newHttpClient(), "http://127.0.0.1:1", null, 2_000)
                .listModels()
        }
        assertTrue(hata.userMessage.contains("ulaşılamıyor", ignoreCase = true))
    }

    @Test
    fun `bos model listesi bos doner`() = runBlocking {
        FakeSglangServer(modelIds = emptyList()).start().use { s ->
            assertEquals(emptyList(), istemci(s).listModels())
        }
    }
}
