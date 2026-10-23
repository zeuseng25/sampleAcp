package dev.acp.llm.completion

/**
 * Fill-in-the-middle prompt biçimi.
 *
 * **Neden ayardan seçiliyor:** FIM belirteçleri model ailesine göre değişiyor.
 * Tek biçim varsayılsaydı, model değiştiğinde (Qwen → GLM → Kimi) yeni model
 * belirteçleri **düz metin** sanır ve hata vermeden kötü öneri üretirdi.
 * Görünür bir ayar, sessiz bir tahminden iyidir.
 */
internal enum class FimBicimi(val gorunenAd: String) {
    /** Özellik kapalı; hiç istek gönderilmez. */
    KAPALI("Kapalı"),

    QWEN("Qwen"),
    DEEPSEEK("DeepSeek"),
    STARCODER("StarCoder"),
    CODELLAMA("CodeLlama"),

    /**
     * FIM desteği olmayan modeller için zayıf yedek: sohbet ucuna talimatlı
     * istek gönderilir. İmleç sonrasını modelin dikkate alması garanti değil.
     */
    SOHBET("Sohbet tarzı"),
    ;

    /** Ham tamamlama ucu (`/v1/completions`) mu kullanılacak? */
    val hamUcKullanir: Boolean get() = this != KAPALI && this != SOHBET
}

/**
 * İmlecin öncesi ve sonrasından modele gidecek ham prompt'u kurar.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object FimPromptKurucu {

    fun kur(bicim: FimBicimi, oncesi: String, sonrasi: String): String = when (bicim) {
        FimBicimi.QWEN ->
            "<|fim_prefix|>$oncesi<|fim_suffix|>$sonrasi<|fim_middle|>"

        FimBicimi.DEEPSEEK ->
            "<｜fim▁begin｜>$oncesi<｜fim▁hole｜>$sonrasi<｜fim▁end｜>"

        FimBicimi.STARCODER ->
            "<fim_prefix>$oncesi<fim_suffix>$sonrasi<fim_middle>"

        FimBicimi.CODELLAMA ->
            "<PRE> $oncesi <SUF>$sonrasi <MID>"

        FimBicimi.SOHBET ->
            error("Sohbet biçimi ham prompt kullanmaz; sohbet ucundan gider.")

        FimBicimi.KAPALI ->
            error("Tamamlama kapalı; prompt kurulmamalıydı.")
    }

    /**
     * Modelin çıktısında kaçabilecek belirteçler. Yanıt temizlenirken kesilir —
     * bazı modeller bunları çıktıya sızdırıyor.
     */
    fun kacanBelirtecler(bicim: FimBicimi): List<String> = when (bicim) {
        FimBicimi.QWEN -> listOf(
            "<|fim_prefix|>", "<|fim_suffix|>", "<|fim_middle|>",
            "<|fim_pad|>", "<|endoftext|>", "<|repo_name|>", "<|file_sep|>",
        )
        FimBicimi.DEEPSEEK -> listOf(
            "<｜fim▁begin｜>", "<｜fim▁hole｜>", "<｜fim▁end｜>", "<|EOT|>",
        )
        FimBicimi.STARCODER -> listOf(
            "<fim_prefix>", "<fim_suffix>", "<fim_middle>", "<|endoftext|>", "<file_sep>",
        )
        FimBicimi.CODELLAMA -> listOf("<PRE>", "<SUF>", "<MID>", "<EOT>")
        FimBicimi.SOHBET, FimBicimi.KAPALI -> emptyList()
    }
}
