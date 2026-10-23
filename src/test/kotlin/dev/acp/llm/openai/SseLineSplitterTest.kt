package dev.acp.llm.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * HTTP gövdesi rastgele parçalar halinde gelir; parça sınırı satırın ortasına,
 * hatta CRLF'in ortasına düşebilir. Bu sınıfın tek işi o parçaları satırlara çevirmek.
 */
class SseLineSplitterTest {

    @Test
    fun `tek parcada tam satir`() {
        val s = SseLineSplitter()
        assertEquals(listOf("data: merhaba"), s.feed("data: merhaba\n"))
    }

    @Test
    fun `tek parcada birden fazla satir`() {
        val s = SseLineSplitter()
        assertEquals(listOf("a", "b", "c"), s.feed("a\nb\nc\n"))
    }

    @Test
    fun `satir iki parcaya bolunmus`() {
        val s = SseLineSplitter()
        assertEquals(emptyList(), s.feed("data: mer"))
        assertEquals(listOf("data: merhaba"), s.feed("haba\n"))
    }

    @Test
    fun `CRLF satir sonu temizlenir`() {
        val s = SseLineSplitter()
        assertEquals(listOf("data: x"), s.feed("data: x\r\n"))
    }

    @Test
    fun `CRLF parca sinirinda bolunmus`() {
        val s = SseLineSplitter()
        // Parça sonundaki \r tek başına satır sonu sayılmaz; \n gelene kadar beklenir.
        assertEquals(emptyList(), s.feed("data: x\r"))
        assertEquals(listOf("data: x", "data: y"), s.feed("\ndata: y\n"))
    }

    @Test
    fun `bos satir korunur cunku olay sonlandiricisidir`() {
        val s = SseLineSplitter()
        assertEquals(listOf("data: x", ""), s.feed("data: x\n\n"))
    }

    @Test
    fun `bos parca hicbir sey uretmez`() {
        val s = SseLineSplitter()
        assertEquals(emptyList(), s.feed(""))
    }

    @Test
    fun `tamamlanmamis son satir flush ile alinir`() {
        val s = SseLineSplitter()
        assertEquals(emptyList(), s.feed("data: yarim"))
        assertEquals("data: yarim", s.flush())
    }

    @Test
    fun `flush tampon bosken null doner`() {
        val s = SseLineSplitter()
        s.feed("a\n")
        assertNull(s.flush())
    }
}
