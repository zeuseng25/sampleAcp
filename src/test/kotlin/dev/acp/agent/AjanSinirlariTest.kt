package dev.acp.agent

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.llm.AracCagrisi
import dev.acp.llm.agent.DuzenlemePlani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Ajan sınırları (spec 008 T71).
 *
 * Sınır aşıldığında **sebebi söyleniyor**: sessizce durmak, kullanıcının işi
 * tamamlanmış sanmasına yol açar — çok dosyalı bir değişiklikte pahalı yanılgı.
 */
class AjanSinirlariTest : BasePlatformTestCase() {

    private fun calistir(y: IdeAracYurutucu, ad: String, args: String): String {
        val is_ = CoroutineScope(Dispatchers.Default).async {
            y.calistir(AracCagrisi("id", ad, args))
        }
        PlatformTestUtil.waitWithEventsDispatching("Araç yanıt vermedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    fun testDosyaSinirinaUlasincaSebebiSoylenir() {
        val plan = DuzenlemePlani()
        val y = IdeAracYurutucu(project, plan, maxDosya = 2)

        repeat(3) { i ->
            myFixture.addFileToProject("S$i.txt", "deger$i")
        }

        assertEquals("kaydedildi", calistir(y, "dosya_duzenle", """{"yol":"S0.txt","eski":"deger0","yeni":"x"}"""))
        assertEquals("kaydedildi", calistir(y, "dosya_duzenle", """{"yol":"S1.txt","eski":"deger1","yeni":"x"}"""))

        val ucuncu = calistir(y, "dosya_duzenle", """{"yol":"S2.txt","eski":"deger2","yeni":"x"}""")
        assertTrue("Sınır sessizce uygulandı: $ucuncu", ucuncu.startsWith("HATA"))
        assertTrue("Sebep söylenmemiş: $ucuncu", ucuncu.contains("sınır"))
        assertEquals("Sınıra rağmen eklendi", 2, plan.dosyaSayisi)
    }

    fun testSinirdakiDosyaYineDuzenlenebilir() {
        // Sınır DOSYA sayısında; zaten plana girmiş bir dosyaya devam edilebilmeli.
        val plan = DuzenlemePlani()
        val y = IdeAracYurutucu(project, plan, maxDosya = 1)
        myFixture.addFileToProject("T.txt", "bir iki")

        assertEquals("kaydedildi", calistir(y, "dosya_duzenle", """{"yol":"T.txt","eski":"bir","yeni":"BIR"}"""))
        assertEquals("kaydedildi", calistir(y, "dosya_duzenle", """{"yol":"T.txt","eski":"iki","yeni":"IKI"}"""))
        assertEquals(2, plan.boyut)
        assertEquals(1, plan.dosyaSayisi)
    }
}
