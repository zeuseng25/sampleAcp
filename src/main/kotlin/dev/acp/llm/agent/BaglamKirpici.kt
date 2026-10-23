package dev.acp.llm.agent

import dev.acp.llm.ChatMessage

/**
 * Ajan konuşmasını bağlam bütçesinde tutar (spec 008 T70).
 *
 * **Neden gerekli:** her tur, önceki tüm mesajları yeniden gönderiyor. Arama
 * sonuçları ve dosya içerikleri hızla birikiyor — T10'un başarısız koşusunda
 * konuşma 204 bin token'a çıktı. Kırpılmazsa uzun bir döngü modelin bağlam
 * sınırını aşar ve istek **tümden reddedilir**; kullanıcı "ajan çalışmadı"
 * görür, sebebini anlamaz.
 *
 * **Mesaj SİLİNMEZ, yalnızca içeriği kısaltılır.** Sebebi protokol: her `tool`
 * mesajı, kendisini isteyen `assistant` mesajının `tool_calls` girdisiyle
 * eşleşmek zorunda. Bir mesajı atmak eşleşmeyi bozar ve sunucu isteği geçersiz
 * sayar — bağlamı aşmaktan daha kötü bir hata olur.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object BaglamKirpici {

    /**
     * Karakter cinsinden bütçe.
     *
     * Token değil karakter: token sayısı modele göre değişiyor ve saymak için
     * tokenizer gerekir (yeni bağımlılık — Altın Kural 1). Karakter kaba ama
     * güvenli tarafta: Türkçe ve kodda karakter/token oranı ~3-4, yani bu bütçe
     * kabaca 15-20 bin token'a denk geliyor.
     */
    const val VARSAYILAN_BUTCE = 60_000

    /**
     * Kırpılan içeriğin yerine yazılan not.
     *
     * Modele **ne olduğu söyleniyor**: boş bırakılsaydı model sonucu unutulmuş
     * sanıp aynı aramayı tekrarlayabilirdi — tam da kaçınmaya çalıştığımız
     * kilitlenme.
     */
    const val NOT = "(eski araç sonucu yer açmak için kırpıldı; gerekirse yeniden çağır)"

    /**
     * @param korunanSonMesaj sondan bu kadar mesaja dokunulmaz — modelin son
     *   adımlarda gördüğü bilgi en değerlisi.
     */
    fun kirp(
        mesajlar: List<ChatMessage>,
        butce: Int = VARSAYILAN_BUTCE,
        korunanSonMesaj: Int = 6,
    ): List<ChatMessage> {
        if (uzunluk(mesajlar) <= butce) return mesajlar

        val sonuc = mesajlar.toMutableList()
        val dokunulmazBaslangic = (sonuc.size - korunanSonMesaj).coerceAtLeast(0)

        for (i in sonuc.indices) {
            if (uzunluk(sonuc) <= butce) break
            if (i >= dokunulmazBaslangic) break

            val m = sonuc[i]
            // Yalnızca araç sonuçları kırpılıyor: sistem yönergesi özelliğin
            // çalışmasını belirliyor (T37), kullanıcının görevi ise zaten kısa
            // ve onsuz model ne yapacağını bilemez.
            if (m.role != ChatMessage.Role.TOOL) continue
            if (m.content == NOT) continue

            sonuc[i] = m.copy(content = NOT)
        }
        return sonuc
    }

    private fun uzunluk(mesajlar: List<ChatMessage>): Int =
        mesajlar.sumOf { it.content.length + it.toolCalls.sumOf { c -> c.argumanlarJson.length } }
}
