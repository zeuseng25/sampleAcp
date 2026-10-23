package dev.acp.completion

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import dev.acp.llm.CompletionRequest
import dev.acp.llm.completion.FimBicimi
import dev.acp.llm.completion.FimPromptKurucu
import dev.acp.llm.completion.OneriTemizleyici
import dev.acp.llm.completion.TamamlamaBaglami
import dev.acp.llm.completion.TamamlamaKipi
import dev.acp.llm.completion.TamamlamaKipiSecici
import dev.acp.service.LlmClientService
import dev.acp.settings.AcpSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Satır içi tamamlama sağlayıcısı (spec 006).
 *
 * Platformun kendi altyapısı kullanılıyor: soluk metnin çizimi, kabul tuşu,
 * yazınca kaybolma, IDE'nin kendi tamamlama listesiyle uyum ve tek adımlı geri
 * alma buradan geliyor. Kendi inlay çizimimizi yazmıyoruz.
 *
 * **Bu sınıf hiçbir hatayı kullanıcıya göstermez.** Yazarken hata penceresi
 * açmak kabul edilemez (spec 006 Davranış Kuralları). `LlmClient` hataları tipli
 * fırlatır — sessizliğin istendiği yer burası, istemci değil.
 *
 * **Kullanıcının kodu günlüğe yazılmaz.** Aşağıdaki `LOG` çağrılarında yalnızca
 * hata türü ve uzunluk geçer, içerik geçmez.
 */
internal class AcpInlineCompletionProvider : DebouncedInlineCompletionProvider() {

    override val id = InlineCompletionProviderID("Acp")

    private val ayarlar get() = AcpSettings.getInstance().state

    /**
     * Ayar kapalıysa **hiç istek gönderilmeden** false döner (spec 006 H3).
     *
     * Platform bunu her tetiklemede çağırıyor; buradan false dönerse
     * [getSuggestionDebounced] hiç çağrılmaz.
     */
    override fun isEnabled(event: InlineCompletionEvent): Boolean = ayarlar.tamamlamaAcikMi()

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration =
        ayarlar.completionDebounceMs.coerceAtLeast(0).milliseconds

    override suspend fun getSuggestionDebounced(
        request: InlineCompletionRequest,
    ): InlineCompletionSuggestion {
        val oneri = try {
            uret(request)
        } catch (e: CancellationException) {
            // İmleç oynadı veya kullanıcı yazmaya devam etti. Hata değil.
            throw e
        } catch (e: Throwable) {
            // Sunucu kapalı, zaman aşımı, bozuk yanıt — hepsi aynı sonuca çıkar:
            // öneri gösterilmez ve kullanıcı rahatsız edilmez.
            LOG.debug("Satır içi tamamlama başarısız: ${e.javaClass.simpleName}")
            null
        } ?: return InlineCompletionSuggestion.Empty

        return InlineCompletionSingleSuggestion.build {
            emit(InlineCompletionGrayTextElement(oneri))
        }
    }

    private suspend fun uret(request: InlineCompletionRequest): String? {
        val bicim = ayarlar.fimBicimi
        // isEnabled zaten baktı; yarış durumunda (kullanıcı istek uçarken kapattı)
        // ikinci kontrol devreye girer.
        if (bicim == FimBicimi.KAPALI) return null

        // Belge okuması okuma eylemi içinde olmalı; başka bir iş parçacığı
        // aynı anda düzenliyor olabilir.
        val metin = readAction { request.document.text }
        val imlec = request.endOffset

        // Kip kararının TEK yeri. Üye erişimi kontrolü de bunun içinde:
        // orada IDE'nin tip bilen listesi bizden iyi, istek bile göndermiyoruz.
        val kip = TamamlamaKipiSecici.sec(metin, imlec)
        if (kip == TamamlamaKipi.YOK) return null

        val baglam = TamamlamaBaglami.kur(metin, imlec)

        // Sohbet biçimi ham prompt kurmaz; henüz desteklenmiyor (spec 006 planı).
        if (!bicim.hamUcKullanir) return null

        val istemci = LlmClientService.getInstance().tamamlamaIstemcisi() ?: return null

        val sonuc = withContext(Dispatchers.IO) {
            istemci.complete(
                CompletionRequest(
                    model = ayarlar.tamamlamaModeli(),
                    prompt = FimPromptKurucu.kur(bicim, baglam.oncesi, baglam.sonrasi),
                    // Blok kipinde daha yüksek tavan. T10 ölçümü: model kendi
                    // durduğu yerde durduğu için tavanı yükseltmek gecikme
                    // doğurmuyor — tek satırın hızı etkilenmiyor (spec 007 H4).
                    maxTokens = when (kip) {
                        TamamlamaKipi.COK_SATIR -> ayarlar.completionBlockMaxTokens
                        else -> ayarlar.completionMaxTokens
                    },
                    // Tamamlamada tutarlılık yaratıcılıktan önemli: aynı yerde
                    // her seferinde farklı öneri görmek rahatsız edici olurdu.
                    temperature = 0.0,
                ),
            )
        }

        val oneri = OneriTemizleyici.temizle(
            sonuc.text,
            bicim,
            baglam.imlecSonrasiSatir,
            kip = kip,
            tabanGirinti = baglam.tabanGirinti,
            maxSatir = ayarlar.completionMaxLines,
        )

        /*
         * Teşhis kaydı.
         *
         * Hataları sessizce yutma kararı doğruydu ama teşhis imkânı bırakmamıştı:
         * öneri görünmediğinde sebebini anlamanın hiçbir yolu yoktu — model boş
         * mu döndü, temizleyici mi kesti, zaman aşımı mı oldu?
         *
         * Yalnızca ÖLÇÜLER yazılır: token sayısı, satır sayısı, bitiş nedeni.
         * Kullanıcının kodu veya modelin ürettiği metin GEÇMEZ.
         *
         * Görmek için: Help → Diagnostic Tools → Debug Log Settings → "dev.acp"
         */
        if (LOG.isDebugEnabled) {
            LOG.debug(
                "tamamlama kip=$kip token=${sonuc.usage?.completionTokens} " +
                    "bitis=${sonuc.finishReason} hamSatir=${sonuc.text.count { it == '\n' } + 1} " +
                    "sonuc=${if (oneri == null) "yok" else "${oneri.count { it == '\n' } + 1} satır"} " +
                    "maxSatir=${ayarlar.completionMaxLines}",
            )
        }
        return oneri
    }

    private companion object {
        val LOG = logger<AcpInlineCompletionProvider>()
    }
}
