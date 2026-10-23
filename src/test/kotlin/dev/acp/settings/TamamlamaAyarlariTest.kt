package dev.acp.settings

import dev.acp.llm.completion.FimBicimi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Satır içi tamamlama ayarları (spec 006 T40, T42).
 *
 * En önemlisi: **varsayılan kapalı** ve kapalıyken hiçbir istek gönderilmiyor.
 * Sohbette kullanıcı her seferinde bilinçli bir eylem yapar; burada kod yazdıkça
 * kendiliğinden dışarı gider. Bu yüzden açık/kapalı kararı bir varsayılan
 * değerden ibaret değil, güvenlik sınırı.
 */
class TamamlamaAyarlariTest {

    private fun ayar() = AcpSettings.State()

    // ---- T40: varsayılanlar ----------------------------------------------

    @Test
    fun `varsayilan bicim kapali`() {
        assertEquals(FimBicimi.KAPALI, ayar().fimBicimi)
    }

    @Test
    fun `varsayilan ayarla tamamlama kapali`() {
        assertFalse(ayar().tamamlamaAcikMi(), "Kutudan çıktığı gibi kod dışarı gitmemeli")
    }

    @Test
    fun `sunucu ve model dolu olsa bile bicim kapaliysa istek gitmez`() {
        // Kullanıcı sohbeti yapılandırmış olabilir; bu tamamlamayı açmaz.
        val a = ayar().apply {
            baseUrl = "https://sunucu"
            model = "sohbet-modeli"
        }
        assertFalse(a.tamamlamaAcikMi())
    }

    @Test
    fun `bicim secilince ve sunucu varsa acilir`() {
        val a = ayar().apply {
            baseUrl = "https://sunucu"
            model = "sohbet-modeli"
            completionFormat = FimBicimi.QWEN.name
        }
        assertTrue(a.tamamlamaAcikMi())
    }

    @Test
    fun `sunucu adresi yoksa acilmaz`() {
        val a = ayar().apply {
            model = "m"
            completionFormat = FimBicimi.QWEN.name
        }
        assertFalse(a.tamamlamaAcikMi())
    }

    @Test
    fun `hicbir model yoksa acilmaz`() {
        val a = ayar().apply {
            baseUrl = "https://sunucu"
            completionFormat = FimBicimi.QWEN.name
        }
        assertFalse(a.tamamlamaAcikMi(), "Model olmadan istek kurulamaz")
    }

    @Test
    fun `taninmayan bicim adi KAPALIya duser`() {
        // Elle düzenlenmiş veya eski sürümden kalmış acp.xml. Çökmek yerine
        // en güvenli tarafa düşülür: kod dışarı gitmez.
        val a = ayar().apply {
            baseUrl = "https://sunucu"
            model = "m"
            completionFormat = "BOYLE_BIR_BICIM_YOK"
        }
        assertEquals(FimBicimi.KAPALI, a.fimBicimi)
        assertFalse(a.tamamlamaAcikMi())
    }

    @Test
    fun `bos bicim adi da KAPALIya duser`() {
        assertEquals(FimBicimi.KAPALI, ayar().apply { completionFormat = "" }.fimBicimi)
    }

    @Test
    fun `sayisal varsayilanlar makul`() {
        val a = ayar()
        assertEquals(64, a.completionMaxTokens)
        assertEquals(300, a.completionDebounceMs)
        // Sohbetin okuma zaman aşımından (180 sn) çok kısa olmalı: geç gelen
        // öneri zaten atılacak, beklemenin kullanıcıya faydası yok.
        assertEquals(3_000, a.completionTimeoutMs)
        assertTrue(a.completionTimeoutMs < a.readTimeoutMs)
    }

    // ---- T42: model seçimi ------------------------------------------------

    @Test
    fun `tamamlama modeli bossa sohbet modeline duser`() {
        val a = ayar().apply { model = "sohbet-modeli" }
        assertEquals("sohbet-modeli", a.tamamlamaModeli())
    }

    @Test
    fun `tamamlama modeli doluysa o kullanilir`() {
        val a = ayar().apply {
            model = "sohbet-modeli"
            completionModel = "kucuk-coder"
        }
        assertEquals("kucuk-coder", a.tamamlamaModeli())
    }

    @Test
    fun `sadece bosluktan ibaret tamamlama modeli bos sayilir`() {
        val a = ayar().apply {
            model = "sohbet-modeli"
            completionModel = "   "
        }
        assertEquals("sohbet-modeli", a.tamamlamaModeli())
    }

    @Test
    fun `iki model de bossa tamamlama acilmaz`() {
        val a = ayar().apply {
            baseUrl = "https://sunucu"
            completionFormat = FimBicimi.QWEN.name
            completionModel = ""
            model = ""
        }
        assertEquals("", a.tamamlamaModeli())
        assertFalse(a.tamamlamaAcikMi())
    }
}
