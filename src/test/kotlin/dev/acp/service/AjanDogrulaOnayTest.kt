package dev.acp.service

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.fake.FakeSglangServer
import dev.acp.settings.AcpProjeAyarlari
import dev.acp.settings.AcpSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * Ajan doğrulama komutu için onay ister (spec 009 T50).
 *
 * Gerçek istemci + sahte sunucu (model `dogrula` çağırır) + gerçek komut
 * çalıştırıcı. Kök, hafif test projesi bellek içi olduğu için geçici bir dizin.
 */
class AjanDogrulaOnayTest : BasePlatformTestCase() {

    private lateinit var sunucu: FakeSglangServer
    private lateinit var kok: Path
    private val ajan get() = AjanService.getInstance(project)
    private val ayarlar get() = AcpSettings.getInstance().state
    private var eski = AcpSettings.State()

    override fun setUp() {
        super.setUp()
        eski = AcpSettings.State().also { it.copyFrom(ayarlar) }
        kok = Files.createTempDirectory("acp-onay")
        sunucu = FakeSglangServer(tokens = listOf("Düzelttim."), mode = FakeSglangServer.Mode.ARAC_CAGIR, aracAdi = "dogrula").start()
        ayarlar.baseUrl = sunucu.baseUrl
        ayarlar.model = "test-model"
        ajan.komutKoku = { kok }
    }

    override fun tearDown() {
        try {
            ajan.durdur()
            ajan.temizle()
            ajan.komutKoku = null
            AcpProjeAyarlari.getInstance(project).state.dogrulamaKomutu = ""
            sunucu.close()
            kok.toFile().deleteRecursively()
            ayarlar.copyFrom(eski)
        } finally {
            super.tearDown()
        }
    }

    private fun komut(k: String) { AcpProjeAyarlari.getInstance(project).state.dogrulamaKomutu = k }

    private fun bekleyenKomut(): String {
        PlatformTestUtil.waitWithEventsDispatching("onay istenmedi", { ajan.durum.value.bekleyenKomut != null }, 20)
        return ajan.durum.value.bekleyenKomut!!
    }

    private fun bitmesini() =
        PlatformTestUtil.waitWithEventsDispatching("ajan bitmedi", { !ajan.durum.value.calisiyor }, 20)

    fun testOnaylaninceCalisirSonucAdimlardaVeModelde() {
        if (SystemInfo.isWindows) return
        komut("touch calisti")
        ajan.baslat("derlenmiyor, düzelt")

        assertEquals("touch calisti", bekleyenKomut())
        assertFalse("onaydan önce çalıştı", Files.exists(kok.resolve("calisti")))
        ajan.komutuCalistir()
        bitmesini()

        assertTrue(Files.exists(kok.resolve("calisti")))
        assertNull(ajan.durum.value.bekleyenKomut)
        assertTrue(ajan.durum.value.adimlar.toString(), ajan.durum.value.adimlar.any { it == "dogrula: başarılı" })
        assertTrue("sonuç modele gitmedi", "BAŞARILI" in sunucu.chatRequestBodies[1])
    }

    fun testCalisirkenDurdurulursaAdimDurdurulduOlur() {
        if (SystemInfo.isWindows) return
        // Gözden geçirme bulgusu 2: satır "çalışıyor"da kalıyordu.
        komut("sleep 30")
        ajan.baslat("derlenmiyor")
        bekleyenKomut()
        ajan.komutuCalistir()
        PlatformTestUtil.waitWithEventsDispatching("komut başlamadı", { ajan.durum.value.adimlar.any { "çalışıyor" in it } }, 20)
        ajan.durdur()
        PlatformTestUtil.waitWithEventsDispatching("durmadı", { ajan.durum.value.mesaj == "Durduruldu." }, 20)
        val a = ajan.durum.value.adimlar
        assertTrue(a.toString(), a.any { it == "dogrula: durduruldu" } && a.none { "çalışıyor" in it })
    }

    fun testBasarisizCiktiKullaniciyaGosterilir() {
        if (SystemInfo.isWindows) return
        // Gözden geçirme bulgusu 3: çıktı yalnızca modele gidiyordu (spec Hata Durumları).
        komut("ls olmayan-dosya-xyz")
        ajan.baslat("derlenmiyor")
        bekleyenKomut()
        ajan.komutuCalistir()
        bitmesini()
        assertTrue(ajan.durum.value.komutBilgisi.orEmpty(), "olmayan-dosya-xyz" in ajan.durum.value.komutBilgisi.orEmpty())
    }

    fun testTanimsizKomutAyarYoluyla() {
        komut("")
        ajan.komutKoku = { kok.resolve("bos-alt") } // tahmin yok: kökte proje dosyası yok
        ajan.baslat("derlenmiyor")
        bitmesini()
        assertTrue(ajan.durum.value.komutBilgisi.orEmpty(), "Proje" in ajan.durum.value.komutBilgisi.orEmpty())
    }

    fun testAtlaninceCalismaz() {
        if (SystemInfo.isWindows) return
        komut("touch calisti")
        ajan.baslat("derlenmiyor")
        bekleyenKomut()
        ajan.komutuAtla()
        bitmesini()

        assertFalse(Files.exists(kok.resolve("calisti")))
        assertTrue(ajan.durum.value.adimlar.any { it == "dogrula: atlandı" })
        assertTrue("reddetti" in sunucu.chatRequestBodies[1])
    }

    fun testOnayBeklerkenDurdurulursaCalismaz() {
        if (SystemInfo.isWindows) return
        komut("touch calisti")
        ajan.baslat("derlenmiyor")
        bekleyenKomut()
        ajan.durdur()
        PlatformTestUtil.waitWithEventsDispatching("durmadı", { ajan.durum.value.mesaj == "Durduruldu." }, 20)

        assertNull("bekleyen onay kaldı", ajan.durum.value.bekleyenKomut)
        ajan.komutuCalistir() // geç gelen tık hiçbir şey yapmamalı
        Thread.sleep(300)
        assertFalse("durdurulduktan sonra çalıştı", Files.exists(kok.resolve("calisti")))
    }
}
