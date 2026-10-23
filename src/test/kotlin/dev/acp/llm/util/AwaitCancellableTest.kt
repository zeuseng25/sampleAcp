package dev.acp.llm.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** T18. */
class AwaitCancellableTest {

    @Test
    fun `tamamlanan future degerini doner`() = runBlocking {
        val future = CompletableFuture.completedFuture("selam")
        assertEquals("selam", future.awaitCancellable())
    }

    @Test
    fun `sonradan tamamlanan future beklenir`() = runBlocking {
        val future = CompletableFuture<String>()
        val bekleyen = async(Dispatchers.Default) { future.awaitCancellable() }
        future.complete("geç geldi")
        assertEquals("geç geldi", bekleyen.await())
    }

    @Test
    fun `hata CompletionException sarmalayicisi olmadan yuzeye cikar`() = runBlocking {
        val future = CompletableFuture<String>()
        future.completeExceptionally(IOException("ağ koptu"))
        val hata = assertFailsWith<IOException> { future.awaitCancellable() }
        assertEquals("ağ koptu", hata.message)
    }

    @Test
    fun `job iptal edilince future da iptal edilir`() = runBlocking {
        val future = CompletableFuture<String>() // asla tamamlanmaz
        val basladi = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.Default) {
            basladi.complete(Unit)
            future.awaitCancellable()
        }

        basladi.await()
        // whenComplete kaydı yapılana kadar bekle — yarış koşulu olmasın.
        withTimeout(2_000) {
            while (future.numberOfDependents == 0) delay(1)
        }

        job.cancelAndJoin()

        // Bu, iptalin sunucuya kadar ulaşmasının ilk halkası (bkz. T33).
        assertTrue(future.isCancelled, "coroutine iptal edildi ama future iptal edilmedi")
    }
}
