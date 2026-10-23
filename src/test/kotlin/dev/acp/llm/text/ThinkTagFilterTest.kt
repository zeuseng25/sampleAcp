package dev.acp.llm.text

import dev.acp.llm.ChatDelta
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SGLang `--reasoning-parser` ile başlatılmamışsa Qwen3'ün düşünme bloğu
 * `content` içinde satır içi gelir. Bu filtre onu ayıklar.
 */
class ThinkTagFilterTest {

    private fun calistir(vararg parcalar: String): List<ChatDelta> {
        val f = ThinkTagFilter()
        return parcalar.flatMap { f.feed(it) } + f.flush()
    }

    @Test
    fun `dusunme blogu yoksa her sey metindir`() {
        assertEquals(listOf(ChatDelta.Text("merhaba")), calistir("merhaba"))
    }

    @Test
    fun `tam blok tek parcada`() {
        assertEquals(
            listOf(ChatDelta.Reasoning("düşünüyorum"), ChatDelta.Text("cevap")),
            calistir("<think>düşünüyorum</think>cevap")
        )
    }

    @Test
    fun `acilis etiketi iki parcaya bolunmus`() {
        assertEquals(
            listOf(ChatDelta.Reasoning("x"), ChatDelta.Text("y")),
            calistir("<thi", "nk>x</think>y")
        )
    }

    @Test
    fun `kapanis etiketi iki parcaya bolunmus`() {
        assertEquals(
            listOf(ChatDelta.Reasoning("x"), ChatDelta.Text("y")),
            calistir("<think>x</thi", "nk>y")
        )
    }

    @Test
    fun `blok kapanmadan akis biterse flush kurtarir`() {
        assertEquals(
            listOf(ChatDelta.Reasoning("yarim")),
            calistir("<think>yarim")
        )
    }

    @Test
    fun `blok oncesi ve sonrasi metin korunur`() {
        assertEquals(
            listOf(ChatDelta.Text("önce"), ChatDelta.Reasoning("d"), ChatDelta.Text("sonra")),
            calistir("önce<think>d</think>sonra")
        )
    }

    @Test
    fun `kucuk parcalara bolunmus akis dogru siniflanir`() {
        val delta = calistir("<", "think", ">", "a", "b", "</think", ">", "c")
        assertEquals(
            listOf(ChatDelta.Reasoning("a"), ChatDelta.Reasoning("b"), ChatDelta.Text("c")),
            delta
        )
    }

    @Test
    fun `kucuk-e benzeyen ama etiket olmayan metin tutulmaz`() {
        assertEquals(listOf(ChatDelta.Text("a < b")), calistir("a < b"))
    }
}
