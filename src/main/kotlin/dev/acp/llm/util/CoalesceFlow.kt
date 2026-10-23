package dev.acp.llm.util

import dev.acp.llm.ChatDelta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Ardışık metin delta'larını birleştirir; arayüzün token başına yeniden çizilmesini önler.
 *
 * Neden gerekli: H200 üzerindeki model saniyede 60–120 token üretebilir. Her token
 * için bir EDT güncellemesi saniyede 100 tur demektir; panel kullanılamaz hâle gelir
 * ve kullanıcı editörde yazamaz (spec 001 H1: "arayüz donmaz").
 *
 * [now] enjekte edilebilir — testlerin gerçek zamana veya sanal zaman altyapısına
 * bağlı kalmaması için.
 *
 * **Bilinen sınır:** tampon yalnızca yeni bir delta geldiğinde veya akış bittiğinde
 * boşalır. Model uzun süre duraklarsa tamponun görünmesi gecikir. Akış sürekli token
 * ürettiği ve akış sonunda mutlaka boşaltıldığı için v1'de kabul edilebilir.
 */
internal fun Flow<ChatDelta>.coalesceText(
    windowMs: Long = 50,
    now: () -> Long = System::currentTimeMillis,
): Flow<ChatDelta> = flow {
    val tampon = StringBuilder()
    var ilkAn = 0L

    suspend fun bosalt() {
        if (tampon.isNotEmpty()) {
            emit(ChatDelta.Text(tampon.toString()))
            tampon.setLength(0)
        }
    }

    try {
        collect { delta ->
            if (delta is ChatDelta.Text) {
                if (tampon.isEmpty()) ilkAn = now()
                tampon.append(delta.text)
                if (now() - ilkAn >= windowMs) bosalt()
            } else {
                // Metin olmayan delta (düşünme, bitiş) sırayı bozmamak için önce tamponu boşaltır.
                bosalt()
                emit(delta)
            }
        }
        bosalt()
    } catch (e: CancellationException) {
        // İptalde yayınlanmaz: toplayıcı zaten gitti, emit hata verir.
        throw e
    } catch (e: Throwable) {
        // Akış hata ile bittiğinde tamponda bekleyen metin KAYBOLMAMALI —
        // kullanıcı o ana kadar geleni görmeye devam etmeli (spec 002 H1).
        bosalt()
        throw e
    }
}
