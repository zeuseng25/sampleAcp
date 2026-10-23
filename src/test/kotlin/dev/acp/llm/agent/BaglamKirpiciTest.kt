package dev.acp.llm.agent

import dev.acp.llm.AracCagrisi
import dev.acp.llm.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bağlam kırpma (spec 008 T70).
 *
 * En kritik davranış: **mesaj silinmiyor**. `tool` mesajları kendilerini isteyen
 * `assistant` mesajının `tool_calls` girdisiyle eşleşmek zorunda; birini atmak
 * eşleşmeyi bozar ve sunucu isteği geçersiz sayar — bağlamı aşmaktan daha kötü.
 */
class BaglamKirpiciTest {

    private fun sistem() = ChatMessage(ChatMessage.Role.SYSTEM, "yönerge")
    private fun kullanici(m: String = "görev") = ChatMessage(ChatMessage.Role.USER, m)
    private fun asistan(id: String) = ChatMessage(
        ChatMessage.Role.ASSISTANT, "",
        toolCalls = listOf(AracCagrisi(id, "dosya_ara", """{"desen":"x"}""")),
    )
    private fun arac(id: String, icerik: String) =
        ChatMessage(ChatMessage.Role.TOOL, icerik, toolCallId = id)

    /** n turluk konuşma; her araç sonucu 1000 karakter. */
    private fun konusma(tur: Int): List<ChatMessage> = buildList {
        add(sistem()); add(kullanici())
        repeat(tur) { i ->
            add(asistan("id$i"))
            add(arac("id$i", "S".repeat(1000)))
        }
    }

    @Test
    fun `butcenin altindaysa hicbir sey degismez`() {
        val m = konusma(2)
        assertEquals(m, BaglamKirpici.kirp(m, butce = 100_000))
    }

    @Test
    fun `butce asilinca eski arac sonuclari kirpilir`() {
        val m = konusma(20)
        val k = BaglamKirpici.kirp(m, butce = 5_000)

        assertTrue(k.sumOf { it.content.length } < m.sumOf { it.content.length })
        assertTrue(k.any { it.content == BaglamKirpici.NOT }, "Hiç kırpılmamış")
    }

    @Test
    fun `MESAJ SAYISI DEGISMEZ - eslesme bozulmamali`() {
        // Bir tool mesajını atmak, onu isteyen assistant tool_calls ile
        // eşleşmeyi bozar; sunucu isteği geçersiz sayar.
        val m = konusma(20)
        assertEquals(m.size, BaglamKirpici.kirp(m, butce = 1_000).size)
    }

    @Test
    fun `rol sirasi korunur`() {
        val m = konusma(20)
        val k = BaglamKirpici.kirp(m, butce = 1_000)
        assertEquals(m.map { it.role }, k.map { it.role })
    }

    @Test
    fun `tool_call kimlikleri korunur`() {
        val m = konusma(10)
        val k = BaglamKirpici.kirp(m, butce = 1_000)
        assertEquals(m.map { it.toolCallId }, k.map { it.toolCallId })
        assertEquals(m.map { it.toolCalls }, k.map { it.toolCalls })
    }

    @Test
    fun `sistem yonergesi ASLA kirpilmaz`() {
        // Yönerge özelliğin çalışmasını belirliyor (T37); kırpılırsa ajan
        // T10'un ilk koşusundaki gibi kilitlenir.
        val m = listOf(sistem()) + konusma(30).drop(1)
        val k = BaglamKirpici.kirp(m, butce = 100)
        assertEquals("yönerge", k.first().content)
    }

    @Test
    fun `kullanici gorevi kirpilmaz`() {
        val m = konusma(30)
        val k = BaglamKirpici.kirp(m, butce = 100)
        assertEquals("görev", k[1].content)
    }

    @Test
    fun `son mesajlar korunur`() {
        // Modelin en son gördüğü bilgi en değerlisi.
        val m = konusma(20)
        val k = BaglamKirpici.kirp(m, butce = 1_000, korunanSonMesaj = 4)
        assertTrue(k.last().content != BaglamKirpici.NOT, "Son araç sonucu kırpılmış")
    }

    @Test
    fun `asistan mesajlari kirpilmaz`() {
        val m = konusma(20)
        val k = BaglamKirpici.kirp(m, butce = 500)
        val asistanlar = k.filter { it.role == ChatMessage.Role.ASSISTANT }
        assertTrue(asistanlar.none { it.content == BaglamKirpici.NOT })
    }

    @Test
    fun `kirpma notu modele ne oldugunu soyluyor`() {
        // Boş bırakılsaydı model sonucu unutulmuş sanıp aynı aramayı
        // tekrarlayabilirdi — kaçınmaya çalıştığımız kilitlenme.
        assertTrue(BaglamKirpici.NOT.isNotBlank())
        assertTrue(BaglamKirpici.NOT.contains("kırpıldı"))
    }

    @Test
    fun `bos konusma coker mi`() {
        assertEquals(emptyList(), BaglamKirpici.kirp(emptyList(), butce = 10))
    }
}
