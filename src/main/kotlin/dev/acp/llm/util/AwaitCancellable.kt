package dev.acp.llm.util

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [CompletableFuture]'ı **gerçekten iptal edilebilir** bir suspend çağrıya çevirir.
 *
 * `kotlinx-coroutines-jdk8`'in `await()`'i kullanılmıyor: o ayrı bir artifact ve
 * platformun sınıf yolunda bulunacağı garanti değil (Altın Kural 1 — jar göndermiyoruz).
 *
 * İptal zinciri şu yüzden önemli: coroutine iptal edilince `cancel(true)` çağrılır,
 * bu HTTP isteğini düşürür, sunucu bağlantı kopmasını görüp üretimi durdurur —
 * yani paylaşılan GPU boşa çalışmaz (spec 001 H1, T33).
 */
internal suspend fun <T> CompletableFuture<T>.awaitCancellable(): T =
    suspendCancellableCoroutine { continuation ->
        whenComplete { value, error ->
            if (error == null) {
                continuation.resume(value)
            } else {
                // whenComplete hatayı CompletionException'a sarar; çağıran asıl sebebi görmeli.
                continuation.resumeWithException(
                    (error as? CompletionException)?.cause ?: error
                )
            }
        }
        continuation.invokeOnCancellation { cancel(true) }
    }
