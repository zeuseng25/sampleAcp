package dev.acp.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import dev.acp.llm.completion.FimBicimi

/**
 * Gizli olmayan ayarlar. `acp.xml` içinde saklanır.
 *
 * **API anahtarı burada DEĞİLDİR** — o [AcpSecrets] üzerinden işletim sisteminin
 * anahtar zincirine gider. Buraya eklenirse düz metin olarak diske yazılır.
 *
 * Altın Kural 6: ayarlanabilir her değerin tek tanım yeri burasıdır. Kullanım
 * yerinde sabit yazılmaz, hep buradan okunur. Sunucu adresi ve modelin varsayılanı
 * **boştur** — kuruma özgü bilgi pakete gömülmez.
 */
@Service(Service.Level.APP)
@State(name = "AcpSettings", storages = [Storage("acp.xml")])
internal class AcpSettings : SimplePersistentStateComponent<AcpSettings.State>(State()) {

    class State : BaseState() {
        /** Model sunucusunun kök adresi. Boş = yapılandırılmamış. */
        var baseUrl by string("")

        /** Kullanılacak model. Bağlantı testinden gelen listeden seçilir. */
        var model by string("")

        /** Bağlama gönderilecek yaklaşık token üst sınırı. */
        var contextTokenBudget by property(8_000)

        var maxTokens by property(2_048)

        /**
         * Sıcaklık, **binde tamsayı** olarak (200 = 0.2).
         *
         * Neden ondalık değil: `BaseState`'in float delegesi olduğu belirsiz, ve
         * tamsayı yerel ondalık ayracından (virgül/nokta) bağımsız serileşir.
         */
        var temperatureMilli by property(200)

        var connectTimeoutMs by property(10_000)

        /** Uzun üretimler için cömert; sunucu düşünürken kopmasın. */
        var readTimeoutMs by property(180_000)

        /**
         * Akış başladıktan sonra sunucudan **hiç veri gelmeden** beklenecek süre.
         *
         * [readTimeoutMs] yalnızca yanıt başlığına kadar geçerlidir; gövde
         * akarken devreye girmez. Bu olmadan sunucu akış ortasında susarsa
         * istemci sonsuza kadar bekler.
         */
        var idleTimeoutMs by property(120_000)

        /** Modelin düşünme bölümü üretmesi istensin mi. */
        var enableThinking by property(false)

        // ---- satır içi tamamlama (spec 006) -------------------------------

        /**
         * FIM biçimi, [FimBicimi] adı olarak. Varsayılan `KAPALI`.
         *
         * Spec 006 H3: sohbette kullanıcı her seferinde bilinçli bir eylem yapar;
         * satır içi tamamlamada kod yazdıkça kendiliğinden dışarı gider. Bu yüzden
         * kullanıcı açıkça açana kadar hiçbir istek gönderilmez.
         *
         * Enum yerine metin saklanıyor: ileride bir biçim adı değişirse veya
         * kaldırılırsa eski `acp.xml` okunurken çökmesin.
         */
        var completionFormat by string(FimBicimi.KAPALI.name)

        /**
         * Tamamlama için kullanılacak model. Boş = sohbet modeli kullanılır.
         *
         * Sohbetten ayrı olmasının sebebi: FIM eğitimi coder varyantlarında.
         * Kullanıcı sohbette büyük bir modeli, tamamlamada küçük ve hızlı bir
         * coder modelini kullanmak isteyebilir.
         */
        var completionModel by string("")

        /** Tek satırlık öneri için yeterli. */
        var completionMaxTokens by property(64)

        /**
         * Çok satırlı (blok) öneride token tavanı — spec 007.
         *
         * Tek satırınkinden ayrı: çok satır eklemek, tek satırın gecikmesini
         * artırmamalı (spec 007 H4).
         *
         * 256 pahalı görünüyor ama değil. T10 ölçümü: model boş bir gövdede
         * `max_tokens` 64 de olsa 256 da olsa **aynı 24 token'ı** üretip
         * duruyor. Tavan bir maliyet değil, üst sınır.
         */
        var completionBlockMaxTokens by property(256)

        /**
         * Çok satırlı önerinin satır üst sınırı.
         *
         * **Emniyet supabıdır, şekillendirici değil.** Önerinin nerede biteceğine
         * girinti kesici karar verir; bu yalnızca model hiç durmazsa devreye girer.
         *
         * İlk değeri 6'ydı ve yanlıştı: gerçek bir `enBuyuguBul` gövdesi 8 satır
         * çıktı, sınır `return`'ü kesti. Sınır sıradan bir gövdeyi kesiyorsa
         * emniyet supabı olmaktan çıkıp öneriyi şekillendiriyor demektir.
         */
        var completionMaxLines by property(12)

        /** Debounce: yazarken her tuşta istek gitmesin (spec 006 H1). */
        var completionDebounceMs by property(300)

        /**
         * Tamamlama isteği zaman aşımı. Sohbetinkinden çok kısa: geç gelen öneri
         * zaten atılacak, beklemenin faydası yok.
         */
        var completionTimeoutMs by property(3_000)

        // ---- çok dosyalı düzenleme / ajan (spec 008) ---------------------

        /**
         * Ajanın dosyalara nasıl dokunacağı: `ONIZLE` veya `DOGRUDAN`.
         *
         * **Varsayılan `ONIZLE`.** 006 ve 007'de "öneri asla onay olmadan koda
         * yazılmaz" kuralını koymuştuk; `DOGRUDAN` o kuraldan ayrılan tek yer ve
         * kullanıcının **açıkça seçmesi** gerekiyor (spec 008 H4).
         *
         * Metin olarak saklanıyor: ileride bir kip adı değişirse eski `acp.xml`
         * okunurken çökmesin, tanınmayan değer güvenli tarafa düşsün.
         */
        var agentUygulamaKipi by string(AjanKipi.ONIZLE.name)

        /**
         * Ajanın en fazla kaç tur döneceği.
         *
         * T10 ölçümünde iyi bir koşu 3 turda bitti; kötü koşu 20'ye dayandı ve
         * 204 bin token harcadı. 20, "işini bitiremeyen bir ajanı durdur" sınırı.
         */
        var agentMaxAdim by property(20)

        /** Tek işte dokunulabilecek dosya üst sınırı. */
        var agentMaxDosya by property(20)

        // ---- sohbette kod araştırma / Uzman (spec 010) --------------------

        /**
         * Sohbet kipinde model kodu araştırabilir mi (salt okunur araçlar).
         * Kapalıyken istek 002'deki ile birebir aynıdır. Uzman'ı etkilemez —
         * Uzman'ın işi araştırmak.
         */
        var sohbetArastirma by property(true)

        /** Sohbette araştırma tur sınırı; sohbet kısa kalmalı. */
        var sohbetMaxAdim by property(6)

        /** Uzman'da araştırma tur sınırı; çağıran ve çağrılanlara inmek için daha yüksek. */
        var uzmanMaxAdim by property(15)

        // ---- derleme hatasını düzeltme (spec 009) -------------------------

        /** Doğrulama komutunun zaman aşımı; aşılınca süreç ağacı öldürülür. */
        var agentKomutZamanAsimiMs by property(300_000)

        val temperature: Double get() = temperatureMilli / 1000.0

        /**
         * Ayardaki kip. Tanınmayan değer `ONIZLE`'ye düşer — kod onay olmadan
         * değişmesin.
         */
        val ajanKipi: AjanKipi
            get() = AjanKipi.entries.firstOrNull { it.name == agentUygulamaKipi } ?: AjanKipi.ONIZLE

        /**
         * Ayardaki biçim. Tanınmayan bir değer yazılıysa (elle düzenleme, eski
         * sürümden kalma) `KAPALI`'ya düşer — kod sessizce dışarı gitmesin.
         */
        val fimBicimi: FimBicimi
            get() = FimBicimi.entries.firstOrNull { it.name == completionFormat } ?: FimBicimi.KAPALI

        /** İstek göndermeye yetecek kadar ayar girilmiş mi. */
        fun yapilandirildi(): Boolean =
            !baseUrl.isNullOrBlank() && !model.isNullOrBlank()

        /**
         * Tamamlama isteği gönderilebilir mi. Biçim `KAPALI` ise **hiçbir koşulda**
         * gönderilmez (spec 006 H3).
         */
        fun tamamlamaAcikMi(): Boolean =
            fimBicimi != FimBicimi.KAPALI && !baseUrl.isNullOrBlank() && tamamlamaModeli().isNotBlank()

        /** Tamamlama modeli boşsa sohbet modeline düşülür. */
        fun tamamlamaModeli(): String =
            completionModel?.takeIf { it.isNotBlank() } ?: model.orEmpty()
    }

    companion object {
        fun getInstance(): AcpSettings = service()
    }
}
