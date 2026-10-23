package dev.acp.llm.openai

import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.CompletionRequest
import dev.acp.llm.LlmError
import dev.acp.llm.completion.FimBicimi
import dev.acp.llm.completion.FimPromptKurucu
import dev.acp.llm.completion.OneriTemizleyici
import dev.acp.settings.UrlNormalizer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * GERÇEK sunucuya karşı koşan testler. Ağ gerektirir, bu yüzden **varsayılan
 * olarak atlanır**:
 *
 * ```
 * ./gradlew test -Dacp.canliTest=true
 * ```
 *
 * Sahte sunucu bizim yazdığımız varsayımları doğrular; bu testler ise
 * varsayımların gerçek dünyayla uyuşup uyuşmadığını gösterir — gerçek TLS,
 * gerçek JSON biçimi, gerçek büyüklükte yanıt.
 *
 * OpenRouter'ın model listesi ucu anahtar istemez; sohbet ucu ister.
 */
class CanliSunucuTest {

    private companion object {
        /**
         * OpenRouter'ın ücretsiz katmanından küçük bir model; duman testi için
         * yeterli ve hiçbir ücret doğurmaz. Ücretsiz modeller zamanla değişebilir —
         * kaybolursa `/v1/models` içinde `:free` ile bitenlerden biri seçilir.
         */
        const val UCRETSIZ_MODEL = "liquid/lfm-2.5-2.6b:free"
    }

    private val openRouter = "https://openrouter.ai/api/v1"

    private fun canliMi() = System.getProperty("acp.canliTest") == "true"

    private fun istemci(baseUrl: String, anahtar: String? = null) =
        OpenAiLlmClient(
            http = HttpClient.newHttpClient(),
            baseUrl = UrlNormalizer.normalize(baseUrl)!!,
            apiKey = anahtar,
            readTimeoutMs = 30_000,
        )

    @Test
    fun `normalizer OpenRouter adresini dogru sadeleştirir`() {
        // Bu ağ gerektirmez, her koşuda çalışır.
        assertEquals("https://openrouter.ai/api", UrlNormalizer.normalize(openRouter))
        assertEquals("https://openrouter.ai/api", UrlNormalizer.normalize("$openRouter/"))
    }

    @Test
    fun `gercek sunucudan model listesi alinir`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı (-Dacp.canliTest=true ile açın)")

        val modeller = istemci(openRouter).listModels()

        assertTrue(modeller.size > 10, "Beklenenden az model geldi: ${modeller.size}")
        assertTrue(modeller.all { it.id.isNotBlank() }, "Boş id'li model var")
        println("Canlı: ${modeller.size} model. İlk beş: ${modeller.take(5).map { it.id }}")
    }

    @Test
    fun `gercek sunucuda olmayan uc BadRequest verir`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı")

        assertFailsWith<LlmError.BadRequest> {
            istemci("https://openrouter.ai/api/kesinlikle-yok").listModels()
        }
        Unit
    }

    /**
     * Gerçek modelle uçtan uca sohbet.
     *
     * İki ortam değişkeni gerekir — anahtar koda veya komut satırına yazılmaz:
     *
     * ```
     * export OPENROUTER_API_KEY=...
     * ./gradlew test -Dacp.canliTest=true --tests "*CanliSunucuTest*"
     * ```
     *
     * Varsayılan model [UCRETSIZ_MODEL] — OpenRouter'ın ücretsiz katmanından,
     * yani test kimseye ücret yazmaz. Başka bir model denemek için
     * `ACP_CANLI_MODEL` ortam değişkenini tanımlayın.
     */
    @Test
    fun `gercek modelden akisli yanit alinir`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı")
        val anahtar = System.getenv("OPENROUTER_API_KEY")
        val model = System.getenv("ACP_CANLI_MODEL")?.takeIf { it.isNotBlank() } ?: UCRETSIZ_MODEL
        assumeTrue(!anahtar.isNullOrBlank(), "OPENROUTER_API_KEY tanımlı değil")

        val deltalar = istemci(openRouter, anahtar).streamChat(
            ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(ChatMessage.Role.USER, "Sadece 'merhaba' yaz, başka hiçbir şey yazma."),
                ),
                maxTokens = 32,
            ),
        ).toList()

        val metin = deltalar.filterIsInstance<ChatDelta.Text>().joinToString("") { it.text }
        val bitis = deltalar.filterIsInstance<ChatDelta.Finish>()

        println("Canlı sohbet — model=$model, ${deltalar.size} delta, yanıt: ${metin.trim()}")
        assertTrue(metin.isNotBlank(), "Modelden hiç metin gelmedi")
        assertTrue(bitis.isNotEmpty(), "Bitiş deltası gelmedi")
    }

    /**
     * Ham tamamlama ucu gerçekten çalışıyor mu — **taşıma katmanı** kontrolü.
     *
     * Sahte sunucu bizim gövde biçimimizin kendi varsayımımıza uyduğunu gösterir;
     * bu test gerçek bir OpenAI-uyumlu ağ geçidinin onu **kabul ettiğini** gösterir.
     * Öneri kalitesiyle ilgilenmez — ücretsiz model zaten coder değil.
     */
    @Test
    fun `gercek sunucuda ham tamamlama ucu calisiyor`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı")
        val anahtar = System.getenv("OPENROUTER_API_KEY")
        assumeTrue(!anahtar.isNullOrBlank(), "OPENROUTER_API_KEY tanımlı değil")
        val model = System.getenv("ACP_CANLI_MODEL")?.takeIf { it.isNotBlank() } ?: UCRETSIZ_MODEL

        val sonuc = istemci(openRouter, anahtar).complete(
            CompletionRequest(model = model, prompt = "1, 2, 3, ", maxTokens = 8),
        )

        println("Canlı tamamlama — model=$model, metin=${sonuc.text.trim().take(60)}")
        assertTrue(sonuc.text.isNotEmpty(), "Ham tamamlama ucundan hiç metin gelmedi")
    }

    /**
     * **FIM doğrulaması — T10'un tekrar koşulabilir hâli.**
     *
     * Yeni bir model ailesine geçerken (GLM, Kimi) bu test koşulur:
     *
     * ```
     * export ACP_FIM_URL=https://... ACP_FIM_KEY=... ACP_FIM_MODEL=<model-adi>
     * ./gradlew test -Dacp.canliTest=true --tests "*CanliSunucuTest*"
     * ```
     *
     * `ACP_FIM_BICIM` ile biçim değiştirilir (varsayılan `QWEN`).
     *
     * Test yalnızca "bir şey geldi" demez; FIM'in **gerçekten** çalıştığını üç
     * ayrı belirtiyle ayırır. Sadece çıktıya bakmak yanıltıcı olurdu: model
     * tesadüfen doğru şeyi yazmış olabilir.
     */
    @Test
    fun `FIM destegi gercek modelde dogrulanir`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı")
        val url = System.getenv("ACP_FIM_URL")
        val model = System.getenv("ACP_FIM_MODEL")
        assumeTrue(!url.isNullOrBlank() && !model.isNullOrBlank(), "ACP_FIM_URL / ACP_FIM_MODEL yok")

        val bicim = FimBicimi.valueOf(System.getenv("ACP_FIM_BICIM")?.takeIf { it.isNotBlank() } ?: "QWEN")
        val oncesi = "fun topla(a: Int, b: Int): Int {\n    return "
        val sonrasi = "\n}"

        val sonuc = istemci(url, System.getenv("ACP_FIM_KEY")).complete(
            CompletionRequest(
                model = model,
                prompt = FimPromptKurucu.kur(bicim, oncesi, sonrasi),
                maxTokens = 40,
            ),
        )

        println("FIM — model=$model, biçim=$bicim")
        println("  ham metin   : ${sonuc.text.replace("\n", "\\n")}")
        println("  matched_stop: ${sonuc.matchedStop}")
        println("  token       : ${sonuc.usage?.completionTokens}")

        // 1) Belirteçler düz metin sanılmamış olmalı. Sanılsaydı model onları
        //    çıktıya kopyalardı — desteklenmediğinin en net işareti.
        for (belirtec in FimPromptKurucu.kacanBelirtecler(bicim)) {
            assertTrue(
                !sonuc.text.contains(belirtec),
                "Model '$belirtec' belirtecini düz metin olarak geri yazdı → FIM desteklenmiyor",
            )
        }

        // 2) Sonek tekrarlanmamalı. Model imleç *sonrasını* görmüyorsa `}` yazar.
        assertTrue(
            !sonuc.text.trimEnd().endsWith("}"),
            "Model sonek'i tekrarladı → imleç sonrasını dikkate almıyor, FIM çalışmıyor",
        )

        // 3) Çıktı kısa olmalı. Sohbet cevabı üretmiş olsaydı çok daha uzun olurdu.
        val temiz = OneriTemizleyici.temizle(sonuc.text, bicim, sonrasi)
        assertTrue(temiz != null, "Temizlemeden sonra gösterilecek bir öneri kalmadı")
        println("  temizlenmiş : $temiz")
        assertTrue(
            (sonuc.usage?.completionTokens ?: 0) < 25,
            "Yanıt fazla uzun (${sonuc.usage?.completionTokens} token) → sohbet cevabı üretmiş olabilir",
        )
    }

    @Test
    fun `cozulemeyen alan adi Connection verir`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı")

        val hata = assertFailsWith<LlmError.Connection> {
            istemci("https://bu-alan-adi-kesinlikle-yok-12345.invalid").listModels()
        }
        assertTrue(hata.userMessage.contains("ulaşılamıyor", ignoreCase = true))
    }
}
