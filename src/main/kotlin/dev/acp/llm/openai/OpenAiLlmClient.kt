package dev.acp.llm.openai

import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatRequest
import dev.acp.llm.CompletionRequest
import dev.acp.llm.CompletionResult
import dev.acp.llm.LlmClient
import dev.acp.llm.LlmError
import dev.acp.llm.ModelInfo
import dev.acp.llm.text.ThinkTagFilter
import dev.acp.llm.util.awaitCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * OpenAI-uyumlu sunucuya (LiteLLM → SGLang) konuşan istemci.
 *
 * [http] dışarıdan verilir — bu paket saf kalmalı, IDE'ye bağlanmamalı.
 * İstemciyi IDE'nin vekil ve sertifika ayarlarına bağlamak `dev.acp.net`
 * paketindeki fabrikanın işi.
 *
 * [baseUrl] normalleşmiş olmalıdır (sonunda `/` veya `/v1` bulunmaz) —
 * bkz. `UrlNormalizer`.
 */
internal class OpenAiLlmClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val apiKey: String?,
    private val readTimeoutMs: Long,
    /**
     * Akış başladıktan sonra sunucudan hiç veri gelmeden beklenecek süre.
     * `readTimeoutMs` yalnızca yanıt **başlığına** kadar geçerli; gövde akarken
     * devreye girmez.
     */
    private val idleTimeoutMs: Long = 120_000,
    private val codec: OpenAiCodec = OpenAiCodec(),
) : LlmClient {

    override suspend fun listModels(): List<ModelInfo> {
        val yanit = gonder(istek("/v1/models"))
        if (yanit.statusCode() != 200) throw hataUret(yanit.statusCode(), yanit.body())
        return codec.decodeModels(yanit.body())
    }

    override suspend fun complete(request: CompletionRequest): CompletionResult {
        val httpRequest = temelIstek("/v1/completions")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(codec.encodeCompletion(request)))
            .build()

        val yanit = gonder(httpRequest)
        if (yanit.statusCode() != 200) throw hataUret(yanit.statusCode(), yanit.body())
        return codec.decodeCompletion(yanit.body())
    }

    override fun streamChat(request: ChatRequest): Flow<ChatDelta> = channelFlow {
        val httpRequest = temelIstek("/v1/chat/completions")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(codec.encodeRequest(request, stream = true)))
            .build()

        val future = http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        val yanit = try {
            future.awaitCancellable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpTimeoutException) {
            throw LlmError.Timeout(LlmError.Timeout.Phase.READ, e)
        } catch (e: IOException) {
            throw LlmError.Connection(e)
        }

        if (yanit.statusCode() != 200) {
            val govde = yanit.body().use { it.readBytes().toString(Charsets.UTF_8) }
            throw hataUret(yanit.statusCode(), govde)
        }

        var duzgunBitti = false
        // Son veri anını gözcü okuyor; her okumada tazeleniyor.
        val sonVeriAni = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        var bostaKaldi = false
        val bolucu = SseLineSplitter()
        val ayristirici = SseParser()
        // Sunucu düşünme ayrıştırıcısıyla çalışmıyorsa <think> bloğu content
        // içinde gelir; bu filtre onu ayıklar. Ayrı alanda gelirse zaten
        // Reasoning olarak üretilir ve buraya hiç uğramaz.
        val dusunceFiltresi = ThinkTagFilter()
        // Araç çağrıları akışta parçalanarak gelir; tamamlanınca yayınlanır.
        val aracBiriktirici = ToolCallBiriktirici()

        val govde = yanit.body()

        /*
         * Boşta kalma gözcüsü.
         *
         * Neden gerekli: HttpRequest.timeout() yalnızca yanıt BAŞLIĞINA kadar
         * geçerli. Sunucu başlıkları gönderip sonra akış ortasında susarsa
         * hiçbir zaman aşımı devreye girmez ve istemci sonsuza kadar bekler.
         *
         * Okuma bloke olduğu için kesmenin yolu gövdeyi kapatmak: bloke okuma
         * IOException ile düşer.
         */
        val gozcu = launch {
            while (isActive) {
                delay(GOZCU_ARALIGI_MS)
                if (System.currentTimeMillis() - sonVeriAni.get() > idleTimeoutMs) {
                    bostaKaldi = true
                    runCatching { govde.close() }
                    break
                }
            }
        }

        try {
            govde.use { akis ->
                val okuyucu = akis.reader(Charsets.UTF_8)
                val tampon = CharArray(8192)

                dis@ while (true) {
                    currentCoroutineContext().ensureActive()
                    val okunan = try {
                        okuyucu.read(tampon)
                    } catch (e: IOException) {
                        // Gözcü kapattıysa bu bir zaman aşımıdır, ağ hatası değil.
                        if (bostaKaldi) throw LlmError.Timeout(LlmError.Timeout.Phase.IDLE, e)
                        throw e
                    }
                    if (okunan < 0) break
                    sonVeriAni.set(System.currentTimeMillis())

                    for (satir in bolucu.feed(String(tampon, 0, okunan))) {
                        when (val olay = ayristirici.feed(satir)) {
                            is SseEvent.Data -> {
                                aracBiriktirici.besle(codec.toolCallParcalari(olay.payload))
                                yay(codec.decodeChunk(olay.payload), dusunceFiltresi)
                            }
                            SseEvent.Done -> { duzgunBitti = true; break@dis }
                            null -> Unit
                        }
                    }
                }

                // Akış sonlandırıcı boş satır gelmeden bittiyse son olayı kurtar.
                if (!duzgunBitti) {
                    bolucu.flush()?.let { satir ->
                        when (val olay = ayristirici.feed(satir)) {
                            is SseEvent.Data -> yay(codec.decodeChunk(olay.payload), dusunceFiltresi)
                            SseEvent.Done -> duzgunBitti = true
                            null -> Unit
                        }
                    }
                    ayristirici.flush()?.let { olay ->
                        when (olay) {
                            is SseEvent.Data -> yay(codec.decodeChunk(olay.payload), dusunceFiltresi)
                            SseEvent.Done -> duzgunBitti = true
                        }
                    }
                }
            }
            dusunceFiltresi.flush().forEach { send(it) }
            // Tamamlanmış araç çağrıları en sonda, tek seferde. Yarım JSON
            // hiçbir zaman tüketiciye ulaşmaz.
            aracBiriktirici.tamamla().forEach { send(ChatDelta.ToolCall(it)) }
        } finally {
            gozcu.cancel()
            // İptal zincirinin son halkası: bağlantıyı gerçekten düşür ki
            // sunucu üretmeyi bıraksın, paylaşılan donanım boşa çalışmasın.
            future.cancel(true)
        }

        // Kısmi metin zaten yayınlandı; tüketici onu koruyup hatayı gösterir.
        if (bostaKaldi) throw LlmError.Timeout(LlmError.Timeout.Phase.IDLE)
        if (!duzgunBitti) throw LlmError.StreamTruncated()
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.channels.SendChannel<ChatDelta>.yay(
        deltalar: List<ChatDelta>,
        dusunceFiltresi: ThinkTagFilter,
    ) {
        for (delta in deltalar) {
            if (delta is ChatDelta.Text) {
                dusunceFiltresi.feed(delta.text).forEach { send(it) }
            } else {
                send(delta)
            }
        }
    }

    // ---- ortak ------------------------------------------------------------

    private fun temelIstek(yol: String): HttpRequest.Builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + yol))
            .timeout(Duration.ofMillis(readTimeoutMs))
            .also { if (!apiKey.isNullOrBlank()) it.header("Authorization", "Bearer $apiKey") }

    private fun istek(yol: String): HttpRequest =
        temelIstek(yol).header("Accept", "application/json").GET().build()

    private suspend fun gonder(request: HttpRequest): HttpResponse<String> =
        try {
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).awaitCancellable()
        } catch (e: CancellationException) {
            throw e // iptal hata değildir
        } catch (e: HttpTimeoutException) {
            throw LlmError.Timeout(LlmError.Timeout.Phase.READ, e)
        } catch (e: IOException) {
            throw LlmError.Connection(e)
        }

    /**
     * HTTP durumunu kullanıcıya gösterilebilir bir hataya çevirir.
     *
     * 401 ile bağlantı hatasını ayırmak önemli: kullanıcının atacağı adım
     * tamamen farklı (anahtarı düzelt vs. adresi/ağı kontrol et).
     */
    private companion object {
        /** Gözcünün kontrol sıklığı. Zaman aşımının kendisi ayardan gelir. */
        const val GOZCU_ARALIGI_MS = 1_000L
    }

    private fun hataUret(durum: Int, govde: String?): LlmError = when {
        durum == 401 || durum == 403 -> LlmError.Unauthorized(govde)
        durum in 400..499 -> LlmError.BadRequest(govde)
        else -> LlmError.ServerError(durum, govde)
    }
}
