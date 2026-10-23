package dev.acp.llm.agent

import dev.acp.llm.AracCagrisi
import dev.acp.llm.AracTanimi
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatRequest
import dev.acp.llm.CompletionRequest
import dev.acp.llm.CompletionResult
import dev.acp.llm.FinishReason
import dev.acp.llm.LlmClient
import dev.acp.llm.ModelInfo
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ajan döngüsünün **hata yolları** (spec 008 T30-T38).
 *
 * Burada sahte istemci ve sahte araç kullanılıyor, çünkü test edilen şeyler
 * gerçek bir modele istendiği anda yaptırılamaz: bozuk JSON üretmesi, aynı
 * çağrıyı tekrarlaması, adım sınırına dayanması, aracın hata vermesi.
 *
 * Gerçek modelle ve gerçek araçlarla çalışan karşılığı: `GercekAjanTest`.
 */
class AjanDongusuTest {

    private val mapper = ObjectMapper()

    /** Sırayla verilen yanıtları döndüren sahte istemci. */
    private class SahteIstemci(private val turlar: List<List<ChatDelta>>) : LlmClient {
        var istekSayisi = 0; private set
        var sonIstek: ChatRequest? = null; private set

        override fun streamChat(request: ChatRequest): Flow<ChatDelta> {
            sonIstek = request
            val tur = turlar.getOrElse(istekSayisi) { emptyList() }
            istekSayisi++
            return tur.asFlow()
        }

        override suspend fun listModels(): List<ModelInfo> = emptyList()
        override suspend fun complete(request: CompletionRequest): CompletionResult =
            CompletionResult("", FinishReason.STOP)
    }

    private class SahteYurutucu(
        private val yanit: (AracCagrisi) -> String = { "tamam" },
    ) : AracYurutucu {
        val cagrilar = mutableListOf<AracCagrisi>()
        override suspend fun calistir(cagri: AracCagrisi): String {
            cagrilar += cagri
            return yanit(cagri)
        }
    }

    private fun arac() = AracTanimi("dosya_ara", "arar", mapper.createObjectNode())

    private fun cagri(ad: String = "dosya_ara", args: String = """{"desen":"x"}""") =
        ChatDelta.ToolCall(AracCagrisi("id_$ad", ad, args))

    private fun dongu(
        istemci: SahteIstemci,
        yurutucu: AracYurutucu = SahteYurutucu(),
        maxAdim: Int = 20,
    ) = AjanDongusu(istemci, yurutucu, listOf(arac()), "m", maxAdim)

    private fun kosa(d: AjanDongusu) = runBlocking { d.calistir("görev").toList() }

    // ---- T30: mutlu yol ----

    private fun bitis(olaylar: List<AjanOlayi>) = olaylar.last() as AjanOlayi.Bitti

    @Test
    fun `arac cagir sonucu besle bitir`() {
        val istemci = SahteIstemci(listOf(
            listOf(cagri()),
            listOf(ChatDelta.Text("Bitti, 1 yer güncellendi.")),
        ))
        val yurutucu = SahteYurutucu()
        val olaylar = kosa(dongu(istemci, yurutucu))

        assertEquals(1, yurutucu.cagrilar.size)
        assertEquals(BitisNedeni.TAMAMLANDI, bitis(olaylar).neden)
        assertEquals("Bitti, 1 yer güncellendi.", bitis(olaylar).mesaj)
        assertEquals(2, istemci.istekSayisi)
    }

    @Test
    fun `arac sonucu modele geri besleniyor`() {
        val istemci = SahteIstemci(listOf(listOf(cagri()), listOf(ChatDelta.Text("son"))))
        kosa(dongu(istemci, SahteYurutucu { "ARAC_SONUCU_42" }))

        val mesajlar = istemci.sonIstek!!.messages
        assertTrue(mesajlar.any { it.content == "ARAC_SONUCU_42" }, "araç sonucu gitmedi")
        assertTrue(mesajlar.any { it.toolCalls.isNotEmpty() }, "asistan çağrısı gitmedi")
    }

    @Test
    fun `sistem yonergesi her istekte gonderiliyor`() {
        val istemci = SahteIstemci(listOf(listOf(ChatDelta.Text("bitti"))))
        kosa(dongu(istemci))
        assertEquals(AjanYonergesi.METIN, istemci.sonIstek!!.messages.first().content)
    }

    @Test
    fun `araclar isteğe ekleniyor`() {
        val istemci = SahteIstemci(listOf(listOf(ChatDelta.Text("bitti"))))
        kosa(dongu(istemci))
        assertEquals(1, istemci.sonIstek!!.tools.size)
    }

    @Test
    fun `birden fazla cagri ayni turda calistirilir`() {
        // T10'da model 4 düzenlemeyi tek mesajda gönderdi.
        val istemci = SahteIstemci(listOf(
            listOf(cagri("a", "{}"), cagri("b", "{}"), cagri("c", "{}")),
            listOf(ChatDelta.Text("bitti")),
        ))
        val y = SahteYurutucu()
        kosa(dongu(istemci, y))
        assertEquals(listOf("a", "b", "c"), y.cagrilar.map { it.ad })
    }

    // ---- T31: adım sınırı ----

    @Test
    fun `adim sinirina ulasinca durur`() {
        // Model hiç durmuyor: her turda araç çağırıyor.
        val sonsuz = (1..50).map { listOf(cagri(args = """{"desen":"$it"}""")) }
        val istemci = SahteIstemci(sonsuz)

        val olaylar = kosa(dongu(istemci, maxAdim = 3))

        assertEquals(BitisNedeni.ADIM_SINIRI, bitis(olaylar).neden)
        assertEquals(3, istemci.istekSayisi)
    }

    // ---- T38: tekrar kilitlenmesi ----

    @Test
    fun `ayni cagri tekrarlanirsa dongu kirilir`() {
        // T10'un ilk koşusunda gerçekleşen davranış: aynı arama, defalarca.
        val ayni = (1..10).map { listOf(cagri()) }
        val istemci = SahteIstemci(ayni)

        val olaylar = kosa(dongu(istemci, maxAdim = 10))

        assertEquals(BitisNedeni.TEKRAR, bitis(olaylar).neden)
        // İkinci turda yakalanmalı; 10 tur boşa harcanmamalı.
        assertEquals(2, istemci.istekSayisi)
    }

    @Test
    fun `farkli argumanla ayni arac tekrar sayilmaz`() {
        // Meşru davranış: aynı aracı farklı desenle aramak.
        val istemci = SahteIstemci(listOf(
            listOf(cagri(args = """{"desen":"a"}""")),
            listOf(cagri(args = """{"desen":"b"}""")),
            listOf(ChatDelta.Text("bitti")),
        ))
        assertEquals(BitisNedeni.TAMAMLANDI, bitis(kosa(dongu(istemci))).neden)
    }

    // ---- T33/T34/T35: araç hataları döngüyü çökertmez ----

    @Test
    fun `arac istisna firlatirsa modele bildirilir`() {
        val istemci = SahteIstemci(listOf(
            listOf(cagri()),
            listOf(ChatDelta.Text("anladım, düzelttim")),
        ))
        val patlayan = object : AracYurutucu {
            override suspend fun calistir(cagri: AracCagrisi): String =
                throw IllegalStateException("dosya kilitli")
        }

        val olaylar = kosa(dongu(istemci, patlayan))

        assertEquals(BitisNedeni.TAMAMLANDI, bitis(olaylar).neden)
        val aracMesaji = istemci.sonIstek!!.messages.last()
        assertTrue(aracMesaji.content.contains("HATA"), aracMesaji.content)
        assertTrue(aracMesaji.content.contains("dosya kilitli"), aracMesaji.content)
    }

    @Test
    fun `bilinmeyen arac adi cokme yapmaz`() {
        val istemci = SahteIstemci(listOf(
            listOf(cagri("boyle_bir_arac_yok", "{}")),
            listOf(ChatDelta.Text("tamam")),
        ))
        val y = SahteYurutucu { c ->
            if (c.ad == "dosya_ara") "ok" else throw IllegalArgumentException("bilinmeyen araç")
        }
        assertEquals(BitisNedeni.TAMAMLANDI, bitis(kosa(dongu(istemci, y))).neden)
    }

    @Test
    fun `bozuk arguman JSONu yurutucuya oldugu gibi gider`() {
        // Ayrıştırma yürütücünün işi; döngü ham metni taşır.
        val istemci = SahteIstemci(listOf(
            listOf(cagri(args = "{bozuk json")),
            listOf(ChatDelta.Text("son")),
        ))
        val y = SahteYurutucu()
        kosa(dongu(istemci, y))
        assertEquals("{bozuk json", y.cagrilar.single().argumanlarJson)
    }

    // ---- T32: iptal ----

    @Test
    fun `akis erken kapatilirsa sonraki istek gitmez`() {
        val sonsuz = (1..50).map { listOf(cagri(args = """{"n":"$it"}""")) }
        val istemci = SahteIstemci(sonsuz)

        // İlk iki olayı alıp bırakıyoruz — kullanıcının durdurmasının karşılığı.
        runBlocking { dongu(istemci).calistir("görev").take(2).toList() }

        assertTrue(istemci.istekSayisi <= 2, "durdurulduktan sonra istek gitti: ${istemci.istekSayisi}")
    }

    // ---- ilerleme olayları (H3) ----

    @Test
    fun `arac cagrilari olay olarak bildirilir`() {
        val istemci = SahteIstemci(listOf(listOf(cagri()), listOf(ChatDelta.Text("bitti"))))
        val olaylar = kosa(dongu(istemci))

        val cagriOlayi = olaylar.filterIsInstance<AjanOlayi.AracCagirdi>().single()
        assertEquals("dosya_ara", cagriOlayi.ad)
        assertTrue(cagriOlayi.ozet.contains("desen"), cagriOlayi.ozet)
    }

    @Test
    fun `uzun argumanlar ozette kirpilir`() {
        val uzun = """{"desen":"${"x".repeat(300)}"}"""
        val istemci = SahteIstemci(listOf(listOf(cagri(args = uzun)), listOf(ChatDelta.Text("b"))))
        val ozet = kosa(dongu(istemci)).filterIsInstance<AjanOlayi.AracCagirdi>().single().ozet
        assertTrue(ozet.length <= 80, "özet kırpılmamış: ${ozet.length}")
    }

    @Test
    fun `model hic arac cagirmazsa tek turda biter`() {
        val istemci = SahteIstemci(listOf(listOf(ChatDelta.Text("Böyle bir kullanım bulunamadı."))))
        val olaylar = kosa(dongu(istemci))

        assertEquals(BitisNedeni.TAMAMLANDI, bitis(olaylar).neden)
        assertEquals("Böyle bir kullanım bulunamadı.", bitis(olaylar).mesaj)
        assertEquals(1, istemci.istekSayisi)
    }
}
