package dev.acp.llm.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 003/T11. */
class PromptSelectorTest {

    private val sahteKaynaklar = mapOf(
        "/prompts/test-yaz.md" to "genel test şablonu",
        "/prompts/test-yaz.kt.md" to "kotlin test şablonu",
        "/prompts/acikla.md" to "açıkla şablonu",
    )

    private fun yukle(yol: String): String? = sahteKaynaklar[yol]

    @Test
    fun `dile ozgu sablon varsa o secilir`() {
        assertEquals(
            "kotlin test şablonu",
            PromptSelector.sablon(AksiyonTuru.TEST_YAZ, "kt", ::yukle),
        )
    }

    @Test
    fun `dile ozgu sablon yoksa genele dusulur`() {
        assertEquals(
            "genel test şablonu",
            PromptSelector.sablon(AksiyonTuru.TEST_YAZ, "go", ::yukle),
        )
    }

    @Test
    fun `dil verilmezse genel sablon`() {
        assertEquals(
            "genel test şablonu",
            PromptSelector.sablon(AksiyonTuru.TEST_YAZ, null, ::yukle),
        )
        assertEquals(
            "genel test şablonu",
            PromptSelector.sablon(AksiyonTuru.TEST_YAZ, "  ", ::yukle),
        )
    }

    @Test
    fun `dil buyuk harfle gelse de bulunur`() {
        assertEquals(
            "kotlin test şablonu",
            PromptSelector.sablon(AksiyonTuru.TEST_YAZ, "KT", ::yukle),
        )
    }

    @Test
    fun `sablon hic yoksa yuksek sesle patlar`() {
        // Sessizce boş prompt göndermek en kötü sonuç olurdu.
        assertFailsWith<IllegalStateException> {
            PromptSelector.sablon(AksiyonTuru.YENIDEN_DUZENLE, null, ::yukle)
        }
    }

    // ---- gerçek paket içeriği ----------------------------------------------

    @Test
    fun `her aksiyon turu icin sablon pakette var`() {
        // 003/T10: dosyaların gerçekten pakete girdiğini doğrular.
        for (tur in AksiyonTuru.entries) {
            val sablon = PromptSelector.sablon(tur, null)
            assertTrue(sablon.isNotBlank(), "${tur.dosyaAdi} şablonu boş")
        }
    }

    @Test
    fun `kodu degistiren eylemler tek kod blogu istiyor`() {
        // Uygulama zinciri ilk kod bloğunu ayıklıyor; şablon bunu söylemezse
        // model açıklama ile kod karıştırır ve yanlış şey uygulanır.
        for (tur in AksiyonTuru.entries.filter { it.editoruDegistirir }) {
            val sablon = PromptSelector.sablon(tur, null)
            assertTrue(
                sablon.contains("tek bir kod bloğu"),
                "${tur.dosyaAdi} şablonu tek kod bloğu istemiyor",
            )
        }
    }

    @Test
    fun `dile ozgu test sablonlari gercekten farkli`() {
        val genel = PromptSelector.sablon(AksiyonTuru.TEST_YAZ, null)
        val kotlin = PromptSelector.sablon(AksiyonTuru.TEST_YAZ, "kt")
        val python = PromptSelector.sablon(AksiyonTuru.TEST_YAZ, "py")
        assertTrue(kotlin != genel, "kt şablonu genelden farklı değil")
        assertTrue(python != genel, "py şablonu genelden farklı değil")
        assertTrue(python.contains("pytest"), "py şablonu pytest'ten söz etmiyor")
    }
}
