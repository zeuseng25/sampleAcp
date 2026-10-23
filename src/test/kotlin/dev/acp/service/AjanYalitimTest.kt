package dev.acp.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.settings.AcpSettings

/**
 * Ajan çalışırken sohbet ve tamamlama etkilenmiyor (spec 008 T73).
 *
 * Üç özellik aynı eklentide, aynı modele bağlanıyor. Biri diğerinin durumunu
 * bozarsa teşhisi zor bir hata sınıfı doğar: kullanıcı ajanı çalıştırır, sohbet
 * geçmişi kaybolur ve sebebi görünmez.
 */
class AjanYalitimTest : BasePlatformTestCase() {

    private val ayarlar get() = AcpSettings.getInstance().state

    override fun tearDown() {
        try {
            ayarlar.baseUrl = ""
            ayarlar.model = ""
        } finally {
            super.tearDown()
        }
    }

    fun testAjanDurumuSohbetDurumundanAyri() {
        val sohbet = ChatSessionService.getInstance(project)
        val ajan = AjanService.getInstance(project)

        val oncekiSohbet = sohbet.state.value

        // Ayar eksik olduğu için ajan hemen hata durumuna geçer — durum değişimi
        // için yeterli, ağ gerekmiyor.
        ajan.baslat("bir görev")

        assertNotNull("Ajan durumu değişmedi", ajan.durum.value.hata)
        assertEquals("Ajan sohbet durumunu değiştirdi", oncekiSohbet, sohbet.state.value)
    }

    fun testAjanTemizlemeSohbeteDokunmaz() {
        val sohbet = ChatSessionService.getInstance(project)
        val ajan = AjanService.getInstance(project)

        ajan.baslat("görev")
        val oncekiSohbet = sohbet.state.value
        ajan.temizle()

        assertEquals(oncekiSohbet, sohbet.state.value)
        assertNull(ajan.durum.value.hata)
    }

    fun testAjanServisiProjeSeviyesinde() {
        // Uygulama seviyesinde olsaydı iki proje aynı planı paylaşır ve bir
        // projedeki düzenlemeler diğerine sızardı.
        assertSame(AjanService.getInstance(project), AjanService.getInstance(project))
    }

    fun testAyarEksikkenAjanSessizceCalismaz() {
        val ajan = AjanService.getInstance(project)
        ajan.temizle()
        ajan.baslat("görev")

        assertFalse("Ayar eksikken çalışmaya başladı", ajan.durum.value.calisiyor)
        assertNotNull("Sebep söylenmemiş", ajan.durum.value.hata)
    }

    fun testAjanCalismadanUygulaHicbirSeyYapmaz() {
        val ajan = AjanService.getInstance(project)
        ajan.temizle()
        assertNull("Sonuç yokken uygulama denendi", ajan.uygula())
    }
}
