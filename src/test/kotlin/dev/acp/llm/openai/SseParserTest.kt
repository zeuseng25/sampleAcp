package dev.acp.llm.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SSE olay ayrıştırma. Kurallar W3C SSE spesifikasyonundan; OpenAI-uyumlu
 * sunucular (SGLang, LiteLLM) bunun dar bir alt kümesini kullanır ama
 * araya giren vekiller yorum/heartbeat satırı gönderebilir.
 */
class SseParserTest {

    private fun parse(vararg lines: String): List<SseEvent> {
        val p = SseParser()
        return lines.mapNotNull { p.feed(it) } + listOfNotNull(p.flush())
    }

    @Test
    fun `data satiri bos satirla tamamlanir`() {
        assertEquals(listOf(SseEvent.Data("{}")), parse("data: {}", ""))
    }

    @Test
    fun `iki noktadan sonra bosluk olmayabilir`() {
        assertEquals(listOf(SseEvent.Data("{}")), parse("data:{}", ""))
    }

    @Test
    fun `DONE sentineli akisi sonlandirir`() {
        assertEquals(listOf(SseEvent.Done), parse("data: [DONE]", ""))
    }

    @Test
    fun `yorum satirlari yok sayilir`() {
        assertEquals(listOf(SseEvent.Data("x")), parse(": ping", "data: x", ""))
    }

    @Test
    fun `event id ve retry alanlari yok sayilir`() {
        assertEquals(
            listOf(SseEvent.Data("x")),
            parse("event: message", "id: 7", "retry: 100", "data: x", "")
        )
    }

    @Test
    fun `ayni olaydaki birden fazla data satiri newline ile birlesir`() {
        assertEquals(listOf(SseEvent.Data("a\nb")), parse("data: a", "data: b", ""))
    }

    @Test
    fun `arka arkaya olaylar ayri ayri doner`() {
        assertEquals(
            listOf(SseEvent.Data("1"), SseEvent.Data("2")),
            parse("data: 1", "", "data: 2", "")
        )
    }

    @Test
    fun `sonlandirici bos satir gelmeden akis biterse flush olayi kurtarir`() {
        assertEquals(listOf(SseEvent.Data("son")), parse("data: son"))
    }

    @Test
    fun `pesuspese bos satirlar bos olay uretmez`() {
        assertEquals(emptyList(), parse("", "", ""))
    }

    @Test
    fun `data icindeki iki nokta korunur`() {
        assertEquals(
            listOf(SseEvent.Data("""{"a":"b:c"}""")),
            parse("""data: {"a":"b:c"}""", "")
        )
    }

    @Test
    fun `flush ikinci cagrida null doner`() {
        val p = SseParser()
        p.feed("data: x")
        assertEquals(SseEvent.Data("x"), p.flush())
        assertNull(p.flush())
    }
}
