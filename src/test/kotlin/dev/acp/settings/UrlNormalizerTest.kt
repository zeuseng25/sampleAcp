package dev.acp.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 001/T20 — H1: "Adres sonundaki eğik çizgi veya `/v1` eki fark etmeksizin aynı
 * sonuca varılır."
 *
 * Neden gerekli: kullanıcıların yaklaşık yarısı adresi `/v1` ile birlikte
 * yapıştırır. Sessizce kabul etmek dört satırlık iş; etmemek "sunucuya
 * ulaşılamıyor" hatasıyla saatler kaybettirir.
 */
class UrlNormalizerTest {

    private fun normal(girdi: String) = UrlNormalizer.normalize(girdi)

    @Test
    fun `dort farkli yazim ayni sonuca gider`() {
        val beklenen = "http://sunucu:8000"
        assertEquals(beklenen, normal("http://sunucu:8000"))
        assertEquals(beklenen, normal("http://sunucu:8000/"))
        assertEquals(beklenen, normal("http://sunucu:8000/v1"))
        assertEquals(beklenen, normal("http://sunucu:8000/v1/"))
    }

    @Test
    fun `bastaki ve sondaki bosluklar kirpilir`() {
        assertEquals("http://sunucu:8000", normal("  http://sunucu:8000/v1  "))
    }

    @Test
    fun `https korunur`() {
        assertEquals("https://llm.kurum.local", normal("https://llm.kurum.local/v1/"))
    }

    @Test
    fun `yol iceren adres korunur ama v1 eki atilir`() {
        // LiteLLM çoğu zaman bir alt yol arkasında durur.
        assertEquals("https://gw.kurum.local/llm", normal("https://gw.kurum.local/llm/v1"))
        assertEquals("https://gw.kurum.local/llm", normal("https://gw.kurum.local/llm/"))
    }

    @Test
    fun `sadece sondaki v1 atilir ortadaki degil`() {
        assertEquals("https://h/v1/proxy", normal("https://h/v1/proxy"))
    }

    @Test
    fun `v1 ile biten ama ayri bir kelime olan yol korunur`() {
        // ".../apiv1" bir sürüm eki değil, yol parçasının kendisi.
        assertEquals("https://h/apiv1", normal("https://h/apiv1"))
    }

    @Test
    fun `bos girdi gecersizdir`() {
        assertNull(normal(""))
        assertNull(normal("   "))
    }

    @Test
    fun `sema yoksa gecersizdir`() {
        // Şemayı biz uydurmuyoruz; kullanıcı http mi https mi bilmeli.
        assertNull(normal("sunucu:8000"))
        assertNull(normal("localhost:30000"))
    }

    @Test
    fun `desteklenmeyen sema gecersizdir`() {
        assertNull(normal("ftp://sunucu"))
        assertNull(normal("file:///tmp"))
    }

    @Test
    fun `bozuk adres gecersizdir`() {
        assertNull(normal("http://"))
        assertNull(normal("http:// bosluk var"))
    }

    @Test
    fun `buyuk harfli sema kabul edilir`() {
        assertEquals("http://sunucu:8000", normal("HTTP://sunucu:8000/v1"))
    }
}
