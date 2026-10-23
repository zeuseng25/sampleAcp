package dev.acp.agent

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.anlama.IliskiArayici
import dev.acp.llm.AracCagrisi
import dev.acp.llm.agent.DuzenlemePlani
import dev.acp.llm.agent.IliskiSonucu
import dev.acp.llm.agent.SembolIstegi
import dev.acp.llm.agent.Yer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Sohbet ve Uzman kipinin salt okunurluğu ve ilişki araçlarının yönlendirmesi
 * (spec 010 T21-T22).
 *
 * Salt okunurluk bir **yetenek sınırı**dır, yönerge değil: araç tanımı verilmese
 * bile model adı uydurup çağırabilir; yürütücü o kipte bu adı reddetmeli.
 */
class SaltOkunurYurutucuTest : BasePlatformTestCase() {

    private class SahteIliski(
        private val hazir: Boolean = true,
        private val sonuc: IliskiSonucu = IliskiSonucu.Bulundu(emptyList(), 0),
    ) : IliskiArayici {
        val istekler = mutableListOf<SembolIstegi>()
        override fun hazirMi() = hazir
        override suspend fun kullanimlar(s: SembolIstegi, max: Int): IliskiSonucu { istekler += s; return sonuc }
        override suspend fun uygulamalar(s: SembolIstegi, max: Int): IliskiSonucu { istekler += s; return sonuc }
        override suspend fun tanim(yol: String, satir: Int, ad: String): IliskiSonucu {
            istekler += SembolIstegi(yol, ad, satir); return sonuc
        }
    }

    private fun calistir(y: IdeAracYurutucu, ad: String, args: String): String {
        val is_ = CoroutineScope(Dispatchers.Default).async { y.calistir(AracCagrisi("id", ad, args)) }
        PlatformTestUtil.waitWithEventsDispatching("Araç yanıt vermedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    fun testSaltOkunurKipteDuzenlemeReddedilir() {
        myFixture.addFileToProject("A.txt", "eski deger")
        val plan = DuzenlemePlani()
        val y = IdeAracYurutucu(project, plan, duzenlemeAcik = false, iliski = null)

        val sonuc = calistir(y, "dosya_duzenle", """{"yol":"A.txt","eski":"eski","yeni":"yeni"}""")

        assertTrue(sonuc, sonuc.startsWith("HATA: bu kipte dosya değiştirilemez"))
        assertEquals("Plana düzenleme girdi", 0, plan.boyut)
    }

    fun testDuzenlemeKipindeDuzenlemeCalisir() {
        myFixture.addFileToProject("A.txt", "eski deger")
        val plan = DuzenlemePlani()
        val y = IdeAracYurutucu(project, plan, iliski = null)
        assertEquals("kaydedildi", calistir(y, "dosya_duzenle", """{"yol":"A.txt","eski":"eski","yeni":"yeni"}"""))
    }

    fun testIliskiYoksaMetinAramasinaYonlendirir() {
        val y = IdeAracYurutucu(project, DuzenlemePlani(), iliski = null)
        val sonuc = calistir(y, "kullanimlari_bul", """{"yol":"A.java","ad":"calis"}""")
        assertTrue(sonuc, sonuc.startsWith("HATA:") && "dosya_ara" in sonuc)
    }

    fun testDizinlemeSurerkenMetinAramasinaYonlendirir() {
        val iliski = SahteIliski(hazir = false)
        val y = IdeAracYurutucu(project, DuzenlemePlani(), iliski = iliski)
        val sonuc = calistir(y, "uygulamalari_bul", """{"yol":"A.java","ad":"calis"}""")
        assertTrue(sonuc, "dizinleme" in sonuc && "dosya_ara" in sonuc)
        assertTrue("Hazır değilken arama yapıldı", iliski.istekler.isEmpty())
    }

    fun testIliskiAraclariArgumanlariIletirVeBicimler() {
        val iliski = SahteIliski(sonuc = IliskiSonucu.Bulundu(listOf(Yer("B.java:3", "B.yaz", "a.calis();")), 1))
        val y = IdeAracYurutucu(project, DuzenlemePlani(), iliski = iliski)

        assertEquals("B.java:3 [B.yaz] a.calis();", calistir(y, "kullanimlari_bul", """{"yol":"A.java","ad":"calis","satir":7}"""))
        calistir(y, "tanima_git", """{"yol":"B.java","satir":3,"ad":"calis"}""")

        assertEquals(
            listOf(SembolIstegi("A.java", "calis", 7), SembolIstegi("B.java", "calis", 3)),
            iliski.istekler,
        )
    }

    fun testEksikArgumanModeleSoylenir() {
        val y = IdeAracYurutucu(project, DuzenlemePlani(), iliski = SahteIliski())
        assertTrue(calistir(y, "kullanimlari_bul", """{"ad":"calis"}""").startsWith("HATA: 'yol' ve 'ad'"))
        assertTrue(calistir(y, "tanima_git", """{"yol":"A","ad":"x"}""").startsWith("HATA: 'satir'"))
    }
}
