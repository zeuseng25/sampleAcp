package dev.acp.agent

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.komut.KomutSonucu
import dev.acp.llm.AracCagrisi
import dev.acp.llm.agent.DuzenlemePlani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.nio.file.Path

/**
 * `dogrula` aracı ve onay kapısı (spec 009 T40).
 *
 * En önemlisi: kullanıcı "Atla" derse **hiçbir şey çalışmaz** ve reddedilen bir
 * komut kullanıcıya hiç sorulmaz. İkisinin de ihlali sessizdir.
 */
class DogrulaAraciTest : BasePlatformTestCase() {

    private class SahteKapi(private val cevap: Boolean) : OnayKapisi {
        val sorulanlar = mutableListOf<List<String>>()
        val sonuclar = mutableListOf<KomutSonucu>()
        override suspend fun iste(komut: List<String>): Boolean { sorulanlar += komut; return cevap }
        override fun bitti(sonuc: KomutSonucu) { sonuclar += sonuc }
    }

    private class SahteCalistirici(private val sonuc: KomutSonucu) : Calistirici {
        var sayi = 0
        override suspend fun calistir(argumanlar: List<String>, kok: Path): KomutSonucu { sayi++; return sonuc }
    }

    private fun kos(y: IdeAracYurutucu): String {
        val is_ = CoroutineScope(Dispatchers.Default).async { y.calistir(AracCagrisi("id", "dogrula", "{}")) }
        PlatformTestUtil.waitWithEventsDispatching("araç bitmedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    private fun yurutucu(
        komut: List<String>?,
        kapi: OnayKapisi?,
        c: Calistirici?,
        duzenlemeAcik: Boolean = true,
    ) = IdeAracYurutucu(
        project, DuzenlemePlani(), duzenlemeAcik = duzenlemeAcik, iliski = null,
        komut = komut, onay = kapi, calistirici = c,
        // Hafif test projesi bellek içi dosya sisteminde; diskte yolu yok.
        komutKoku = { Path.of("/proje") },
    )

    fun testKomutTanimsizsaSorulmazNasilTanimlanacagiSoylenir() {
        val kapi = SahteKapi(true)
        val sonuc = kos(yurutucu(null, kapi, SahteCalistirici(KomutSonucu.Basarili(""))))
        assertTrue(sonuc, sonuc.startsWith("HATA: doğrulama komutu tanımlı değil"))
        assertTrue(kapi.sorulanlar.isEmpty())
    }

    fun testTanimsizKomutKullaniciyaDaBildirilir() {
        // Gözden geçirme bulgusu 4: yalnızca modele söyleniyordu.
        val kapi = SahteKapi(true)
        kos(yurutucu(null, kapi, SahteCalistirici(KomutSonucu.Basarili(""))))
        assertEquals(listOf<KomutSonucu>(KomutSonucu.Tanimsiz), kapi.sonuclar)
    }

    fun testMutlakYollarProjeKokuneGoreliOlur() {
        // Gözden geçirme bulgusu 5: Maven/Kotlin mutlak yol basıyor; dosya_oku göreli istiyor.
        val c = SahteCalistirici(KomutSonucu.Basarisiz(1,
            "[ERROR] /proje/src/A.java:[12,5] cannot find symbol\ne: file:///proje/src/B.kt:3:1 Unresolved reference",
        ))
        val sonuc = kos(yurutucu(listOf("mvn"), SahteKapi(true), c))
        assertTrue(sonuc, "src/A.java:[12,5]" in sonuc && "src/B.kt:3:1" in sonuc)
        assertFalse(sonuc, "/proje/" in sonuc)
    }

    fun testReddedilenKomutOnaydanOnceSoylenir() {
        val kapi = SahteKapi(true)
        val c = SahteCalistirici(KomutSonucu.Basarili(""))
        val sonuc = kos(yurutucu(listOf("mvn", "a&&rm"), kapi, c))
        assertTrue(sonuc, sonuc.startsWith("HATA: komut reddedildi"))
        assertTrue("reddedilen komut kullanıcıya soruldu", kapi.sorulanlar.isEmpty())
        assertEquals(0, c.sayi)
    }

    fun testAtlaDenirseHicbirSeyCalismaz() {
        val kapi = SahteKapi(false)
        val c = SahteCalistirici(KomutSonucu.Basarili(""))
        val sonuc = kos(yurutucu(listOf("mvn", "-q", "compile"), kapi, c))

        assertEquals(listOf(listOf("mvn", "-q", "compile")), kapi.sorulanlar)
        assertEquals("Atla'dan sonra komut çalıştı", 0, c.sayi)
        assertTrue(sonuc, "reddetti" in sonuc)
        assertEquals(listOf<KomutSonucu>(KomutSonucu.Atlandi), kapi.sonuclar)
    }

    fun testBasarisizSonucCiktiylaModeleGider() {
        val kapi = SahteKapi(true)
        val c = SahteCalistirici(KomutSonucu.Basarisiz(1, "[ERROR] Foo.java:[3,1] cannot find symbol"))
        val sonuc = kos(yurutucu(listOf("mvn", "-q", "compile"), kapi, c))
        assertTrue(sonuc, sonuc.startsWith("BAŞARISIZ (çıkış 1)"))
        assertTrue(sonuc, "cannot find symbol" in sonuc)
        assertEquals(1, c.sayi)
        assertEquals(KomutSonucu.Basarisiz(1, "[ERROR] Foo.java:[3,1] cannot find symbol"), kapi.sonuclar.single())
    }

    fun testSonucMetinleri() {
        fun metin(s: KomutSonucu) = kos(yurutucu(listOf("mvn"), SahteKapi(true), SahteCalistirici(s)))
        assertTrue(metin(KomutSonucu.Basarili("ok")).startsWith("BAŞARILI"))
        assertTrue(metin(KomutSonucu.Bulunamadi("mvn")).let { "bulunamadı" in it && "kod hatası değil" in it })
        assertTrue(metin(KomutSonucu.ZamanAsimi(300_000)).let { "zaman aşımı" in it && "300 sn" in it })
    }

    fun testSaltOkunurKipteKomutYok() {
        val kapi = SahteKapi(true)
        val sonuc = kos(yurutucu(listOf("mvn"), kapi, SahteCalistirici(KomutSonucu.Basarili("")), duzenlemeAcik = false))
        assertTrue(sonuc, sonuc.startsWith("HATA: bu kipte komut çalıştırılamaz"))
        assertTrue(kapi.sorulanlar.isEmpty())
    }
}
