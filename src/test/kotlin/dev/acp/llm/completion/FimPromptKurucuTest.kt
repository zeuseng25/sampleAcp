package dev.acp.llm.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * FIM belirteçleri model ailesine göre değişiyor. Yanlış biçim gönderilirse
 * model belirteçleri **düz metin** sanar ve hata vermeden kötü öneri üretir —
 * bu yüzden her biçimin belirteçleri birebir doğrulanıyor.
 *
 * T10'da gerçek modele (`qwen/qwen3.8-27b`) Qwen biçimiyle istek atıldı ve
 * `matched_stop` ile yanıt geldi: belirteçler tanınıyor.
 */
class FimPromptKurucuTest {

    private val oncesi = "fun topla(a: Int, b: Int): Int {\n    return "
    private val sonrasi = "\n}"

    @Test
    fun `qwen bicimi`() {
        assertEquals(
            "<|fim_prefix|>$oncesi<|fim_suffix|>$sonrasi<|fim_middle|>",
            FimPromptKurucu.kur(FimBicimi.QWEN, oncesi, sonrasi),
        )
    }

    @Test
    fun `deepseek bicimi tam genislikli karakterler kullanir`() {
        val p = FimPromptKurucu.kur(FimBicimi.DEEPSEEK, oncesi, sonrasi)
        assertEquals("<｜fim▁begin｜>$oncesi<｜fim▁hole｜>$sonrasi<｜fim▁end｜>", p)
        // Sıradan ASCII boru işareti kullanılırsa model belirteci tanımaz.
        assertTrue(p.contains('｜'), "DeepSeek tam genişlikli ｜ kullanır, ASCII | değil")
    }

    @Test
    fun `starcoder bicimi`() {
        assertEquals(
            "<fim_prefix>$oncesi<fim_suffix>$sonrasi<fim_middle>",
            FimPromptKurucu.kur(FimBicimi.STARCODER, oncesi, sonrasi),
        )
    }

    @Test
    fun `codellama bicimi bosluklu`() {
        assertEquals(
            "<PRE> $oncesi <SUF>$sonrasi <MID>",
            FimPromptKurucu.kur(FimBicimi.CODELLAMA, oncesi, sonrasi),
        )
    }

    @Test
    fun `kapali bicim prompt kurmaz`() {
        assertFailsWith<IllegalStateException> {
            FimPromptKurucu.kur(FimBicimi.KAPALI, oncesi, sonrasi)
        }
    }

    @Test
    fun `sohbet bicimi ham prompt kurmaz`() {
        assertFailsWith<IllegalStateException> {
            FimPromptKurucu.kur(FimBicimi.SOHBET, oncesi, sonrasi)
        }
    }

    @Test
    fun `bos sonek de gecerli - dosya sonunda yaziliyor olabilir`() {
        assertEquals(
            "<|fim_prefix|>merhaba<|fim_suffix|><|fim_middle|>",
            FimPromptKurucu.kur(FimBicimi.QWEN, "merhaba", ""),
        )
    }

    @Test
    fun `ham uc yalnizca gercek fim bicimlerinde kullanilir`() {
        assertTrue(FimBicimi.QWEN.hamUcKullanir)
        assertTrue(FimBicimi.DEEPSEEK.hamUcKullanir)
        assertTrue(FimBicimi.STARCODER.hamUcKullanir)
        assertTrue(FimBicimi.CODELLAMA.hamUcKullanir)
        assertTrue(!FimBicimi.SOHBET.hamUcKullanir, "sohbet biçimi /v1/chat/completions kullanır")
        assertTrue(!FimBicimi.KAPALI.hamUcKullanir, "kapalıyken hiç istek gitmez")
    }

    @Test
    fun `varsayilan bicim kapalidir`() {
        // Spec 006 H3: kod kendiliğinden dışarı gitmesin diye ilk deger KAPALI.
        assertEquals(FimBicimi.KAPALI, FimBicimi.entries.first())
    }
}
