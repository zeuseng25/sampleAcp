package dev.acp.fake

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * SGLang / LiteLLM'in OpenAI-uyumlu uçlarını taklit eden sahte sunucu.
 *
 * Yalnızca JDK'nın `com.sun.net.httpserver`'ını kullanır — yeni bağımlılık yok.
 *
 * İki işi var:
 *  1. Otomatik testler: mutlu yol, hata kodları, yarıda kesilen akış, iptal.
 *  2. Elle geliştirme: `main()` ile ayağa kaldırılıp `runIde` bu adrese bağlanır,
 *     böylece M4–M7 GPU sunucusuna erişim olmadan geliştirilebilir.
 */
class FakeSglangServer(
    private val tokens: List<String> = listOf("Merhaba", ", ", "dünya", "!"),
    private val mode: Mode = Mode.NORMAL,
    private val apiKey: String? = null,
    private val modelIds: List<String> = listOf("Qwen/Qwen3-Coder-30B-A3B-Instruct"),
    private val tokenDelayMs: Long = 0,
    /** `/v1/completions` yanıtı. Verilmezse [tokens] birleştirilir. */
    private val completionText: String? = null,
    /** ARAC_* kiplerinde çağrılan araç (spec 009: `dogrula`). */
    private val aracAdi: String = "dosya_ara",
) : AutoCloseable {

    enum class Mode {
        /** Tüm token'lar, ardından `[DONE]`. */
        NORMAL,

        /** Yanıt öncesi 401. */
        UNAUTHORIZED,

        /** 500 + gövde. */
        SERVER_ERROR,

        /** 400 + OpenAI biçimli hata gövdesi. */
        BAD_REQUEST,

        /** Yarısında bağlantıyı kapatır — `[DONE]` hiç gelmez. */
        TRUNCATE,

        /** Token'ların arasına `:` yorum satırları serpiştirir (vekil heartbeat'i). */
        HEARTBEATS,

        /** `reasoning_content` alanıyla düşünme bloğu yayınlar, sonra metin. */
        REASONING,

        /** Son veri parçası olarak yalnızca usage gönderir (choices boş). */
        USAGE,

        /**
         * Yarısında **susar ama bağlantıyı kapatmaz**. Boşta kalma zaman aşımı
         * olmadan istemci sonsuza kadar bekler.
         */
        SESSIZ_KAL,

        /**
         * Araç çağıran model (spec 010): istekte araç sonucu (`"role":"tool"`)
         * yoksa önce bir ara metin, sonra `dosya_ara` çağrısı yayınlar; varsa
         * [tokens]'ı cevap olarak verir.
         */
        ARAC_CAGIR,

        /** Her turda araç çağırır — adım sınırı testi için. */
        ARAC_HEP_CAGIR,

        /** İstekte `tools` varsa 400 (vLLM `--enable-auto-tool-choice` olmadan böyle). */
        ARACLARI_REDDET,

        /** İlk turda araç çağırır; araç sonucu gelince 400 (araç desteği VAR, başka bir hata). */
        ARAC_SONRA_400,
    }

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val port: Int get() = server.address.port
    val baseUrl: String get() = "http://127.0.0.1:$port"

    /** Son gelen istek gövdesi — testlerde prompt/bağlam doğrulamak için. */
    @Volatile
    var lastRequestBody: String? = null
        private set

    /** Sohbet ucuna gelen bütün gövdeler, sırayla. */
    val chatRequestBodies: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** İstemci akışı yarıda kapattıysa true olur — iptal testinin can alıcı noktası. */
    @Volatile
    var clientDisconnected: Boolean = false
        private set

    /** İstemci bağlantıyı kopardığında serbest kalır. */
    val disconnectLatch = CountDownLatch(1)

    init {
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/v1/models", ::handleModels)
        server.createContext("/v1/chat/completions", ::handleChat)
        server.createContext("/v1/completions", ::handleCompletion)
    }

    fun start(): FakeSglangServer = apply { server.start() }

    override fun close() {
        server.stop(0)
    }

    // ---- uçlar ----------------------------------------------------------

    private fun handleModels(exchange: HttpExchange) {
        if (!authorized(exchange)) return respondJson(exchange, 401, ERROR_UNAUTHORIZED)
        val data = modelIds.joinToString(",") {
            """{"id":${quote(it)},"object":"model","owned_by":"sglang"}"""
        }
        respondJson(exchange, 200, """{"object":"list","data":[$data]}""")
    }

    /**
     * Ham tamamlama ucu — satır içi öneri (FIM) bunu kullanır.
     *
     * Akışsız ve sohbetten ayrı bir gövde biçimi var: yanıt `choices[0].text`
     * içinde döner, `message.content` içinde değil.
     */
    private fun handleCompletion(exchange: HttpExchange) {
        lastRequestBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)

        if (!authorized(exchange)) return respondJson(exchange, 401, ERROR_UNAUTHORIZED)

        when (mode) {
            Mode.UNAUTHORIZED -> return respondJson(exchange, 401, ERROR_UNAUTHORIZED)
            Mode.SERVER_ERROR -> return respondJson(exchange, 500, """{"error":"iç hata"}""")
            Mode.BAD_REQUEST -> return respondJson(
                exchange, 400,
                """{"error":{"message":"model bulunamadı","type":"invalid_request_error"}}"""
            )
            // Yanıt vermeden bekler: tamamlama zaman aşımı testi bunu kullanır.
            Mode.SESSIZ_KAL -> {
                Thread.sleep(60_000)
                return
            }
            else -> Unit
        }

        val metin = completionText ?: tokens.joinToString("")
        respondJson(
            exchange, 200,
            """{"id":"cmpl-1","object":"text_completion","model":${quote(modelIds.first())},""" +
                """"choices":[{"index":0,"text":${quote(metin)},"finish_reason":"stop",""" +
                """"matched_stop":248044,"logprobs":null}],""" +
                """"usage":{"prompt_tokens":22,"completion_tokens":4,"total_tokens":26}}"""
        )
    }

    private fun handleChat(exchange: HttpExchange) {
        val govde = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        lastRequestBody = govde
        chatRequestBodies += govde

        if (!authorized(exchange)) return respondJson(exchange, 401, ERROR_UNAUTHORIZED)

        when (mode) {
            Mode.UNAUTHORIZED -> return respondJson(exchange, 401, ERROR_UNAUTHORIZED)
            Mode.SERVER_ERROR -> return respondJson(exchange, 500, """{"error":"iç hata"}""")
            Mode.BAD_REQUEST -> return respondJson(
                exchange, 400,
                """{"error":{"message":"model bulunamadı","type":"invalid_request_error"}}"""
            )
            Mode.ARACLARI_REDDET -> if ("\"tools\"" in govde) return respondJson(
                exchange, 400,
                """{"object":"error","message":"\"auto\" tool choice requires --enable-auto-tool-choice""" +
                    """ and --tool-call-parser to be set","type":"BadRequestError","code":400}"""
            )
            Mode.ARAC_SONRA_400 -> if ("\"role\":\"tool\"" in govde) return respondJson(
                exchange, 400,
                """{"object":"error","message":"Assistant tool call function.arguments must be valid JSON.",""" +
                    """"type":"BadRequest","code":400}"""
            )
            else -> Unit
        }
        // Araç verilmemiş istekte (adım sınırındaki son tur) model araç çağıramaz.
        val aracCagir = "\"tools\"" in govde && (
            mode == Mode.ARAC_SONRA_400 || mode == Mode.ARAC_HEP_CAGIR ||
                (mode == Mode.ARAC_CAGIR && "\"role\":\"tool\"" !in govde)
            )

        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(200, 0) // 0 = chunked

        try {
            exchange.responseBody.use { out ->
                fun emit(line: String) {
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.flush()
                    if (tokenDelayMs > 0) Thread.sleep(tokenDelayMs)
                }

                if (mode == Mode.REASONING) {
                    emit(sseData(reasoningChunk("Kullanıcı selam veriyor. ")))
                    emit(sseData(reasoningChunk("Kısa yanıt yeterli.")))
                }

                if (aracCagir) {
                    emit(sseData(textChunk("Önce arayayım.")))
                    val n = chatRequestBodies.size
                    val argumanlar = if (aracAdi == "dosya_ara") """{"desen":"hedef$n"}""" else "{}"
                    emit(sseData(toolCallChunk(argumanlar)))
                    emit(sseData(finishChunk()))
                    emit("data: [DONE]\n\n")
                    return@use
                }

                tokens.forEachIndexed { i, token ->
                    if (mode == Mode.HEARTBEATS) emit(": ping\n\n")
                    // Akışı [DONE] göndermeden kapatır; use bloğu stream'i kapatacak.
                    if (mode == Mode.TRUNCATE && i == tokens.size / 2) return
                    // Susar ama bağlantıyı açık tutar.
                    if (mode == Mode.SESSIZ_KAL && i == tokens.size / 2) {
                        Thread.sleep(60_000)
                        return
                    }
                    emit(sseData(textChunk(token)))
                }

                emit(sseData(finishChunk()))
                if (mode == Mode.USAGE) emit(sseData(usageChunk()))
                emit("data: [DONE]\n\n")
            }
        } catch (_: IOException) {
            // İstemci akışı kapattı — iptalin sunucuya ulaştığının kanıtı.
            clientDisconnected = true
            disconnectLatch.countDown()
        }
    }

    // ---- yardımcılar ----------------------------------------------------

    private fun authorized(exchange: HttpExchange): Boolean {
        val beklenen = apiKey ?: return true
        return exchange.requestHeaders.getFirst("Authorization") == "Bearer $beklenen"
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sseData(json: String) = "data: $json\n\n"

    private fun textChunk(token: String) =
        """{"id":"c1","object":"chat.completion.chunk","model":"fake",""" +
            """"choices":[{"index":0,"delta":{"content":${quote(token)}},"finish_reason":null}]}"""

    private fun reasoningChunk(token: String) =
        """{"id":"c1","object":"chat.completion.chunk","model":"fake",""" +
            """"choices":[{"index":0,"delta":{"reasoning_content":${quote(token)}},"finish_reason":null}]}"""

    private fun toolCallChunk(argumanlar: String) =
        """{"id":"c1","object":"chat.completion.chunk","model":"fake","choices":[{"index":0,"delta":""" +
            """{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":""" +
            """{"name":${quote(aracAdi)},"arguments":${quote(argumanlar)}}}]},"finish_reason":null}]}"""

    private fun finishChunk() =
        """{"id":"c1","object":"chat.completion.chunk","model":"fake",""" +
            """"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""

    private fun usageChunk() =
        """{"id":"c1","object":"chat.completion.chunk","model":"fake","choices":[],""" +
            """"usage":{"prompt_tokens":11,"completion_tokens":4,"total_tokens":15}}"""

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }

    private companion object {
        const val ERROR_UNAUTHORIZED =
            """{"error":{"message":"geçersiz api anahtarı","type":"invalid_request_error"}}"""
    }
}

/**
 * Elle geliştirme için: sunucuyu sabit bir portta ayağa kaldırır.
 * `runIde` içindeki ACP ayarlarına yazdırılan adresi girin.
 */
fun main() {
    val server = FakeSglangServer(
        tokens = listOf(
            "Tabii", ", ", "işte", " örnek", ":\n\n", "```kotlin\n",
            "fun merhaba() {\n", "    println(\"selam\")\n", "}\n", "```\n",
        ),
        tokenDelayMs = 40,
    ).start()
    println("Sahte SGLang hazır: ${server.baseUrl}")
    println("ACP ayarlarına bu adresi girin. Durdurmak için Ctrl+C.")
    TimeUnit.DAYS.sleep(365)
}
