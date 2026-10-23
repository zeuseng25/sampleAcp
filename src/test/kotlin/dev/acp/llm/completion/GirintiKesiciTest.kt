package dev.acp.llm.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Öneri nerede biter (spec 007 T30-T34).
 *
 * Girinti, dile özgü kod çözümleme kullanamadığımız için (Altın Kural 3)
 * elimizdeki tek yapısal sinyal.
 */
class GirintiKesiciTest {

    private fun kes(oneri: String, taban: Int = 4, max: Int = 6) =
        GirintiKesici.kes(oneri, taban, max)

    // ---- T30: ilk satır karşılaştırmaya girmez ----

    @Test
    fun `ilk satir girinti karsilastirmasina girmez`() {
        // Model, imlecin satırının DEVAMINI yazar; o satırın kendi girintisi yok.
        // Karşılaştırmaya alınsaydı 0 < 4 çıkar ve öneri daha başlamadan kesilirdi.
        val oneri = "int a = 1;\n    int b = 2;"
        assertEquals("int a = 1;\n    int b = 2;", kes(oneri))
    }

    @Test
    fun `ilk satir tek basinayken de korunur`() {
        assertEquals("return a + b;", kes("return a + b;"))
    }

    // ---- T31: tabandan az girintili satırda dur ----

    @Test
    fun `kapanis parantezinde durur ve onu atar`() {
        val oneri = "int a = 1;\n    int b = 2;\n}\nvoid digeri() {"
        assertEquals("int a = 1;\n    int b = 2;", kes(oneri))
    }

    @Test
    fun `ic blok korunur - daha derin girinti kesmez`() {
        val oneri = "if (x) {\n        derin();\n    }\n    son();"
        assertEquals("if (x) {\n        derin();\n    }\n    son();", kes(oneri))
    }

    @Test
    fun `taban sifirken hicbir sey kesilmez`() {
        // Dosya kökünde yazılıyorsa her satır >= 0 girintili.
        val oneri = "class A {}\nclass B {}"
        assertEquals("class A {}\nclass B {}", kes(oneri, taban = 0))
    }

    // ---- T32: boş satır bloğu bitirmez ----

    @Test
    fun `arada bos satir varsa oneri devam eder`() {
        // Boş satırın girintisi 0'dır; kural olmasaydı öneri orada biterdi.
        val oneri = "int a = 1;\n\n    int b = 2;"
        assertEquals("int a = 1;\n\n    int b = 2;", kes(oneri))
    }

    @Test
    fun `sadece bosluk iceren satir da bloğu bitirmez`() {
        val oneri = "int a = 1;\n  \n    int b = 2;"
        assertTrue(kes(oneri).contains("int b = 2;"))
    }

    @Test
    fun `sondaki bos satirlar atilir`() {
        assertEquals("int a = 1;", kes("int a = 1;\n\n\n"))
    }

    // ---- T33: üst sınır ----

    @Test
    fun `max satir her kosulda uygulanir`() {
        val oneri = (1..20).joinToString("\n") { "    satir$it();" }
        assertEquals(3, kes(oneri, max = 3).lines().size)
    }

    @Test
    fun `max satir sifirsa hicbir sey donmez`() {
        assertEquals("", kes("    a();", max = 0))
    }

    @Test
    fun `model hic durmazsa ust sinir yakalar`() {
        // Girinti kesicinin yakalayamadığı durum: her satır blok içinde.
        val oneri = (1..100).joinToString("\n") { "    x$it();" }
        assertTrue(kes(oneri, max = 6).lines().size <= 6)
    }

    // ---- baştaki yeni satır (T10 ölçümünde bulundu) ----

    @Test
    fun `bastaki yeni satir atilir`() {
        // Gerçek model çıktısı: imleç zaten girintideyken model kendi satırını
        // açıyor. Atılmazsa imlecin satırı boş kalır.
        assertEquals("int c = a + b;", kes("\n    int c = a + b;"))
    }

    @Test
    fun `yalnizca bir bastaki yeni satir atilir`() {
        // İkinci boş satır kasıtlı olabilir.
        assertEquals("\n    int c = 1;", kes("\n\n    int c = 1;"))
    }

    @Test
    fun `crlf de temizlenir`() {
        assertEquals("int c = 1;", kes("\r\n    int c = 1;"))
    }

    // ---- T34: kötü girdi ----

    @Test
    fun `bos oneri bos doner`() {
        assertEquals("", kes(""))
    }

    @Test
    fun `yalnizca bosluk bos doner`() {
        assertEquals("", kes("   \n  \n"))
    }

    @Test
    fun `model girintisiz yazdiysa blok yine de butun kalir`() {
        // DEĞİŞTİRİLDİ 2026-09-04. Eskiden "ilk satırdan sonrası kesilir" diye
        // yazılmıştı ve o **hatalı davranışı kodluyordu**: gerçek modelde
        // tersCevir gibi fonksiyonlarda beş satırlık doğru bir gövde tek işe
        // yaramaz satıra iniyordu.
        //
        // Artık eşik modelin kendi hizasından türüyor, blok bütün kalıyor.
        assertEquals("int a = 1;\nint b = 2;", kes("int a = 1;\nint b = 2;"))
    }

    @Test
    fun `girintisiz yazilmis blokta kapanis parantezi yine de kesilir`() {
        // Eşik gevşedi diye kapanış kaçmamalı: derinlik sayacı ayırıyor.
        assertEquals("int a = 1;", kes("int a = 1;\n}\nvoid digeri() {"))
    }

    @Test
    fun `girintisiz blokta ic kapanis korunur`() {
        // İç `for`'un kapanışı bloğu bitirmez — açılmış bir parantezi kapatıyor.
        val oneri = "String t = \"\";\nfor (int i = 0; i < 3; i++) {\n    t += i;\n}\nreturn t;"
        assertEquals(oneri, kes(oneri, max = 10))
    }

    // ---- girinti ölçümü ----

    @Test
    fun `girinti genisligi bosluk sayar`() {
        assertEquals(4, GirintiKesici.girintiGenisligi("    kod"))
        assertEquals(0, GirintiKesici.girintiGenisligi("kod"))
    }

    @Test
    fun `sekme sabit genislikte sayilir`() {
        assertEquals(GirintiKesici.SEKME_GENISLIGI, GirintiKesici.girintiGenisligi("\tkod"))
        assertEquals(GirintiKesici.SEKME_GENISLIGI + 2, GirintiKesici.girintiGenisligi("\t  kod"))
    }

    @Test
    fun `tamamen bosluk olan satirin genisligi uzunlugudur`() {
        assertEquals(6, GirintiKesici.girintiGenisligi("      "))
    }
}
