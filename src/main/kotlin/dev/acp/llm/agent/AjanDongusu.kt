package dev.acp.llm.agent

import dev.acp.llm.AracCagrisi
import dev.acp.llm.AracTanimi
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.LlmClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import dev.acp.llm.util.coalesceText

/** Aracı çalıştırır; dönen metin modele geri verilir. */
internal interface AracYurutucu {
    suspend fun calistir(cagri: AracCagrisi): String
}

/** Döngünün neden bittiği. */
internal enum class BitisNedeni {
    /** Model işi bitirdi ve araç çağırmayı bıraktı. */
    TAMAMLANDI,

    /** Adım sınırına ulaşıldı. */
    ADIM_SINIRI,

    /** Model aynı çağrıları tekrarladı — kilitlenme. */
    TEKRAR,
}

/** Kullanıcıya gösterilen adım adım ilerleme (spec 008 H3). */
internal sealed interface AjanOlayi {
    data class Dusunuyor(val metin: String) : AjanOlayi
    data class AracCagirdi(val ad: String, val ozet: String) : AjanOlayi
    data class Bitti(val neden: BitisNedeni, val mesaj: String?) : AjanOlayi

    // ---- yalnızca akışlı kipte (spec 010) ----

    /** Cevap metninin bir parçası; geldiği anda. */
    data class MetinParcasi(val metin: String) : AjanOlayi

    /** Modelin düşünme bölümünün bir parçası. */
    data class DusunceParcasi(val metin: String) : AjanOlayi

    /** Bu turda akan metin cevap değilmiş (tur araç çağırdı); tüketici cevaptan çıkarır. */
    data object MetinGeriAl : AjanOlayi
}

/**
 * Araç çağırma döngüsü (spec 008).
 *
 * **Saf.** [client] ve [yurutucu] enjekte edilir; ikisi de sahtelenebildiği için
 * hata yolları (iptal, adım sınırı, bozuk JSON, araç hatası) IDE başlatmadan ve
 * gerçek modele bağlanmadan test edilebilir. Bunlar gerçek bir modele istendiği
 * anda yaptırılamayacak durumlar.
 *
 * Döngü **hiçbir dosyaya yazmaz**; düzenlemeleri [yurutucu] biriktirir. Spec'in
 * "ya tamamen ya hiç" kuralı buna dayanıyor.
 */
internal class AjanDongusu(
    private val client: LlmClient,
    private val yurutucu: AracYurutucu,
    private val araclar: List<AracTanimi>,
    private val model: String,
    private val maxAdim: Int = 20,
    private val yonerge: String = AjanYonergesi.METIN,
    private val baglamButcesi: Int = BaglamKirpici.VARSAYILAN_BUTCE,
    /**
     * Sohbet için: metin geldiği anda [AjanOlayi.MetinParcasi] olarak akar.
     * Kapalıyken (008) olaylar birebir eskisi gibi.
     */
    private val akisli: Boolean = false,
    /** Sohbetin istek ayarları (sıcaklık, düşünme) her isteğe uygulanır. */
    private val istekHazirla: (ChatRequest) -> ChatRequest = { it },
) {

    /** @param gecmis sohbetin önceki turları; sistem yönergesi ile görev arasına girer. */
    fun calistir(gorev: String, gecmis: List<ChatMessage> = emptyList()): Flow<AjanOlayi> = flow {
        val mesajlar = mutableListOf(ChatMessage(ChatMessage.Role.SYSTEM, yonerge))
        mesajlar += gecmis
        mesajlar += ChatMessage(ChatMessage.Role.USER, gorev)
        var oncekiImza: String? = null

        repeat(maxAdim) {
            // Konuşma her turda büyüyor; kırpılmazsa uzun bir döngü bağlam
            // sınırını aşar ve istek TÜMDEN reddedilir (spec 008 T70).
            val gonderilecek = BaglamKirpici.kirp(mesajlar, baglamButcesi)

            val istek = istekHazirla(ChatRequest(model = model, messages = gonderilecek, tools = araclar))
            val metinB = StringBuilder()
            val cagrilar = mutableListOf<dev.acp.llm.AracCagrisi>()
            // Akışlı kipte token başına olay yayılmasın; panel kilitlenir (spec 001 H1).
            val akis = client.streamChat(istek).let { if (akisli) it.coalesceText() else it }
            akis.collect { d ->
                when (d) {
                    is ChatDelta.Text -> {
                        metinB.append(d.text)
                        if (akisli) emit(AjanOlayi.MetinParcasi(d.text))
                    }
                    is ChatDelta.Reasoning -> if (akisli) emit(AjanOlayi.DusunceParcasi(d.text))
                    is ChatDelta.ToolCall -> cagrilar += d.cagri
                    else -> Unit
                }
            }
            val metin = metinB.toString()

            if (akisli) {
                // Araç çağıran turun metni cevap değildir; sıradan bağımsız (T00).
                if (cagrilar.isNotEmpty() && metin.isNotEmpty()) emit(AjanOlayi.MetinGeriAl)
            } else if (metin.isNotBlank()) {
                emit(AjanOlayi.Dusunuyor(metin))
            }

            // Araç çağırmadıysa iş bitmiştir.
            if (cagrilar.isEmpty()) {
                emit(AjanOlayi.Bitti(BitisNedeni.TAMAMLANDI, metin.ifBlank { null }))
                return@flow
            }

            /*
             * Kilitlenme koruması (T38).
             *
             * Sistem yönergesi tekrarı azaltıyor ama garanti etmiyor. T10'un ilk
             * koşusunda model aynı aramayı 12 kez yaptı ve 204 bin token harcadı.
             * Yönergeye güvenip bu sayacı atlamak, "model iyi davranır"
             * varsayımına bahse girmek olurdu.
             */
            val imza = cagrilar.joinToString("|") { "${it.ad}(${it.argumanlarJson})" }
            if (imza == oncekiImza) {
                emit(AjanOlayi.Bitti(BitisNedeni.TEKRAR, null))
                return@flow
            }
            oncekiImza = imza

            mesajlar += ChatMessage(ChatMessage.Role.ASSISTANT, metin, toolCalls = cagrilar)

            for (cagri in cagrilar) {
                emit(AjanOlayi.AracCagirdi(cagri.ad, ozetle(cagri.argumanlarJson)))
                /*
                 * Araç hatası modele BİLDİRİLİR, döngüyü çökertmez.
                 *
                 * Model geçersiz argüman üretebilir, olmayan dosya isteyebilir.
                 * Bunlar protokol hatası değil, modelin düzeltebileceği
                 * durumlar — hata metnini görünce genellikle düzeltiyor.
                 */
                val sonuc = try {
                    yurutucu.calistir(cagri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    "HATA: ${e.javaClass.simpleName}: ${e.message}"
                }
                mesajlar += ChatMessage(ChatMessage.Role.TOOL, sonuc, toolCallId = cagri.id)
            }
        }
        if (!akisli) {
            emit(AjanOlayi.Bitti(BitisNedeni.ADIM_SINIRI, null))
            return@flow
        }

        /*
         * Sohbet/Uzman: sınırda bulgular atılmasın (gözden geçirme bulgusu 6).
         * Araçsız bir son tur — model son araç sonuçlarını da görür ve eldekiyle,
         * eksik olduğunu belirterek cevap verir. 008 (akışsız) bundan etkilenmez.
         */
        mesajlar += ChatMessage(ChatMessage.Role.USER, SON_TUR_ISTEGI)
        val son = istekHazirla(
            ChatRequest(model = model, messages = BaglamKirpici.kirp(mesajlar, baglamButcesi), tools = emptyList()),
        )
        val sonMetin = StringBuilder()
        client.streamChat(son).coalesceText().collect { d ->
            when (d) {
                is ChatDelta.Text -> { sonMetin.append(d.text); emit(AjanOlayi.MetinParcasi(d.text)) }
                is ChatDelta.Reasoning -> emit(AjanOlayi.DusunceParcasi(d.text))
                else -> Unit
            }
        }
        emit(AjanOlayi.Bitti(BitisNedeni.ADIM_SINIRI, sonMetin.toString().ifBlank { null }))
    }

    private companion object {
        const val SON_TUR_ISTEGI =
            "Araştırma adım sınırına ulaştı; daha fazla araç kullanamazsın. Şu ana kadar " +
                "topladığın bilgiyle cevap ver ve cevabın eksik olabileceğini belirt."
    }

    /** Kullanıcıya gösterilecek kısa özet; tam argümanlar ekrana sığmaz. */
    private fun ozetle(json: String): String =
        json.replace(Regex("\\s+"), " ").let { if (it.length > 80) it.take(77) + "…" else it }
}
