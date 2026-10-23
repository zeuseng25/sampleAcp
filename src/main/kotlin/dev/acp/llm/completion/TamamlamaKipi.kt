package dev.acp.llm.completion

/**
 * İmlecin konumuna göre ne tür öneri isteneceği (spec 007).
 *
 * Karar kullanıcıya sorulmaz: ayardan seçtirmek, kullanıcıyı her bağlam
 * değişiminde ayara gitmeye zorlardı.
 */
internal enum class TamamlamaKipi {
    /** Öneri istenmez. */
    YOK,

    /** Satır ortası — 006'daki davranış. */
    TEK_SATIR,

    /** İmlecin solunda yalnızca boşluk: blok önerisi. */
    COK_SATIR,
}

/**
 * Kip kararının **tek** yeri.
 *
 * Sağlayıcı yalnızca burayı çağırır. Karar dağıtılsaydı (üye erişimi kontrolü
 * bir yerde, boş satır kontrolü başka yerde) birinin unutulması sessizce yanlış
 * kipte istek göndermeye yol açardı.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object TamamlamaKipiSecici {

    fun sec(metin: String, imlec: Int): TamamlamaKipi {
        val konum = imlec.coerceIn(0, metin.length)

        // 006'da alınan karar: modelin tip bilgisi yok, IDE'nin var. Alan IDE'nin.
        if (TamamlamaBaglami.uyeErisimiSonrasiMi(metin, konum)) return TamamlamaKipi.YOK

        return if (solundaYalnizcaBosluk(metin, konum)) {
            TamamlamaKipi.COK_SATIR
        } else {
            TamamlamaKipi.TEK_SATIR
        }
    }

    /**
     * İmleç ile satır başı arasında yalnızca boşluk mu var?
     *
     * Bu, "boş bir bloğa yeni girildi" durumunun dilden bağımsız işareti.
     * Satır ortasında (`int x = ‸`) uzun öneri göstermek rahatsız edici olur ve
     * neredeyse hep yanlış çıkar — o yüzden orada tek satırda kalınır.
     */
    private fun solundaYalnizcaBosluk(metin: String, imlec: Int): Boolean {
        var i = imlec
        while (i > 0) {
            val c = metin[i - 1]
            if (c == '\n') return true
            if (!c.isWhitespace()) return false
            i--
        }
        // Dosyanın en başı: satır başı sayılır.
        return true
    }
}
