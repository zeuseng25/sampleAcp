package dev.acp.llm.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Temizleme boru hattı, **gerçek model çıktıları** üzerinde.
 *
 * Uydurma girdiyle yazılan testler, modelin gerçekte ne ürettiğini kaçırıyor.
 * Buradaki dizgeler `qwen/qwen3.8-27b`'den 2026-09-04'te birebir alındı;
 * kullanıcının bildirdiği `s.length` hatası bu testlerle teşhis edildi.
 */
class GercekModelCiktisiTest {

    private fun temizle(ham: String, taban: Int = 4, sonrasiSatir: String = "", max: Int = 6) =
        OneriTemizleyici.temizle(
            ham, FimBicimi.QWEN, sonrasiSatir,
            kip = TamamlamaKipi.COK_SATIR, tabanGirinti = taban, maxSatir = max,
        )

    /** `public static int uzunluk(String s) {\n    ‸\n}` */
    private val uzunluk = "\n    return s.length();\n"

    /** `public static String tersCevir(String metin) {\n    ‸\n}` — model girintisiz başlamış. */
    private val tersCevir =
        "\nString ters = \"\";\nfor (int i = metin.length() - 1; i >= 0; i--) {\n" +
            "    ters += metin.charAt(i);\n}\nreturn ters;\n"

    /** `public static int enBuyuguBul(int[] sayilar) {\n    ‸\n}` */
    private val enBuyugu =
        "\n    int enBuyuk = sayilar[0];\n    for (int i = 1; i < sayilar.length; i++) {\n" +
            "        if (sayilar[i] > enBuyuk) {\n            enBuyuk = sayilar[i];\n        }\n" +
            "    }\n    return enBuyuk;\n"

    @Test
    fun `parantezler korunur - kullanicinin bildirdigi hata`() {
        // Model `s.length()` üretiyor; temizleyici parantezleri YEMEMELİ.
        assertEquals("return s.length();", temizle(uzunluk))
    }

    @Test
    fun `dizi length parantezsiz kalir`() {
        val sonuc = temizle(enBuyugu, max = 10)!!
        assertTrue(sonuc.contains("sayilar.length;"), "Diziye parantez eklenmiş: $sonuc")
        assertTrue(!sonuc.contains("sayilar.length();"), "Diziye parantez eklenmiş: $sonuc")
    }

    @Test
    fun `ic bloklar korunur`() {
        val sonuc = temizle(enBuyugu, max = 10)!!
        assertTrue(sonuc.contains("if (sayilar[i] > enBuyuk) {"), sonuc)
        assertTrue(sonuc.contains("return enBuyuk;"), "Son satır kesilmiş: $sonuc")
    }

    @Test
    fun `girintisiz baslayan cikti butun kalir`() {
        // Model bazen ilk satırı girintili, bazen girintisiz yazıyor (aynı
        // sunucu, aynı ayar). Girintisiz olduğunda eski kesici ikinci satırda
        // durup geriye tek satır bırakıyordu; bu testin ilk hâli o hatayı
        // yazdırıyordu.
        val sonuc = temizle(tersCevir, max = 10)!!
        assertTrue(sonuc.contains("for (int i = metin.length() - 1"), "Döngü kesilmiş: $sonuc")
        assertTrue(sonuc.contains("ters += metin.charAt(i);"), "Gövde kesilmiş: $sonuc")
        assertTrue(sonuc.contains("return ters;"), "Son satır kesilmiş: $sonuc")
        assertEquals(5, sonuc.lines().size, "Beklenen 5 satır: $sonuc")
    }

    /**
     * Kullanıcının 2026-09-04'te "bir şey üretmedi" dediği fonksiyon.
     *
     * Model aslında dolu ve doğru bir gövde üretiyordu (80 token, 8 satır).
     * Sorun bizim `maxSatir` varsayılanımızdı: 6, gövdeyi `return`'den önce
     * kesiyordu. Ham çıktı birebir burada.
     */
    private val enBuyukSayiyiBul =
        "   int enBuyukSayi = sayilar[0];\n        for (int i = 1; i < sayilar.length; i++) {\n" +
            "            if (sayilar[i] > enBuyukSayi) {\n                enBuyukSayi = sayilar[i];\n" +
            "            }\n        }\n        return enBuyukSayi;\n    }\n"

    @Test
    fun `kullanicinin vakasi - govde butun geliyor`() {
        val sonuc = temizle(enBuyukSayiyiBul, max = 12)!!
        assertTrue(sonuc.contains("return enBuyukSayi;"), "return kesilmiş: $sonuc")
        // Fonksiyonun kapanış parantezi ALINMAMALI — derinlik sayacı ayırıyor.
        assertTrue(!sonuc.trimEnd().endsWith("}\n    }"), "fonksiyon kapanışı alınmış: $sonuc")
        assertEquals(7, sonuc.lines().size, "Beklenen 7 satır: $sonuc")
    }

    @Test
    fun `eski varsayilan alti satir govdeyi kesiyordu`() {
        // Hatanın kendisi: sınır 6 iken `return` düşüyor.
        val eski = temizle(enBuyukSayiyiBul, max = 6)!!
        assertTrue(!eski.contains("return enBuyukSayi;"), "6 satırla return gelmemeliydi")
    }

    @Test
    fun `sonek kirpma parantez yemiyor`() {
        // İmleçten sonra `}` varken de parantezler korunmalı.
        assertEquals("return s.length();", temizle(uzunluk, sonrasiSatir = "}"))
    }
}
