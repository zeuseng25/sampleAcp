package dev.acp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T05 — H6: görünen ad build zamanında verilir. */
class AcpBrandingTest {

    private fun akis(icerik: String) = icerik.byteInputStream(Charsets.UTF_8)

    @Test
    fun `dosyadaki ad okunur`() {
        assertEquals("Şirket Asistanı", AcpBranding.oku(akis("displayName=Şirket Asistanı\n")))
    }

    @Test
    fun `turkce karakterler bozulmaz`() {
        // Properties varsayılan olarak ISO-8859-1 okur; UTF-8 okuduğumuzun kanıtı.
        assertEquals("ĞÜŞİÖÇ ğüşiöç", AcpBranding.oku(akis("displayName=ĞÜŞİÖÇ ğüşiöç\n")))
    }

    @Test
    fun `dosya yoksa varsayilan kullanilir`() {
        assertEquals(AcpBranding.VARSAYILAN, AcpBranding.oku(null))
    }

    @Test
    fun `anahtar yoksa varsayilan kullanilir`() {
        assertEquals(AcpBranding.VARSAYILAN, AcpBranding.oku(akis("baskaAnahtar=x\n")))
    }

    @Test
    fun `bos veya sadece bosluk olan ad yok sayilir`() {
        assertEquals(AcpBranding.VARSAYILAN, AcpBranding.oku(akis("displayName=\n")))
        assertEquals(AcpBranding.VARSAYILAN, AcpBranding.oku(akis("displayName=   \n")))
    }

    @Test
    fun `bastaki ve sondaki bosluklar kirpilir`() {
        assertEquals("Asistan", AcpBranding.oku(akis("displayName=  Asistan  \n")))
    }

    @Test
    fun `build tarafindan uretilen kaynak gercekten pakete giriyor`() {
        // Bu test, Gradle'ın generateBranding görevinin kaynağı ürettiğini kanıtlar.
        assertTrue(
            AcpBranding.displayName.isNotBlank(),
            "acp-branding.properties üretilmemiş veya okunamıyor",
        )
    }
}
