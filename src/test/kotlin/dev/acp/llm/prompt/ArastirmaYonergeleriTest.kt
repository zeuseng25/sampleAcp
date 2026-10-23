package dev.acp.llm.prompt

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sohbet ve Uzman yönergeleri (spec 010 T43).
 *
 * 008'deki ders: yönerge bir ayrıntı değil, özelliğin kendisi. İyi niyetli bir
 * kısaltma Uzman'ı sıradan bir sohbete çevirebilir ve bu hiçbir davranış
 * testinde görünmez. Buradaki cümleler spec 010 H1'in kabul kriterleri.
 */
class ArastirmaYonergeleriTest {

    private val uzman = PromptSelector.yonerge("uzman")
    private val sohbet = PromptSelector.yonerge("sohbet-arastirma")

    @Test
    fun `uzman alti bolumu ister`() {
        listOf(
            "Ne yapıyor", "Nereyi çağırıyor", "Kim çağırıyor",
            "Mimarideki yeri", "Değiştirirsen ne olur", "Riskler ve öneriler",
        ).forEach { assertTrue(it in uzman, "eksik bölüm: $it") }
    }

    @Test
    fun `uzman once arastirir ve kaynak gosterir`() {
        assertTrue("kullanimlari_bul" in uzman && "uygulamalari_bul" in uzman && "tanima_git" in uzman)
        assertTrue("dosya:satır" in uzman, "konum kuralı yok")
        assertTrue("UYDURMA" in uzman, "uydurma yasağı yok")
        assertTrue("çıkarım" in uzman, "mimari çıkarım uyarısı yok")
    }

    @Test
    fun `uzman polimorfik siniri bilir`() {
        // T00: dilden bağımsız üst tanım aracı yok; yönerge birleştirmeyi ister.
        assertTrue("arayüz" in uzman && "uygulamalari_bul" in uzman)
    }

    @Test
    fun `sohbet kisa ve gerekmedikce arastirmaz`() {
        assertTrue("kısa" in sohbet.lowercase())
        assertTrue("gerekmiyorsa" in sohbet)
        assertTrue("kullanimlari_bul" in sohbet)
    }

    @Test
    fun `ikisi de duzenleme yapamayacagini bilir`() {
        assertTrue("değiştiremezsin" in uzman && "değiştiremezsin" in sohbet)
        assertFalse("dosya_duzenle" in uzman || "dosya_duzenle" in sohbet)
    }
}
