package dev.acp.llm.openai

import com.fasterxml.jackson.databind.ObjectMapper
import dev.acp.llm.AracCagrisi
import dev.acp.llm.AracTanimi
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Araç çağırma protokolü (spec 008 T21-T23).
 *
 * İki şey kritik: **araç yokken gövdenin birebir eskisi gibi kalması**
 * (001-007 gerilemesin) ve **akışta parçalanan argümanların birleşmesi**
 * (yarım JSON çalışan agent ile hiç çalışmayan agent arasındaki fark).
 */
class AracCagirmaTest {

    private val mapper = ObjectMapper()
    private val codec = OpenAiCodec()

    private fun arac(ad: String = "dosya_ara"): AracTanimi {
        val sema = mapper.createObjectNode()
        sema.put("type", "object")
        sema.putObject("properties").putObject("desen").put("type", "string")
        sema.putArray("required").add("desen")
        return AracTanimi(ad, "Projede metin arar", sema)
    }

    private fun istek(vararg araclar: AracTanimi) = ChatRequest(
        model = "m",
        messages = listOf(ChatMessage(ChatMessage.Role.USER, "merhaba")),
        tools = araclar.toList(),
    )

    // ---- T22: GERİLEME KORUMASI ----

    @Test
    fun `arac yokken govde birebir eskisi gibi`() {
        // 001-007'nin tüm istekleri araçsız. Buraya bir alan sızarsa sunucular
        // farklı davranabilir ve sebebi çok geç anlaşılır.
        val govde = mapper.readTree(codec.encodeRequest(istek(), stream = true))

        assertTrue(govde.path("tools").isMissingNode, "boş araç listesi gövdeye yazılmış")
        val mesaj = govde.path("messages")[0]
        assertTrue(mesaj.path("tool_calls").isMissingNode, "tool_calls yazılmış")
        assertTrue(mesaj.path("tool_call_id").isMissingNode, "tool_call_id yazılmış")
        assertEquals(setOf("role", "content"), mesaj.fieldNames().asSequence().toSet())
    }

    // ---- T21: kodlama ----

    @Test
    fun `araclar govdeye yazilir`() {
        val govde = mapper.readTree(codec.encodeRequest(istek(arac()), stream = true))
        val t = govde.path("tools")[0]

        assertEquals("function", t.path("type").asText())
        assertEquals("dosya_ara", t.path("function").path("name").asText())
        assertEquals("Projede metin arar", t.path("function").path("description").asText())
        assertEquals("object", t.path("function").path("parameters").path("type").asText())
    }

    @Test
    fun `asistanin arac cagrisi govdeye yazilir`() {
        val r = ChatRequest(
            model = "m",
            messages = listOf(
                ChatMessage(
                    ChatMessage.Role.ASSISTANT, "",
                    toolCalls = listOf(AracCagrisi("call_1", "dosya_ara", """{"desen":"x"}""")),
                ),
                ChatMessage(ChatMessage.Role.TOOL, "sonuc", toolCallId = "call_1"),
            ),
        )
        val govde = mapper.readTree(codec.encodeRequest(r, stream = true))

        val asistan = govde.path("messages")[0].path("tool_calls")[0]
        assertEquals("call_1", asistan.path("id").asText())
        assertEquals("dosya_ara", asistan.path("function").path("name").asText())
        assertEquals("""{"desen":"x"}""", asistan.path("function").path("arguments").asText())

        val aracMesaji = govde.path("messages")[1]
        assertEquals("tool", aracMesaji.path("role").asText())
        assertEquals("call_1", aracMesaji.path("tool_call_id").asText())
    }

    // ---- T94: geçmişteki argüman her zaman geçerli JSON nesnesi ----

    private fun gidenArguman(arguman: String): String {
        val r = ChatRequest(
            model = "m",
            messages = listOf(
                ChatMessage(ChatMessage.Role.ASSISTANT, "", toolCalls = listOf(AracCagrisi("c", "dosya_ara", arguman))),
                ChatMessage(ChatMessage.Role.TOOL, "HATA: argüman bozuk", toolCallId = "c"),
            ),
        )
        return mapper.readTree(codec.encodeRequest(r, stream = true))
            .path("messages")[0].path("tool_calls")[0].path("function").path("arguments").asText()
    }

    @Test
    fun `gecersiz arguman sunucuya bos nesne olarak gider`() {
        // vLLM: "Assistant tool call function.arguments must be valid JSON." → 400,
        // ve döngünün geri kalanı ölür. Modelin hatası tool mesajında zaten duruyor.
        for (bozuk in listOf("", "  ", """{"desen":""", "[1]", "null")) {
            assertEquals("{}", gidenArguman(bozuk), "'$bozuk' olduğu gibi gönderildi")
        }
    }

    @Test
    fun `gecerli arguman aynen gider`() {
        assertEquals("""{"desen":"x"}""", gidenArguman("""{"desen":"x"}"""))
    }

    // ---- T23: akışta parça birleştirme ----

    private fun parca(json: String) = codec.toolCallParcalari(json)

    @Test
    fun `parcalanan argumanlar birlestirilir`() {
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"dosya_ara","arguments":"{\"desen\":"}}]}}]}"""))
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"eskiCagri\"}"}}]}}]}"""))

        val cagrilar = b.tamamla()
        assertEquals(1, cagrilar.size)
        assertEquals("call_1", cagrilar[0].id)
        assertEquals("dosya_ara", cagrilar[0].ad)
        assertEquals("""{"desen":"eskiCagri"}""", cagrilar[0].argumanlarJson)
    }

    @Test
    fun `ayni anda birden fazla cagri index ile ayrilir`() {
        // T10 ölçümünde model 4 düzenlemeyi tek mesajda gönderdi.
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[
            {"index":0,"id":"a","function":{"name":"birinci","arguments":"{\"x\":1}"}},
            {"index":1,"id":"b","function":{"name":"ikinci","arguments":"{\"y\":2}"}}]}}]}"""))

        val c = b.tamamla()
        assertEquals(2, c.size)
        assertEquals(listOf("birinci", "ikinci"), c.map { it.ad })
        assertEquals("""{"x":1}""", c[0].argumanlarJson)
        assertEquals("""{"y":2}""", c[1].argumanlarJson)
    }

    @Test
    fun `index yoksa dizideki sira kullanilir`() {
        // Bazı sunucular index göndermiyor; ikisi tek çağrıya karışmamalı.
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[
            {"id":"a","function":{"name":"bir","arguments":"{}"}},
            {"id":"b","function":{"name":"iki","arguments":"{}"}}]}}]}"""))
        assertEquals(2, b.tamamla().size)
    }

    @Test
    fun `adi olmayan cagri atilir`() {
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}"""))
        assertTrue(b.tamamla().isEmpty(), "adsız çağrı üretildi")
    }

    @Test
    fun `argumansiz cagri bos nesne olur`() {
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"listele"}}]}}]}"""))
        assertEquals("{}", b.tamamla()[0].argumanlarJson)
    }

    @Test
    fun `arac icermeyen parca biriktiriciyi doldurmaz`() {
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"content":"merhaba"}}]}"""))
        assertTrue(b.bosMu())
        assertTrue(b.tamamla().isEmpty())
    }

    @Test
    fun `tamamla biriktiriciyi sifirlar`() {
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"x","arguments":"{}"}}]}}]}"""))
        assertEquals(1, b.tamamla().size)
        assertTrue(b.tamamla().isEmpty(), "ikinci çağrıda aynı sonuç tekrar döndü")
    }

    @Test
    fun `id sonradan gelirse ilk deger korunur`() {
        val b = ToolCallBiriktirici()
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"ilk","function":{"name":"x","arguments":"{"}}]}}]}"""))
        b.besle(parca("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"sonra","function":{"arguments":"}"}}]}}]}"""))
        assertEquals("ilk", b.tamamla()[0].id)
    }

    @Test
    fun `duz metin parcasi arac parcasi uretmez`() {
        assertTrue(parca("""{"choices":[{"delta":{"content":"a"}}]}""").isEmpty())
        assertTrue(parca("""{"choices":[]}""").isEmpty())
    }
}
