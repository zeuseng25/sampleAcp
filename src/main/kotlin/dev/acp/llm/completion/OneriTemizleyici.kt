package dev.acp.llm.completion

/**
 * Modelin ham çıktısını gösterilebilir tek satırlık bir öneriye indirger.
 *
 * Model iyi davranmayabilir: kod çiti ekleyebilir, birden fazla satır yazabilir,
 * özel belirteç sızdırabilir veya imlecin **sonrasındaki** kodu tekrarlayabilir.
 * Sonuncusu en tehlikelisi — temizlenmezse kod iki kez yazılır.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object OneriTemizleyici {

    /**
     * @param hamYanit modelden gelen çıktı
     * @param bicim kaçan belirteçleri bilmek için
     * @param imlecSonrasiSatir imlecin bulunduğu satırın imleçten sonraki kısmı;
     *   öneri bunu tekrarlıyorsa kırpılır
     * @return gösterilecek öneri, gösterilmeyecekse `null`
     */
    fun temizle(
        hamYanit: String,
        bicim: FimBicimi,
        imlecSonrasiSatir: String,
        /**
         * Varsayılanı `TEK_SATIR`: 006'nın davranışı. Çağıranlar kipi açıkça
         * vermedikçe hiçbir şey değişmez.
         */
        kip: TamamlamaKipi = TamamlamaKipi.TEK_SATIR,
        tabanGirinti: Int = 0,
        maxSatir: Int = 6,
    ): String? {
        var metin = hamYanit

        // 1) Kaçan özel belirteçler — bazı modeller bunları çıktıya sızdırıyor.
        for (belirtec in FimPromptKurucu.kacanBelirtecler(bicim)) {
            metin = metin.replace(belirtec, "")
        }

        // 2) Kod çiti — FIM'de olmamalı ama sohbet biçiminde ve bazı modellerde geliyor.
        metin = citleriAyikla(metin)

        // 3) Uzunluk kararı kipe bağlı.
        metin = when (kip) {
            // Spec 006 Belirsizlik 4.
            TamamlamaKipi.TEK_SATIR -> metin.substringBefore('\n')
            // Spec 007: blok içinde kal.
            TamamlamaKipi.COK_SATIR -> GirintiKesici.kes(metin, tabanGirinti, maxSatir)
            // Kip seçici bu durumda istek bile göndermez; savunma amaçlı.
            TamamlamaKipi.YOK -> return null
        }

        // 4) Sondaki boşluk anlamsız; baştaki boşluk anlamlı olabilir (girinti).
        metin = metin.trimEnd()

        // 5) Öneri imlecin sonrasını tekrarlıyorsa kırp.
        metin = sonekTekrariniKirp(metin, imlecSonrasiSatir)

        return metin.ifBlank { null }
    }

    /**
     * ```` ```kotlin ```` gibi bir çit satırıyla başlıyorsa çitin içini alır.
     * Çit yoksa metin olduğu gibi döner.
     */
    private fun citleriAyikla(metin: String): String {
        val kirpik = metin.trimStart()
        if (!kirpik.startsWith("```") && !kirpik.startsWith("~~~")) return metin

        val satirlar = kirpik.lines()
        val icerik = satirlar.drop(1) // açılış çiti (ve varsa dil etiketi)
        val kapanis = icerik.indexOfFirst { it.trimStart().startsWith("```") || it.trimStart().startsWith("~~~") }
        return if (kapanis >= 0) icerik.take(kapanis).joinToString("\n") else icerik.joinToString("\n")
    }

    /**
     * Model imlecin sonrasındaki metni de yazdıysa o kısmı atar.
     *
     * Örnek: imleçten sonra `)` varken model ` a + b)` derse, `)` iki kez yazılır.
     * En uzun örtüşmeden başlayarak aranıyor.
     */
    private fun sonekTekrariniKirp(oneri: String, imlecSonrasiSatir: String): String {
        val sonek = imlecSonrasiSatir.trim()
        if (sonek.isEmpty() || oneri.isEmpty()) return oneri

        // Tam sonek öneride geçiyorsa oradan kes.
        if (oneri.endsWith(sonek)) return oneri.dropLast(sonek.length).trimEnd()

        // Kısmi örtüşme: önerinin sonu, sonekin başıyla aynı mı.
        val enFazla = minOf(oneri.length, sonek.length)
        for (uzunluk in enFazla downTo 2) {
            if (oneri.regionMatches(oneri.length - uzunluk, sonek, 0, uzunluk)) {
                return oneri.dropLast(uzunluk).trimEnd()
            }
        }
        return oneri
    }
}
