package dev.acp.llm.text

import kotlin.math.ceil

/**
 * Metnin kaç token tutacağını **kabaca** tahmin eder.
 *
 * Gerçek tokenizer'ı çalıştırmıyoruz: model sunucusundaki tokenizer'ı burada
 * taklit etmek hem ağır hem de modele göre değişir. Bu tahmin bir **emniyet
 * payı**dır, ölçüm değil — bağlamın bütçeyi kabaca aşmamasını sağlar.
 *
 * Katsayı Türkçe + kod karışımı için tutucu seçildi. İleride sunucunun
 * bildirdiği gerçek `usage.prompt_tokens` ile kalibre edilebilir (spec 001
 * Belirsizlik 3).
 */
internal object TokenEstimator {

    private const val KARAKTER_BASINA_TOKEN = 3.5

    fun estimate(text: String): Int =
        ceil(text.length / KARAKTER_BASINA_TOKEN).toInt()

    /** [tokenSayisi] token'a karşılık gelen kabaca karakter sayısı. */
    fun karakterButcesi(tokenSayisi: Int): Int =
        (tokenSayisi * KARAKTER_BASINA_TOKEN).toInt()
}
