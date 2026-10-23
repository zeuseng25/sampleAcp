package dev.acp.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.acp.AcpBundle
import dev.acp.llm.ChatDelta
import dev.acp.llm.ChatMessage
import dev.acp.llm.ChatRequest
import dev.acp.llm.LlmClient
import dev.acp.llm.LlmError
import dev.acp.agent.IdeAracYurutucu
import dev.acp.llm.agent.AjanDongusu
import dev.acp.llm.agent.AjanOlayi
import dev.acp.llm.agent.AracSetleri
import dev.acp.llm.agent.BitisNedeni
import dev.acp.llm.agent.DuzenlemePlani
import dev.acp.context.CompositeContextProvider
import dev.acp.context.ContextRequest
import dev.acp.editor.ResultApplier
import dev.acp.editor.ScratchFileWriter
import dev.acp.editor.TargetAnchor
import dev.acp.llm.prompt.AksiyonTuru
import dev.acp.llm.prompt.PromptBuilder
import dev.acp.llm.prompt.PromptSelector
import dev.acp.llm.util.coalesceText
import dev.acp.settings.AcpSecrets
import dev.acp.settings.AcpSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Sohbet oturumunun sahibi.
 *
 * Durum **panelde değil burada** tutulur: panel sekmesi kapatılıp açıldığında
 * konuşma yerinde durmalı (spec 002 H1). Panel yalnızca bir görüntüleyicidir.
 *
 * Coroutine kapsamı kurucuya platform tarafından enjekte edilir; proje kapanınca
 * iptal edilir.
 */
@Service(Service.Level.PROJECT)
internal class ChatSessionService(
    private val project: Project,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val sayac = AtomicLong(0)
    private var akan: Job? = null

    /** Kodu değiştiren eylemin hedefi; yanıt gelene kadar saklanır. */
    private var bekleyenCapa: TargetAnchor? = null
    private var bekleyenTur: AksiyonTuru? = null
    private var bekleyenUzanti: String? = null

    /** Açık önizleme penceresi; uygulanınca veya iptal edilince kapatılır. */
    private var onizlemePenceresi: com.intellij.openapi.ui.WindowWrapper? = null

    /** Sunucu araç çağırmayı reddettiyse oturumun geri kalanında denenmez (spec 010 T44). */
    @Volatile
    private var araclarDesteklenmiyor = false

    fun send(soru: String, kip: SohbetKipi = SohbetKipi.SOHBET) {
        val metin = soru.trim()
        if (metin.isEmpty()) return
        onizlemeyiKapat()
        bekleyenCapa?.birak()
        bekleyenCapa = null
        bekleyenTur = null
        gonder(gorunen = metin, talimat = metin, kip = kip)
    }

    /**
     * Hazır bir eylemi çalıştırır (spec 003).
     *
     * Kullanıcı panelde kısa eylem adını görür; modele prompt şablonu gider.
     * Seçili kod ayrıca bağlam olarak eklenir — bağlam toplama zaten seçimi
     * en yüksek öncelikle alıyor, ayrı bir yol açmaya gerek yok.
     */
    fun sendAction(
        tur: AksiyonTuru,
        gorunenAd: String,
        dilEtiketi: String?,
        capa: TargetAnchor? = null,
        /** Seçimin göreli yolu ve satırları ("a/B.kt, satır 2–3"); araçlar dosyayı bulsun (spec 010). */
        konum: String? = null,
    ) {
        onizlemeyiKapat()
        bekleyenCapa?.birak()
        bekleyenCapa = capa
        bekleyenTur = tur
        bekleyenUzanti = dilEtiketi
        val sablon = PromptSelector.sablon(tur, dilEtiketi)
        gonder(
            gorunen = gorunenAd,
            talimat = konum?.let { "$sablon\n\nKonum: $it" } ?: sablon,
            // "Açıkla" Uzman'ın işi: kodu ilişkileriyle araştırıp anlatır (spec 010 H6).
            kip = if (tur == AksiyonTuru.ACIKLA) SohbetKipi.UZMAN else SohbetKipi.SOHBET,
        )
    }

    /** Bekleyen sonucu karşılaştırmalı olarak gösterir. */
    fun onizle() {
        val sonuc = _state.value.uygulanabilir ?: return
        val capa = bekleyenCapa ?: return
        onizlemeyiKapat() // ikinci kez basılırsa iki pencere kalmasın
        onizlemePenceresi = ResultApplier.onizle(
            project,
            ResultApplier.diffIstegiKur(project, capa, sonuc.kod, capa.file.fileType),
        )
    }

    /** Bekleyen sonucu editöre uygular. Bayatsa uygulamaz, durumu bildirir. */
    fun uygula() {
        val sonuc = _state.value.uygulanabilir ?: return
        val capa = bekleyenCapa ?: return
        val komutAdi = AcpBundle.message(
            if (bekleyenTur == AksiyonTuru.DOKUMAN_EKLE) {
                "editor.command.dokumanEkle"
            } else {
                "editor.command.yenidenDuzenle"
            }
        )
        when (ResultApplier.uygula(project, capa, sonuc.kod, komutAdi)) {
            // Uygulandıktan sonra önizleme bayat kalır; kapatılmalı.
            ResultApplier.Sonuc.Uygulandi -> temizleBekleyen()
            ResultApplier.Sonuc.Bayat -> hataGoster("editor.error.stale")
            ResultApplier.Sonuc.SaltOkunur -> hataGoster("editor.error.readonly")
        }
    }

    private fun hataGoster(anahtar: String) {
        _state.update {
            it.copy(
                error = ChatError(AcpBundle.message(anahtar), yenidenDenenebilir = false),
                uygulanabilir = null,
            )
        }
        temizleBekleyen()
    }

    private fun onizlemeyiKapat() {
        onizlemePenceresi?.close()
        onizlemePenceresi = null
    }

    private fun temizleBekleyen() {
        onizlemeyiKapat()
        bekleyenCapa?.birak()
        bekleyenCapa = null
        _state.update { it.copy(uygulanabilir = null) }
    }

    private fun gonder(gorunen: String, talimat: String, kip: SohbetKipi = SohbetKipi.SOHBET) {

        val ayarlar = AcpSettings.getInstance().state
        if (!ayarlar.yapilandirildi()) {
            _state.update {
                it.copy(
                    error = ChatError(
                        AcpBundle.message("chat.error.notConfigured"),
                        yenidenDenenebilir = false, // ayarlar düzelmeden tekrar denemek anlamsız
                    ),
                )
            }
            return
        }

        val kullanici = ChatMessageUi(yeniId(), ChatRole.USER, gorunen, talimat = talimat, kip = kip)
        val asistan = ChatMessageUi(yeniId(), ChatRole.ASSISTANT, streaming = true)
        _state.update {
            it.copy(
                messages = it.messages + kullanici + asistan,
                streaming = true,
                error = null,
            )
        }
        baslat(asistan.id, kullanici.id)
    }

    fun cancel() {
        akan?.cancel()
    }

    /**
     * Son soruyu **baştan** sorar (spec 002 Belirsizlik 2).
     *
     * Yarım yanıt silinir. "Kaldığı yerden devam" bilinçli olarak yok — modeller
     * bunu çoğu zaman kötü yapıp kopuk veya tekrar eden metin üretiyor.
     */
    fun retry() {
        val mesajlar = _state.value.messages
        val sonuncu = mesajlar.lastOrNull() ?: return
        if (sonuncu.role != ChatRole.ASSISTANT) return

        val kalan = mesajlar.dropLast(1)
        if (kalan.lastOrNull()?.role != ChatRole.USER) return

        val yeniAsistan = ChatMessageUi(yeniId(), ChatRole.ASSISTANT, streaming = true)
        _state.update { it.copy(messages = kalan + yeniAsistan, streaming = true, error = null) }
        // Bağlam yeniden toplanmaz: aynı soru aynı bağlamla sorulmalı ki
        // "yeniden dene" öngörülebilir olsun.
        baslat(yeniAsistan.id, kullaniciId = null)
    }

    fun clear() {
        akan?.cancel()
        // Yeni konuşma = yeni oturum; sunucu ayarı bu arada değişmiş olabilir.
        araclarDesteklenmiyor = false
        onizlemeyiKapat()
        bekleyenCapa?.birak()
        bekleyenCapa = null
        bekleyenTur = null
        _state.value = ChatUiState()
    }

    /**
     * Durumu **EDT'de** dinler; [parent] dispose edilince dinleme biter.
     *
     * Kapsam yönetimi burada tutuluyor ki görünüm kendi coroutine kapsamını
     * kurmak zorunda kalmasın — panel kapanınca sızıntı bırakmanın en kolay yolu odur.
     */
    fun observe(parent: Disposable, dinleyici: (ChatUiState) -> Unit) =
        collectOnEdt(parent, state, dinleyici)

    /**
     * Verilen akışı **EDT'de** dinler; [parent] dispose edilince biter.
     *
     * Kapsam yönetimi burada tutuluyor ki görünüm kendi coroutine kapsamını
     * kurmak zorunda kalmasın — panel kapanınca sızıntı bırakmanın en kolay yolu odur.
     */
    fun <T> collectOnEdt(parent: Disposable, akis: Flow<T>, dinleyici: (T) -> Unit) {
        val dinlemeIsi = scope.launch(Dispatchers.EDT) {
            akis.collect { dinleyici(it) }
        }
        Disposer.register(parent) { dinlemeIsi.cancel() }
    }

    // ---- iç ---------------------------------------------------------------

    private fun yeniId() = MessageId(sayac.incrementAndGet())

    private fun baslat(asistanId: MessageId, kullaniciId: MessageId?) {
        akan?.cancel()
        // Önce atanır, sonra başlar: finally'deki "hâlâ güncel iş miyim" kontrolü
        // iş atamadan önce biterse yanlış sonuç vermesin.
        val is_ = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var hata: Throwable? = null
            var deltaSayisi = 0
            var arastirmali = false
            val baslangicAni = System.currentTimeMillis()
            try {
                val ayarlar = AcpSettings.getInstance().state

                // Bağlam yalnızca yeni sorularda toplanır; "yeniden dene" mevcut
                // bağlamı korur.
                if (kullaniciId != null) baglamiEkle(kullaniciId, ayarlar.contextTokenBudget)
                val adres = ayarlar.baseUrl.orEmpty()
                // PasswordSafe bloklayabilir — EDT'de değil, IO'da okunur.
                val anahtar = withContext(Dispatchers.IO) { AcpSecrets.getApiKey(adres) }

                // Prompt içeriği ve anahtar ASLA loglanmaz; yalnızca akışın
                // nerede olduğunu anlamaya yetecek kadarı.
                LOG.info("İstek başlıyor: model=${ayarlar.model}, anahtar=${anahtar != null}")

                val istemci = LlmClientService.getInstance().createClient(adres, anahtar)
                val kip = _state.value.messages.lastOrNull { it.role == ChatRole.USER }?.kip ?: SohbetKipi.SOHBET
                arastirmali = !araclarDesteklenmiyor && (kip == SohbetKipi.UZMAN || ayarlar.sohbetArastirma)

                deltaSayisi = if (arastirmali) {
                    try {
                        arastir(asistanId, ayarlar, istemci, kip)
                    } catch (e: LlmError.BadRequest) {
                        // Bu cevapta bir araç zaten çalıştıysa sunucu araçları destekliyor;
                        // 400 başka bir şey (bozuk argüman vb.) — gizlenmez (gözden geçirme bulgusu 5).
                        val aracCalisti = _state.value.messages.firstOrNull { it.id == asistanId }
                            ?.adimlar?.isNotEmpty() == true
                        if (aracCalisti || e.body?.contains("tool", ignoreCase = true) != true) throw e
                        // Sunucu araç çağırmayı desteklemiyor: sohbet bozulmasın (spec 010).
                        araclarDesteklenmiyor = true
                        arastirmali = false
                        LOG.info("Sunucu araçları reddetti; oturum araçsız sürüyor.")
                        guncelle(asistanId) {
                            it.copy(text = "", reasoning = "", adimlar = listOf(AcpBundle.message("chat.research.unsupported")))
                        }
                        duzCevap(asistanId, ayarlar, istemci)
                    }
                } else {
                    duzCevap(asistanId, ayarlar, istemci)
                }
            } catch (e: CancellationException) {
                // Yarım araştırma cevabı tamamlanmış gibi görünmesin (spec 010 H5).
                if (arastirmali) guncelle(asistanId) { it.copy(text = it.text.trimEnd() + "\n\n— durduruldu") }
                throw e // iptal hata değildir; finally yine de çalışır
            } catch (e: Throwable) {
                hata = e
            } finally {
                val sure = System.currentTimeMillis() - baslangicAni
                if (hata != null) {
                    LOG.warn("İstek hata ile bitti (${sure}ms, $deltaSayisi delta)", hata)
                } else {
                    LOG.info("İstek bitti: ${sure}ms, $deltaSayisi delta")
                }
                // Yerine yeni bir istek geçtiyse paylaşılan durum (akış, hata, bekleyen
                // eylem) yenisinin; burada yalnızca kendi mesajı kapatılır (bulgu 8).
                if (akan === coroutineContext[Job]) {
                    tamamla(asistanId, hata)
                } else {
                    guncelle(asistanId) { it.copy(streaming = false) }
                }
            }
        }
        akan = is_
        is_.start()
    }

    /** Araçsız tek istek — 002'den beri değişmeyen yol (spec 010 T41). */
    private suspend fun duzCevap(asistanId: MessageId, ayarlar: AcpSettings.State, istemci: LlmClient): Int {
        var sayi = 0
        istemci.streamChat(istekKur(ayarlar, asistanId))
            // Token başına yeniden çizim paneli kilitler (H1: "arayüz donmaz").
            .coalesceText()
            .collect { delta ->
                sayi++
                uygula(asistanId, delta)
            }
        return sayi
    }

    /**
     * Kodu araştırarak cevap (spec 010): salt okunur araçlarla döngü.
     *
     * Düzenleme aracı hiç verilmez VE yürütücü onu reddeder — salt okunurluk
     * yönergeye değil yeteneğe dayanır.
     */
    private suspend fun arastir(
        asistanId: MessageId,
        ayarlar: AcpSettings.State,
        istemci: LlmClient,
        kip: SohbetKipi,
    ): Int {
        val mesajlar = gecmis(asistanId)
        val gorev = mesajlar.lastOrNull()?.takeIf { it.role == ChatMessage.Role.USER } ?: return 0
        val uzman = kip == SohbetKipi.UZMAN
        val dongu = AjanDongusu(
            client = istemci,
            yurutucu = IdeAracYurutucu(project, DuzenlemePlani(), duzenlemeAcik = false),
            araclar = AracSetleri.OKUMA,
            model = ayarlar.model.orEmpty(),
            maxAdim = if (uzman) ayarlar.uzmanMaxAdim else ayarlar.sohbetMaxAdim,
            yonerge = PromptSelector.yonerge(if (uzman) "uzman" else "sohbet-arastirma"),
            akisli = true,
            istekHazirla = { istekAyarla(it, ayarlar) },
        )
        // Önceki sorular bağlamsız gider: her biri açık dosyayı taşıyordu ve birkaç
        // takip sorusunda bağlam sınırı aşılıyordu (gözden geçirme bulgusu 7).
        // Güncel soru kendi taze bağlamıyla gider.
        val onceki = _state.value.messages
            .filter { it.id != asistanId && it.text.isNotBlank() }
            .dropLast(1)
            .map {
                if (it.role == ChatRole.USER) {
                    ChatMessage(ChatMessage.Role.USER, it.talimat ?: it.text)
                } else {
                    ChatMessage(ChatMessage.Role.ASSISTANT, it.text)
                }
            }
        var sayi = 0
        // Teşhis için içeriksiz günlük (T90): araç adı ve süresi, bitiş nedeni — argüman/kod yazılmaz.
        var sonAn = System.currentTimeMillis()
        var sonArac: String? = null
        LOG.info("Araştırma başlıyor: kip=$kip, önceki ${onceki.size} mesaj")
        dongu.calistir(gorev.content, onceki).collect { olay ->
            sayi++
            if (olay is AjanOlayi.AracCagirdi || olay is AjanOlayi.Bitti) {
                val simdi = System.currentTimeMillis()
                LOG.info("Araştırma: ${sonArac ?: "model"} ${simdi - sonAn}ms")
                sonAn = simdi
                sonArac = (olay as? AjanOlayi.AracCagirdi)?.ad
            }
            if (olay is AjanOlayi.Bitti) LOG.info("Araştırma bitti: ${olay.neden}")
            when (olay) {
                is AjanOlayi.MetinParcasi -> uygula(asistanId, ChatDelta.Text(olay.metin))
                is AjanOlayi.DusunceParcasi -> uygula(asistanId, ChatDelta.Reasoning(olay.metin))
                // Araç çağıran turun metni cevap değildi; düşünceye taşınır.
                AjanOlayi.MetinGeriAl -> guncelle(asistanId) {
                    it.copy(reasoning = it.reasoning + it.text + "\n", text = "")
                }
                is AjanOlayi.AracCagirdi -> guncelle(asistanId) {
                    it.copy(adimlar = it.adimlar + "${olay.ad}: ${olay.ozet}")
                }
                is AjanOlayi.Bitti -> if (olay.neden != BitisNedeni.TAMAMLANDI) {
                    val not = AcpBundle.message(
                        if (olay.neden == BitisNedeni.ADIM_SINIRI) "chat.research.stepLimit" else "chat.research.repeat",
                    )
                    guncelle(asistanId) { it.copy(text = (it.text.trimEnd() + "\n\n" + not).trim()) }
                }
                is AjanOlayi.Dusunuyor -> Unit // akışlı kipte gelmez
            }
        }
        return sayi
    }

    private fun istekKur(ayarlar: AcpSettings.State, asistanId: MessageId): ChatRequest =
        istekAyarla(ChatRequest(model = ayarlar.model.orEmpty(), messages = gecmis(asistanId)), ayarlar)

    private fun istekAyarla(istek: ChatRequest, ayarlar: AcpSettings.State): ChatRequest =
        istek.copy(
            temperature = ayarlar.temperature,
            maxTokens = ayarlar.maxTokens,
            extraBody = if (ayarlar.enableThinking) {
                emptyMap()
            } else {
                mapOf("chat_template_kwargs" to mapOf("enable_thinking" to false))
            },
        )

    /**
     * Açık dosya ve seçimi toplayıp kullanıcı mesajına ekler.
     *
     * Kullanıcı panelde yalnızca kendi sorusunu görür; modele giden gövde
     * bağlamla zenginleştirilir. Rozetler kullanıcıya **ne paylaştığını** gösterir
     * (spec 002 H3).
     */
    private suspend fun baglamiEkle(kullaniciId: MessageId, tokenButcesi: Int) {
        val mesaj = _state.value.messages.firstOrNull { it.id == kullaniciId } ?: return
        val soru = mesaj.talimat ?: mesaj.text
        val ogeler = runCatching {
            CompositeContextProvider.collect(ContextRequest(project, tokenButcesi))
        }.getOrElse { hata ->
            // Bağlam toplanamadıysa soru yine de gitmeli — bağlam bir kolaylık,
            // zorunluluk değil. AMA sessizce yutulmaz: bu yakalayıcı bir keresinde
            // uzantı noktası adı yanlış olduğu için bağlamın hiç toplanmadığını
            // gizledi ve hatayı kullanıcı buldu.
            LOG.warn("Bağlam toplanamadı; soru bağlamsız gönderiliyor.", hata)
            emptyList()
        }
        // Bağlam boşsa bile talimat gövde olarak kullanılmalı: eylemlerde
        // görünen ad ("Seçili kodu açıkla") modele gitmemeli.
        guncelle(kullaniciId) { m ->
            m.copy(
                gonderilenMetin = PromptBuilder.build(soru, ogeler),
                context = ogeler.map { ContextRozeti(it.displayName, it.kind.name) },
            )
        }
    }

    /** Akmakta olan asistan mesajı hariç tüm konuşma — model önceki turları görsün. */
    private fun gecmis(asistanId: MessageId): List<ChatMessage> =
        _state.value.messages
            .filter { it.id != asistanId && it.text.isNotBlank() }
            .map {
                ChatMessage(
                    role = if (it.role == ChatRole.USER) {
                        ChatMessage.Role.USER
                    } else {
                        ChatMessage.Role.ASSISTANT
                    },
                    content = it.gonderilenMetin ?: it.text,
                )
            }

    private fun uygula(asistanId: MessageId, delta: ChatDelta) {
        when (delta) {
            is ChatDelta.Text -> guncelle(asistanId) { it.copy(text = it.text + delta.text) }
            is ChatDelta.Reasoning ->
                guncelle(asistanId) { it.copy(reasoning = it.reasoning + delta.text) }
            is ChatDelta.Finish -> Unit // akışın bitişi finally'de işleniyor
            is ChatDelta.ToolCall -> {
                // Sohbet hiç araç göndermiyor (ChatRequest.tools boş), dolayısıyla
                // buraya araç çağrısı gelmemeli. Gelirse sunucu istemediğimiz bir
                // şey döndürüyor demektir — sessizce yutmak yerine günlüğe yazılır.
                // İçerik yazılmaz, yalnızca aracın adı (kullanıcının kodu geçmez).
                LOG.warn("Sohbette beklenmeyen araç çağrısı: ${delta.cagri.ad}")
            }
        }
    }

    private fun tamamla(asistanId: MessageId, hata: Throwable?) {
        _state.update { durum ->
            durum.copy(
                messages = durum.messages.map {
                    if (it.id == asistanId) it.copy(streaming = false) else it
                },
                streaming = false,
                // Kısmi metin silinmez; kullanıcı gelen kadarını görmeye devam eder.
                error = hata?.let {
                    ChatError(
                        mesaj = (it as? LlmError)?.userMessage
                            ?: AcpBundle.message("chat.error.generic"),
                        yenidenDenenebilir = true,
                    )
                },
            )
        }
        // Durum kapatıldıktan SONRA değerlendirilir: burada bir hata çıkarsa
        // akış "bitti" olarak işaretlenmiş kalsın, panel takılı görünmesin.
        if (hata == null) sonucuDegerlendir(asistanId)
    }

    /**
     * Yanıt tamamlandığında ne yapılacağına karar verir.
     *
     * - Kodu değiştiren eylemlerde: kod bloğu varsa uygulama önerilir.
     * - "Test yaz"da: sonuç geçici dosyada açılır, mevcut dosyaya dokunulmaz.
     */
    private fun sonucuDegerlendir(asistanId: MessageId) {
        val tur = bekleyenTur ?: return
        val yanit = _state.value.messages.firstOrNull { it.id == asistanId }?.text.orEmpty()
        val kod = ResultApplier.kodBlogunuAyikla(yanit)

        when {
            tur == AksiyonTuru.TEST_YAZ && kod != null -> {
                val uzanti = bekleyenUzanti
                bekleyenTur = null
                // Geçici dosya oluşturmak ve açmak EDT gerektirir; burası
                // servis coroutine'i (arka plan).
                scope.launch(Dispatchers.EDT) {
                    runCatching {
                        ScratchFileWriter.yaz(
                            project = project,
                            dosyaAdi = "acp-test.${uzanti ?: "txt"}",
                            uzanti = uzanti,
                            icerik = kod,
                        )
                    }.onFailure { LOG.warn("Test dosyası oluşturulamadı", it) }
                }
            }
            tur.editoruDegistirir && kod != null && bekleyenCapa != null -> {
                _state.update { it.copy(uygulanabilir = UygulanabilirSonuc(asistanId, kod)) }
            }
            else -> {
                // Kod bloğu yoksa uygulama hiç önerilmez (spec 003 H2).
                temizleBekleyen()
                bekleyenTur = null
            }
        }
    }

    private fun guncelle(id: MessageId, donustur: (ChatMessageUi) -> ChatMessageUi) {
        _state.update { durum ->
            durum.copy(messages = durum.messages.map { if (it.id == id) donustur(it) else it })
        }
    }

    companion object {
        private val LOG = logger<ChatSessionService>()

        fun getInstance(project: Project): ChatSessionService = project.service()
    }
}
