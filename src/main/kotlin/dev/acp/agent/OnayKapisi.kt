package dev.acp.agent

import dev.acp.komut.KomutSonucu
import java.nio.file.Path

/**
 * Komut çalışmadan önce kullanıcıya sorulur (spec 009 H2).
 *
 * Onay bir yönerge değil, yetenek sınırıdır: model komutu istemeyi seçebilir,
 * çalıştırmayı seçemez.
 */
internal interface OnayKapisi {
    /** Kullanıcı karar verene kadar askıda kalır; true = Çalıştır. İptal edilebilir. */
    suspend fun iste(komut: List<String>): Boolean

    /** Sonuç kullanıcıya gösterilsin diye (atlandı dahil). */
    fun bitti(sonuc: KomutSonucu)
}

/** Komutu çalıştıran taraf; testte sahtelenir. */
internal fun interface Calistirici {
    suspend fun calistir(argumanlar: List<String>, kok: Path): KomutSonucu
}
