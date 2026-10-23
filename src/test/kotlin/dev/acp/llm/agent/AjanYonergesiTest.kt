package dev.acp.llm.agent

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ajan yönergesi sabit (spec 008 T37, spec 009 T41).
 *
 * `AjanYonergesi`'nin belgesi bu testi anıyordu ama test yoktu; iyi niyetli bir
 * kısaltma özelliği çalışmaz hâle getirebilir ve bu hiçbir davranış testinde
 * görünmez (T10: yönergesiz 0 düzenleme, yönergeyle 9).
 */
class AjanYonergesiTest {

    private val m = AjanYonergesi.METIN

    @Test
    fun `008 kurallari yerinde`() {
        assertTrue("TEKRARLAMA" in m)
        assertTrue("dosyayı okuman GEREKMEZ" in m)
        assertTrue("dosya_duzenle" in m)
        assertTrue("TAMAMINI" in m)
        assertTrue("DUR" in m)
    }

    @Test
    fun `009 dogrula yalnizca hata bildirilince ve diskteki kod`() {
        assertTrue("dogrula" in m)
        assertTrue("hatası bildirdiyse" in m, "tetikleyici yok")
        assertTrue("görevde zaten varsa" in m, "yapıştırılmış hata için çağırmama kuralı yok")
        assertTrue("diskteki" in m, "komutun diskteki kodu derlediği söylenmiyor")
    }

    @Test
    fun `ortam hatasi kodla duzeltilmeye calisilmaz`() {
        // T90 bulgusu: "release version 21 not supported" — ajan kodu değiştirmeye uğraştı.
        assertTrue("kodda değilse" in m && "kullanıcıya söyle" in m)
    }
}
