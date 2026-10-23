package dev.acp.context

import dev.acp.llm.prompt.ContextItem
import dev.acp.llm.prompt.ContextKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 002/T70 — bütçe kuralları. Saf: IDE gerekmez.
 *
 * En önemli kural: **seçim asla kırpılmaz** (spec 002 H3).
 */
class BudgetTest {

    private fun secim(uzunluk: Int) = ContextItem(
        kind = ContextKind.SELECTION,
        displayName = "Foo.kt (1-5)",
        text = "s".repeat(uzunluk),
    )

    private fun dosya(uzunluk: Int) = ContextItem(
        kind = ContextKind.CURRENT_FILE,
        displayName = "Foo.kt",
        text = (1..uzunluk / 10).joinToString("\n") { "satir$it".padEnd(9) },
    )

    @Test
    fun `hepsi sigiyorsa hicbiri kirpilmaz`() {
        val ogeler = listOf(secim(100), dosya(200))
        val sonuc = CompositeContextProvider.butceyeSigdir(ogeler, 10_000)
        assertEquals(2, sonuc.size)
        assertTrue(sonuc.none { it.truncated })
    }

    @Test
    fun `secim butceyi assa bile tam gonderilir`() {
        val buyukSecim = secim(5_000)
        val sonuc = CompositeContextProvider.butceyeSigdir(listOf(buyukSecim, dosya(1_000)), 1_000)

        val secilenler = sonuc.filter { it.kind == ContextKind.SELECTION }
        assertEquals(1, secilenler.size)
        assertEquals(5_000, secilenler[0].text.length)
        assertFalse(secilenler[0].truncated)
        // Bütçe seçimle doldu; dosya düştü.
        assertTrue(sonuc.none { it.kind == ContextKind.CURRENT_FILE })
    }

    @Test
    fun `sigmayan dosya kirpilir ve isaretlenir`() {
        val sonuc = CompositeContextProvider.butceyeSigdir(listOf(secim(100), dosya(5_000)), 1_000)
        val dosyaOgesi = sonuc.single { it.kind == ContextKind.CURRENT_FILE }
        assertTrue(dosyaOgesi.truncated)
        assertTrue(dosyaOgesi.text.length < 5_000)
    }

    @Test
    fun `secim yoksa dosya butun butceyi kullanir`() {
        val sonuc = CompositeContextProvider.butceyeSigdir(listOf(dosya(300)), 10_000)
        assertEquals(1, sonuc.size)
        assertFalse(sonuc[0].truncated)
    }

    @Test
    fun `bos liste bos doner`() {
        assertEquals(emptyList(), CompositeContextProvider.butceyeSigdir(emptyList(), 1_000))
    }
}
