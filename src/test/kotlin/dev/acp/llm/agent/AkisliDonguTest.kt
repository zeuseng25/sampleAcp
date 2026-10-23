package dev.acp.llm.agent

import com.fasterxml.jackson.databind.ObjectMapper
import dev.acp.llm.AracCagrisi
import dev.acp.llm.AracTanimi
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.CompletionRequest
import dev.acp.llm.CompletionResult
import dev.acp.llm.FinishReason
import dev.acp.llm.LlmClient
import dev.acp.llm.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Akışlı döngü — sohbet ve Uzman kipi bunu kullanır (spec 010 T10-T11).
 *
 * Bir turun araç çağırıp çağırmayacağı akış bitmeden bilinmez. Metin hemen
 * akar; aynı turda araç çağrısı gelirse [AjanOlayi.MetinGeriAl] yayılır ve
 * tüketici o turun metnini cevaptan çıkarır (T00 kararı: sunucunun parça
 * sırasına bağımlı değil).
 */
class AkisliDonguTest {

    private val mapper = ObjectMapper()

    private class SahteIstemci(private val turlar: List<List<ChatDelta>>) : LlmClient {
        val istekler = mutableListOf<ChatRequest>()
        override fun streamChat(request: ChatRequest): Flow<ChatDelta> {
            istekler += request
            return turlar.getOrElse(istekler.size - 1) { emptyList() }.asFlow()
        }
        override suspend fun listModels(): List<ModelInfo> = emptyList()
        override suspend fun complete(request: CompletionRequest) = CompletionResult("", FinishReason.STOP)
    }

    private val yurutucu = object : AracYurutucu {
        override suspend fun calistir(cagri: AracCagrisi) = "sonuç"
    }

    private fun cagri() = ChatDelta.ToolCall(AracCagrisi("c1", "dosya_ara", """{"desen":"x"}"""))

    private fun dongu(istemci: SahteIstemci, akisli: Boolean = true, hazirla: (ChatRequest) -> ChatRequest = { it }) =
        AjanDongusu(
            istemci, yurutucu, listOf(AracTanimi("dosya_ara", "arar", mapper.createObjectNode())), "m",
            akisli = akisli, istekHazirla = hazirla,
        )

    private fun kosa(d: AjanDongusu, gecmis: List<ChatMessage> = emptyList()) =
        runBlocking { d.calistir("soru", gecmis).toList() }

    @Test
    fun `son turun metni parca parca akar`() {
        val olaylar = kosa(dongu(SahteIstemci(listOf(listOf(ChatDelta.Text("Mer"), ChatDelta.Text("haba"))))))
        // Parçalar birleştirilebilir (token başına olay paneli kilitler, spec 001 H1);
        // önemli olan metnin eksiksiz ve araçsız turda akması.
        assertEquals("Merhaba", olaylar.filterIsInstance<AjanOlayi.MetinParcasi>().joinToString("") { it.metin })
        assertTrue(olaylar.none { it is AjanOlayi.MetinGeriAl })
    }

    @Test
    fun `aracli turun metni geri alinir`() {
        val olaylar = kosa(dongu(SahteIstemci(listOf(
            listOf(ChatDelta.Text("Önce arayayım."), cagri()),
            listOf(ChatDelta.Text("Cevap")),
        ))))
        val sira = olaylar.map { it.javaClass.simpleName }
        // Ara metin aktı, sonra geri alındı, sonra araç, sonra asıl cevap.
        assertTrue(sira.indexOf("MetinGeriAl") < sira.indexOf("AracCagirdi"), sira.toString())
        assertEquals("Cevap", olaylar.filterIsInstance<AjanOlayi.MetinParcasi>().last().metin)
        assertTrue(olaylar.none { it is AjanOlayi.Dusunuyor }, "akışlı kipte metin iki kez gösterildi")
    }

    @Test
    fun `arac cagrisi metinden once gelse de metin geri alinir`() {
        val olaylar = kosa(dongu(SahteIstemci(listOf(
            listOf(cagri(), ChatDelta.Text("arıyorum")),
            listOf(ChatDelta.Text("Cevap")),
        ))))
        assertEquals(1, olaylar.count { it is AjanOlayi.MetinGeriAl })
    }

    @Test
    fun `dusunme parcalari ayri akar`() {
        val olaylar = kosa(dongu(SahteIstemci(listOf(listOf(ChatDelta.Reasoning("hmm"), ChatDelta.Text("x"))))))
        assertEquals(listOf("hmm"), olaylar.filterIsInstance<AjanOlayi.DusunceParcasi>().map { it.metin })
    }

    @Test
    fun `gecmis sistem ile soru arasina girer`() {
        val istemci = SahteIstemci(listOf(listOf(ChatDelta.Text("x"))))
        val onceki = listOf(
            ChatMessage(ChatMessage.Role.USER, "önceki soru"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "önceki cevap"),
        )
        kosa(dongu(istemci), onceki)
        val roller = istemci.istekler.single().messages.map { it.role to it.content }
        assertEquals(ChatMessage.Role.SYSTEM, roller[0].first)
        assertEquals(onceki.map { it.role to it.content }, roller.subList(1, 3))
        assertEquals(ChatMessage.Role.USER to "soru", roller[3])
    }

    @Test
    fun `istek hazirlayici her istege uygulanir`() {
        val istemci = SahteIstemci(listOf(listOf(cagri()), listOf(ChatDelta.Text("x"))))
        kosa(dongu(istemci, hazirla = { it.copy(temperature = 0.1) }))
        assertTrue(istemci.istekler.size == 2 && istemci.istekler.all { it.temperature == 0.1 })
    }

    @Test
    fun `adim sinirinda aracsiz son tur kismi cevap verir`() {
        // Gözden geçirme bulgusu 6: sınırda bütün bulgular atılıyor, kullanıcıya
        // yalnızca "sınıra ulaşıldı" kalıyordu.
        val istemci = object : LlmClient {
            val istekler = mutableListOf<ChatRequest>()
            override fun streamChat(request: ChatRequest): Flow<ChatDelta> {
                istekler += request
                val n = istekler.size
                return if (request.tools.isEmpty()) {
                    listOf(ChatDelta.Text("Kısmi cevap")).asFlow()
                } else {
                    listOf(ChatDelta.ToolCall(AracCagrisi("c$n", "dosya_ara", """{"desen":"$n"}"""))).asFlow()
                }
            }
            override suspend fun listModels(): List<ModelInfo> = emptyList()
            override suspend fun complete(request: CompletionRequest) = CompletionResult("", FinishReason.STOP)
        }
        val d = AjanDongusu(
            istemci, yurutucu, listOf(AracTanimi("dosya_ara", "arar", mapper.createObjectNode())), "m",
            maxAdim = 2, akisli = true,
        )
        val olaylar = runBlocking { d.calistir("soru").toList() }

        assertEquals(3, istemci.istekler.size)
        val son = istemci.istekler.last()
        assertTrue(son.tools.isEmpty(), "son turda araç verildi")
        assertEquals(ChatMessage.Role.USER, son.messages.last().role)
        assertTrue("TOOL" in son.messages.map { it.role.name }, "son araç sonuçları son tura gitmedi")
        assertEquals("Kısmi cevap", olaylar.filterIsInstance<AjanOlayi.MetinParcasi>().joinToString("") { it.metin })
        assertEquals(BitisNedeni.ADIM_SINIRI, (olaylar.last() as AjanOlayi.Bitti).neden)
    }

    // ---- T11: varsayılan kip 008 ile birebir ----

    @Test
    fun `akissiz kipte yeni olay yok`() {
        val olaylar = kosa(dongu(SahteIstemci(listOf(
            listOf(ChatDelta.Reasoning("r"), ChatDelta.Text("ara"), cagri()),
            listOf(ChatDelta.Text("son")),
        )), akisli = false))
        assertFalse(olaylar.any {
            it is AjanOlayi.MetinParcasi || it is AjanOlayi.MetinGeriAl || it is AjanOlayi.DusunceParcasi
        })
        assertEquals(
            listOf("Dusunuyor", "AracCagirdi", "Dusunuyor", "Bitti"),
            olaylar.map { it.javaClass.simpleName },
        )
    }
}
