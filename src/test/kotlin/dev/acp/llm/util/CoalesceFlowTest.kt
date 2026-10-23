package dev.acp.llm.util

import dev.acp.llm.ChatDelta
import dev.acp.llm.FinishReason
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** T19. Saat enjekte edildiği için testler zamana bağlı değil. */
class CoalesceFlowTest {

    private var saat = 0L

    @Test
    fun `ardisik metinler tek deltada birlesir`() = runBlocking {
        val sonuc = flowOf(ChatDelta.Text("a"), ChatDelta.Text("b"), ChatDelta.Text("c"))
            .coalesceText(windowMs = 50) { saat }
            .toList()
        assertEquals(listOf(ChatDelta.Text("abc")), sonuc)
    }

    @Test
    fun `pencere dolunca tampon bosalir`() = runBlocking {
        val sonuc = flow {
            emit(ChatDelta.Text("a"))
            saat = 60 // pencere aşıldı
            emit(ChatDelta.Text("b"))
            emit(ChatDelta.Text("c"))
        }.coalesceText(windowMs = 50) { saat }.toList()

        // "a"+"b" pencere dolduğu için birlikte, "c" akış sonunda.
        assertEquals(listOf(ChatDelta.Text("ab"), ChatDelta.Text("c")), sonuc)
    }

    @Test
    fun `metin olmayan delta once tamponu bosaltir`() = runBlocking {
        val sonuc = flowOf(
            ChatDelta.Text("yanıt"),
            ChatDelta.Finish(FinishReason.STOP),
        ).coalesceText(windowMs = 50) { saat }.toList()

        assertEquals(
            listOf(ChatDelta.Text("yanıt"), ChatDelta.Finish(FinishReason.STOP)),
            sonuc,
        )
    }

    @Test
    fun `dusunme deltasi metinle karismaz`() = runBlocking {
        val sonuc = flowOf(
            ChatDelta.Reasoning("düşün"),
            ChatDelta.Text("cevap"),
        ).coalesceText(windowMs = 50) { saat }.toList()

        assertEquals(
            listOf(ChatDelta.Reasoning("düşün"), ChatDelta.Text("cevap")),
            sonuc,
        )
    }

    @Test
    fun `akis hata ile biterse tamponda bekleyen metin yine de yayinlanir`() = runBlocking {
        // Spec 002: "Bağlantı yanıt ortasında koparsa o ana kadar gelen metin
        // kaybolmaz." Tampon yalnızca normal bitişte boşaltılsaydı, pencere
        // dolmadan kopan akışta gelen her şey yok olurdu.
        val alinan = mutableListOf<ChatDelta>()
        val hata = assertFailsWith<IllegalStateException> {
            flow<ChatDelta> {
                emit(ChatDelta.Text("yarim"))
                error("baglanti koptu")
            }.coalesceText(windowMs = 50) { saat }.toList(alinan)
        }

        assertEquals("baglanti koptu", hata.message)
        assertEquals(listOf<ChatDelta>(ChatDelta.Text("yarim")), alinan.toList())
    }

    @Test
    fun `bos akis bos doner`() = runBlocking {
        assertEquals(emptyList(), flowOf<ChatDelta>().coalesceText(50) { saat }.toList())
    }

    @Test
    fun `yuz token 50ms penceresinde az sayida deltaya iner`() = runBlocking {
        // H1 "arayüz donmaz" kriterinin sayısal karşılığı.
        val sonuc = flow {
            repeat(100) { i ->
                saat = i.toLong() // token başına 1ms — gerçekçi üretim hızı
                emit(ChatDelta.Text("t"))
            }
        }.coalesceText(windowMs = 50) { saat }.toList()

        assertTrue(sonuc.size <= 3, "100 token ${sonuc.size} deltaya indi, beklenen ≤3")
        assertEquals("t".repeat(100), sonuc.joinToString("") { (it as ChatDelta.Text).text })
    }
}
