package dev.acp.llm.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Araç tanımları tek yerde; sohbet ve ajan aynı listeyi kullanır (spec 010 T20). */
class AracSetleriTest {

    @Test
    fun `okuma seti duzenleme icermez`() {
        assertEquals(
            listOf("dosya_ara", "dosya_oku", "kullanimlari_bul", "uygulamalari_bul", "tanima_git"),
            AracSetleri.OKUMA.map { it.ad },
        )
        assertFalse(AracSetleri.OKUMA.any { it.ad == AracSetleri.DUZENLE })
    }

    @Test
    fun `duzenleme seti okuma artı duzenle`() {
        assertEquals(
            AracSetleri.OKUMA.map { it.ad } + AracSetleri.DUZENLE + AracSetleri.DOGRULA,
            AracSetleri.DUZENLEME.map { it.ad },
        )
        // Spec 009: komut yalnızca çok dosyalı kipte; Sohbet/Uzman'a verilmez.
        assertFalse(AracSetleri.OKUMA.any { it.ad == AracSetleri.DOGRULA })
    }

    @Test
    fun `iliski araclarinin zorunlu alanlari`() {
        fun zorunlu(ad: String) = AracSetleri.OKUMA.single { it.ad == ad }.parametreler
            .path("required").map { it.asText() }
        assertEquals(listOf("yol", "ad"), zorunlu("kullanimlari_bul"))
        assertEquals(listOf("yol", "ad"), zorunlu("uygulamalari_bul"))
        assertEquals(listOf("yol", "satir", "ad"), zorunlu("tanima_git"))
    }
}
