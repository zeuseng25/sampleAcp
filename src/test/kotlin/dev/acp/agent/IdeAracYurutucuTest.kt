package dev.acp.agent

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import dev.acp.llm.AracCagrisi
import dev.acp.llm.agent.DuzenlemePlani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Ajan araçlarının IDE tarafı (spec 008 T40-T44).
 *
 * En kritik test `testDuzenlemeDiskeYAZMAZ`: spec'in "ya tamamen ya hiç" kuralı
 * buna dayanıyor ve ihlali **sessizce** olur — kod çalışır, testler geçer, ama
 * döngü ortasında iptal edilen bir iş dosyaları yarım bırakır.
 */
class IdeAracYurutucuTest : BasePlatformTestCase() {

    private lateinit var plan: DuzenlemePlani
    private lateinit var yurutucu: IdeAracYurutucu

    override fun setUp() {
        super.setUp()
        plan = DuzenlemePlani()
        yurutucu = IdeAracYurutucu(project, plan)
    }

    /** `runBlocking` KULLANILMAZ — okuma eylemi EDT isteyebilir, test kilitlenir. */
    private fun calistir(ad: String, args: String): String {
        val is_ = CoroutineScope(Dispatchers.Default).async {
            yurutucu.calistir(AracCagrisi("id", ad, args))
        }
        PlatformTestUtil.waitWithEventsDispatching("Araç yanıt vermedi", { is_.isCompleted }, 20)
        return is_.getCompleted()
    }

    private fun dosyaOlustur(ad: String, icerik: String) =
        myFixture.addFileToProject(ad, icerik)

    // ---- T40/T41: arama ----

    fun testAramaDosyaYoluVeSatirDondurur() {
        dosyaOlustur("Ornek.txt", "birinci satir\nhedef burada\nucuncu")

        val sonuc = calistir("dosya_ara", """{"desen":"hedef"}""")

        assertTrue("Yol yok: $sonuc", sonuc.contains("Ornek.txt"))
        assertTrue("Satır numarası yok: $sonuc", sonuc.contains(":2:"))
        assertTrue("Satır içeriği yok: $sonuc", sonuc.contains("hedef burada"))
    }

    fun testAramaSonucYoksaBunuSoyler() {
        assertEquals("sonuç yok", calistir("dosya_ara", """{"desen":"kesinlikle_yok_12345"}"""))
    }

    fun testAramaSonuclariKirpilir() {
        // Bağlamı doldurmamalı; fazlası sayıyla bildirilmeli.
        val cokSatir = (1..100).joinToString("\n") { "tekrar$it eslesme" }
        dosyaOlustur("Cok.txt", cokSatir)

        val sonuc = calistir("dosya_ara", """{"desen":"eslesme"}""")
        assertTrue("Kırpılmamış: ${sonuc.lines().size} satır", sonuc.lines().size <= 41)
        assertTrue("Fazlası bildirilmemiş: $sonuc", sonuc.contains("sonuç daha"))
    }

    fun testBosDesenReddedilir() {
        assertTrue(calistir("dosya_ara", """{"desen":""}""").startsWith("HATA"))
    }

    // ---- T42: okuma ----

    fun testDosyaOkunur() {
        dosyaOlustur("Oku.txt", "icerik burada")
        assertTrue(calistir("dosya_oku", """{"yol":"Oku.txt"}""").contains("icerik burada"))
    }

    fun testOlmayanDosyaHataVerir() {
        assertTrue(calistir("dosya_oku", """{"yol":"yok.txt"}""").startsWith("HATA"))
    }

    // ---- T43: EN KRİTİK — diske yazmaz ----

    fun testDuzenlemeDiskeYAZMAZ() {
        val dosya = dosyaOlustur("Hedef.txt", "eski deger burada")

        val sonuc = calistir(
            "dosya_duzenle",
            """{"yol":"Hedef.txt","eski":"eski deger","yeni":"yeni deger"}""",
        )

        assertEquals("kaydedildi", sonuc)
        // Plana girdi...
        assertEquals(1, plan.boyut)
        // ...ama dosya DEĞİŞMEDİ.
        assertEquals(
            "Araç diske yazdı — 'ya tamamen ya hiç' kuralı ihlal edildi",
            "eski deger burada",
            String(dosya.virtualFile.contentsToByteArray()),
        )
    }

    fun testBirdenFazlaDuzenlemeToplanir() {
        dosyaOlustur("A.txt", "bir\niki")
        dosyaOlustur("B.txt", "uc")

        calistir("dosya_duzenle", """{"yol":"A.txt","eski":"bir","yeni":"BIR"}""")
        calistir("dosya_duzenle", """{"yol":"A.txt","eski":"iki","yeni":"IKI"}""")
        calistir("dosya_duzenle", """{"yol":"B.txt","eski":"uc","yeni":"UC"}""")

        assertEquals(3, plan.boyut)
        assertEquals(2, plan.dosyaSayisi)
        assertEquals(2, plan.dosyaBazinda()["A.txt"]!!.size)
    }

    fun testEskiMetinBulunamazsaHataVerir() {
        dosyaOlustur("C.txt", "gercek icerik")
        val sonuc = calistir("dosya_duzenle", """{"yol":"C.txt","eski":"olmayan","yeni":"x"}""")

        assertTrue("Hata beklendi: $sonuc", sonuc.startsWith("HATA"))
        assertEquals("Plana hatalı düzenleme girdi", 0, plan.boyut)
    }

    fun testBirdenFazlaEslesmeReddedilir() {
        // Belirsiz düzenleme sessizce yanlış yere uygulanmamalı.
        dosyaOlustur("D.txt", "tekrar\ntekrar")
        val sonuc = calistir("dosya_duzenle", """{"yol":"D.txt","eski":"tekrar","yeni":"x"}""")

        assertTrue("Belirsizlik yakalanmadı: $sonuc", sonuc.startsWith("HATA"))
        assertEquals(0, plan.boyut)
    }

    fun testAyniDuzenlemeIkiKezEklenmez() {
        dosyaOlustur("E.txt", "deger")
        calistir("dosya_duzenle", """{"yol":"E.txt","eski":"deger","yeni":"yeni"}""")
        val ikinci = calistir("dosya_duzenle", """{"yol":"E.txt","eski":"deger","yeni":"yeni"}""")

        assertEquals("zaten kayıtlı", ikinci)
        assertEquals(1, plan.boyut)
    }

    fun testDegisiklikIcermeyenDuzenlemeReddedilir() {
        dosyaOlustur("F.txt", "ayni")
        assertTrue(
            calistir("dosya_duzenle", """{"yol":"F.txt","eski":"ayni","yeni":"ayni"}""")
                .startsWith("HATA"),
        )
    }

    // ---- T44: proje dışı yol ----

    fun testProjeDisiYolReddedilir() {
        val sonuc = calistir("dosya_duzenle", """{"yol":"../../../etc/passwd","eski":"a","yeni":"b"}""")
        assertTrue("Proje dışı yol kabul edildi: $sonuc", sonuc.startsWith("HATA"))
        assertEquals(0, plan.boyut)
    }

    fun testProjeDisiOkumaReddedilir() {
        assertTrue(calistir("dosya_oku", """{"yol":"../../../etc/hosts"}""").startsWith("HATA"))
    }

    // ---- hatalı girdi döngüyü çökertmez ----

    fun testBozukJsonHataDondurur() {
        val sonuc = calistir("dosya_ara", "{bozuk")
        assertTrue("Bozuk JSON çökme yapmalı değil: $sonuc", sonuc.startsWith("HATA"))
    }

    fun testBilinmeyenAracHataDondurur() {
        assertTrue(calistir("boyle_bir_arac_yok", "{}").contains("bilinmeyen araç"))
    }
}
