package dev.acp.llm.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** İlişki sonucunun modele giden metni (spec 010 T30). */
class IliskiBicimiTest {

    private val y1 = Yer("src/Kullanici.java:3", "Kullanici.yaz", "a.calis();")
    private val y2 = Yer("src/app.js:2", null, "import { topla } from './util'")

    @Test
    fun `her yer konum kapsayan ve satirla yazilir`() {
        val metin = IliskiBicimi.metin(IliskiSonucu.Bulundu(listOf(y1, y2), toplam = 2))
        assertEquals(
            "src/Kullanici.java:3 [Kullanici.yaz] a.calis();\n" +
                "src/app.js:2 [dosya düzeyi] import { topla } from './util'",
            metin,
        )
    }

    @Test
    fun `fazlasi sayiyla bildirilir`() {
        val metin = IliskiBicimi.metin(IliskiSonucu.Bulundu(listOf(y1), toplam = 57))
        assertTrue(metin.endsWith("... (56 yer daha)"), metin)
    }

    @Test
    fun `hic yoksa acikca soylenir`() {
        assertEquals("kullanım yok", IliskiBicimi.metin(IliskiSonucu.Bulundu(emptyList(), 0)))
    }

    @Test
    fun `belirsiz adaylar satirla listelenir ve secim istenir`() {
        val metin = IliskiBicimi.metin(IliskiSonucu.Belirsiz(listOf(y1, y2)))
        assertTrue(metin.startsWith("BELİRSİZ:"), metin)
        assertTrue("src/Kullanici.java:3" in metin && "src/app.js:2" in metin, metin)
        assertTrue("satir" in metin, "modele nasıl seçeceği söylenmeli: $metin")
    }

    @Test
    fun `yok sebebiyle doner`() {
        assertEquals("HATA: 'x' bulunamadı. Benzer: y, z", IliskiBicimi.metin(IliskiSonucu.Yok("'x' bulunamadı. Benzer: y, z")))
    }

    @Test
    fun `uzun satir kirpilir`() {
        val uzun = Yer("a:1", "f", "x".repeat(500))
        val metin = IliskiBicimi.metin(IliskiSonucu.Bulundu(listOf(uzun), 1))
        assertTrue(metin.length < 250, "satır kırpılmadı: ${metin.length}")
    }
}
