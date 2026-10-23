package dev.acp.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import dev.acp.agent.IdeAracYurutucu
import dev.acp.agent.KomutCalistirici
import dev.acp.agent.OnayKapisi
import dev.acp.komut.CiktiKirpici
import dev.acp.komut.KomutSonucu
import dev.acp.AcpBundle
import dev.acp.settings.AcpProjeAyarlari
import kotlinx.coroutines.CompletableDeferred
import dev.acp.editor.TopluSonuc
import dev.acp.editor.TopluUygulayici
import dev.acp.llm.agent.AjanDongusu
import dev.acp.llm.agent.AracSetleri
import dev.acp.llm.agent.AjanOlayi
import dev.acp.llm.agent.BitisNedeni
import dev.acp.llm.agent.DuzenlemePlani
import dev.acp.settings.AcpSettings
import dev.acp.settings.AjanKipi
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kullanıcıya gösterilen ajan durumu. */
internal data class AjanDurumu(
    val calisiyor: Boolean = false,
    /** Kullanıcının yazdığı görev; panelde gösterilir. */
    val gorev: String? = null,
    /** Şu ana kadarki adımlar — en yenisi sonda. */
    val adimlar: List<String> = emptyList(),
    val sonuc: TopluSonuc? = null,
    val mesaj: String? = null,
    val hata: String? = null,
    /** Onay bekleyen doğrulama komutu (spec 009 H2); null = beklenen yok. */
    val bekleyenKomut: String? = null,
    /** Kullanıcıya gösterilecek komut çıktısı veya ayar bilgisi (spec 009 Hata Durumları). */
    val komutBilgisi: String? = null,
    /** Tur başladığında sohbetteki mesaj sayısı; görünüm turu oraya çizer. */
    val sohbetSirasi: Int = Int.MAX_VALUE,
)

/**
 * Çok dosyalı düzenleme oturumu (spec 008).
 *
 * Proje seviyesinde: her projenin kendi planı ve kendi çalışan işi var.
 *
 * **Durdurma gerçek iptaldir**, yalnızca arayüzü durdurmak değil: [Job] iptal
 * edilince akış kapanır, uçuştaki HTTP isteği düşer ve döngü sonraki isteği
 * göndermez. Hiçbir dosya değişmemiş olur, çünkü yazma yalnızca [uygula] ile
 * ve döngü bittikten sonra yapılıyor.
 */
@Service(Service.Level.PROJECT)
internal class AjanService(private val project: Project, private val scope: CoroutineScope) : OnayKapisi {

    private val _durum = MutableStateFlow(AjanDurumu())
    val durum: StateFlow<AjanDurumu> = _durum

    private var is_: Job? = null
    private var yurutucu: IdeAracYurutucu? = null

    /** Açık önizleme penceresi; uygulanınca/vazgeçilince/yeni turda kapatılır (bayat kalır). */
    private var onizlemePenceresi: com.intellij.openapi.ui.WindowWrapper? = null

    private fun onizlemeyiKapat() {
        onizlemePenceresi?.close()
        onizlemePenceresi = null
    }

    /** Kullanıcının Çalıştır/Atla kararını bekleyen onay. */
    @Volatile
    private var onayBekleyen: CompletableDeferred<Boolean>? = null

    /** Komut kökü; null = proje kökü. Testte hafif proje bellek içi olduğu için verilir. */
    internal var komutKoku: (() -> java.nio.file.Path?)? = null

    fun baslat(gorev: String) {
        if (_durum.value.calisiyor) return
        onizlemeyiKapat() // önceki turun önizlemesi bayat
        val ayarlar = AcpSettings.getInstance().state
        val adres = ayarlar.baseUrl?.takeIf { it.isNotBlank() }
        if (adres == null || ayarlar.model.isNullOrBlank()) {
            _durum.value = AjanDurumu(gorev = gorev, hata = "Sunucu adresi veya model ayarlanmamış.")
            return
        }

        // Sohbet yalnızca OKUNUR (sıra için); yazılmaz — 008 T73 yalıtımı.
        val sira = ChatSessionService.getInstance(project).state.value.messages.size
        _durum.value = AjanDurumu(calisiyor = true, gorev = gorev, sohbetSirasi = sira)

        is_ = scope.launch {
            try {
                val plan = DuzenlemePlani()
                val koku = komutKoku ?: { IdeAracYurutucu.varsayilanKok(project) }
                val y = IdeAracYurutucu(
                    project, plan, maxDosya = ayarlar.agentMaxDosya,
                    komut = AcpProjeAyarlari.getInstance(project).komut(), onay = this@AjanService,
                    calistirici = KomutCalistirici(
                        ayarlar.agentKomutZamanAsimiMs.toLong(), ekOrtam = projeJavaOrtami(),
                    ),
                    komutKoku = koku,
                )
                yurutucu = y

                val istemci = LlmClientService.getInstance()
                    .createClient(adres, dev.acp.settings.AcpSecrets.getApiKey(adres))

                AjanDongusu(istemci, y, AracSetleri.DUZENLEME, ayarlar.model!!, ayarlar.agentMaxAdim)
                    .calistir(gorev)
                    .collect { olay -> isle(olay) }

                // Döngü bitti; şimdi hesaplama — hâlâ hiçbir dosyaya dokunulmadı.
                sonucuIsle(TopluUygulayici.hesapla(project, plan))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Kullanıcı durdurdu: hata değil, ve hiçbir dosya değişmedi.
                _durum.update {
                    AjanDurumu(gorev = it.gorev, adimlar = it.adimlar, mesaj = "Durduruldu.", sohbetSirasi = it.sohbetSirasi)
                }
                throw e
            } catch (e: Throwable) {
                LOG.warn("Ajan başarısız: ${e.javaClass.simpleName}")
                _durum.update {
                    it.copy(calisiyor = false, hata = (e as? dev.acp.llm.LlmError)?.userMessage
                        ?: "Beklenmeyen bir hata oluştu.")
                }
            }
        }
    }

    /** Hesaplanmış sonucu duruma koyar. Ayrı fonksiyon, çünkü döngü ağ istiyor; bu kısım istemiyor. */
    internal suspend fun sonucuIsle(sonuc: TopluSonuc) {
        LOG.info(
            "Ajan sonucu: ${sonuc.dosyalar.size} dosya, ${sonuc.toplamDegisiklik} değişiklik, " +
                "${sonuc.atlananlar.size} atlandı, kip=${kip()}",
        )
        _durum.update {
            it.copy(
                calisiyor = false,
                // Boş sonuç için önizleme açtırmıyoruz (spec 008 T64):
                // boş bir diff penceresi "bir şey oldu" hissi verir.
                sonuc = sonuc.takeIf { s -> !s.bosMu },
                mesaj = when {
                    !sonuc.bosMu -> it.mesaj
                    sonuc.atlananlar.isNotEmpty() ->
                        "Değişiklik uygulanamadı (${sonuc.atlananlar.size} deneme başarısız)."
                    // Modelin "bulamadım" cevabı varsa onu koru; yoksa açıkça söyle.
                    else -> it.mesaj ?: "Değiştirilecek bir yer bulunamadı."
                },
            )
        }
        // DOGRUDAN: aynı uygulama yolu, araya önizleme girmeden (H4). Yazma EDT ister.
        if (!sonuc.bosMu && kip() == AjanKipi.DOGRUDAN) withContext(Dispatchers.EDT) { uygula() }
    }

    /**
     * Projenin SDK'sı bir Java SDK'sıysa komut onu JAVA_HOME olarak alır.
     *
     * IDE projeyi kendi SDK'sıyla derler; komut ise kabuğun JAVA_HOME'unu
     * devralıyordu. T90'da (graphify) kabuk JDK 17, proje 21 istiyordu: derleme
     * koda hiç ulaşmadı ("release version 21 not supported"), ajan gerçek hatayı
     * görmedi. `JavaSdkType` platform çekirdeğinde (Altın Kural 3).
     */
    private fun projeJavaOrtami(): Map<String, String> =
        com.intellij.openapi.roots.ProjectRootManager.getInstance(project).projectSdk
            ?.takeIf { it.sdkType is com.intellij.openapi.projectRoots.JavaSdkType }
            ?.homePath
            ?.let { mapOf("JAVA_HOME" to it) }
            .orEmpty()

    // ---- doğrulama komutu onayı (spec 009) ----

    override suspend fun iste(komut: List<String>): Boolean {
        val bekleyen = CompletableDeferred<Boolean>()
        onayBekleyen = bekleyen
        val metin = komut.joinToString(" ")
        _durum.update { it.copy(bekleyenKomut = metin) }
        try {
            val calistir = bekleyen.await()
            if (calistir) _durum.update { it.copy(adimlar = it.adimlar + "$CALISIYOR$metin") }
            return calistir
        } finally {
            // Durdur (iptal) dahil: bekleyen onay ekranda kalmaz, geç gelen tık etkisizdir.
            onayBekleyen = null
            _durum.update { it.copy(bekleyenKomut = null) }
        }
    }

    override fun bitti(sonuc: KomutSonucu) {
        // Teşhis için (T90): sonuç türü ve çıkış kodu; çıktı yazılmaz.
        LOG.info("dogrula: ${sonuc.javaClass.simpleName}" + ((sonuc as? KomutSonucu.Basarisiz)?.let { ", çıkış=${it.cikisKodu}" } ?: ""))
        val satir = "dogrula: " + when (sonuc) {
            is KomutSonucu.Basarili -> "başarılı"
            is KomutSonucu.Basarisiz -> "başarısız (çıkış ${sonuc.cikisKodu})"
            is KomutSonucu.ZamanAsimi -> "zaman aşımı"
            is KomutSonucu.Bulunamadi -> "'${sonuc.program}' bulunamadı"
            is KomutSonucu.Reddedildi -> "reddedildi"
            KomutSonucu.Atlandi -> "atlandı"
            KomutSonucu.Durduruldu -> "durduruldu"
            is KomutSonucu.Baslatilamadi -> "başlatılamadı"
            KomutSonucu.Tanimsiz -> "komut tanımlı değil"
        }
        // Kullanıcı da görür: başarısızda çıktının ilgili kısmı, tanımsızda ayar yolu (bulgu 3, 4).
        val bilgi = when (sonuc) {
            is KomutSonucu.Basarisiz -> CiktiKirpici.kirp(sonuc.cikti, KULLANICI_CIKTI_SINIRI)
            KomutSonucu.Tanimsiz -> AcpBundle.message("chat.agent.command.undefined")
            is KomutSonucu.Baslatilamadi -> sonuc.sebep
            else -> null
        }
        // "çalışıyor" satırı sonuçla değişir; atlandıysa yeni satır.
        _durum.update { d ->
            val i = d.adimlar.indexOfLast { it.startsWith(CALISIYOR) }
            d.copy(
                adimlar = if (i >= 0) d.adimlar.toMutableList().also { it[i] = satir } else d.adimlar + satir,
                komutBilgisi = bilgi,
            )
        }
    }

    fun komutuCalistir() { onayBekleyen?.complete(true) }

    fun komutuAtla() { onayBekleyen?.complete(false) }

    fun durdur() {
        is_?.cancel()
        is_ = null
    }

    /**
     * Hesaplanmış sonucu uygular.
     *
     * @return hata metni, veya null.
     *
     * `DOGRUDAN` kipinde de **aynı yol** kullanılıyor; tek fark araya önizleme
     * girip girmemesi. Böylece o kipte de tek geri alma adımı korunuyor.
     */
    fun uygula(): String? {
        val sonuc = _durum.value.sonuc ?: return null
        val hata = TopluUygulayici.uygula(project, sonuc, KOMUT_ADI)
        if (hata == null) onizlemeyiKapat()
        _durum.update { if (hata == null) it.copy(sonuc = null, mesaj = "Uygulandı.") else it.copy(hata = hata) }
        return hata
    }

    fun onizle() {
        onizlemeyiKapat() // ikinci kez basılırsa iki pencere kalmasın
        _durum.value.sonuc?.let { TopluUygulayici.onizle(project, it) { p -> onizlemePenceresi = p } }
    }

    fun kip(): AjanKipi = AcpSettings.getInstance().state.ajanKipi

    fun temizle() {
        onizlemeyiKapat()
        _durum.value = AjanDurumu()
    }

    // ---- iç ----

    private fun isle(olay: AjanOlayi) {
        when (olay) {
            is AjanOlayi.Dusunuyor -> Unit // ara düşünce ekranı doldurur
            // Yalnızca akışlı kipte gelir; ajan akışsız çalışıyor.
            is AjanOlayi.MetinParcasi, is AjanOlayi.DusunceParcasi, AjanOlayi.MetinGeriAl -> Unit
            is AjanOlayi.AracCagirdi -> {
                // Yalnızca aracın ADI: argümanlar kod parçası içerebilir (spec 008 "kod günlüğe yazılmaz").
                LOG.info("Ajan aracı: ${olay.ad}")
                _durum.update { it.copy(adimlar = it.adimlar + "${olay.ad}: ${olay.ozet}") }
            }
            is AjanOlayi.Bitti -> _durum.also {
                LOG.info("Ajan döngüsü bitti: ${olay.neden}, model mesajı ${olay.mesaj?.length ?: 0} karakter")
            }.update {
                it.copy(
                    mesaj = olay.mesaj ?: bitisMetni(olay.neden),
                    // Bitiş nedeni kullanıcıya söylenmeli: yarım kalan bir işi
                    // tamamlanmış sanmak, çok dosyalı değişiklikte pahalıdır.
                    hata = if (olay.neden == BitisNedeni.TAMAMLANDI) null else bitisMetni(olay.neden),
                )
            }
        }
    }

    private fun bitisMetni(neden: BitisNedeni): String = when (neden) {
        BitisNedeni.TAMAMLANDI -> "Tamamlandı."
        BitisNedeni.ADIM_SINIRI -> "Adım sınırına ulaşıldı; iş yarım kalmış olabilir."
        BitisNedeni.TEKRAR -> "Model aynı adımı tekrarladı ve durduruldu; iş yarım kalmış olabilir."
    }

    companion object {
        fun getInstance(project: Project): AjanService = project.service()

        private val LOG = logger<AjanService>()
        private const val KOMUT_ADI = "ACP çok dosyalı düzenleme"
        private const val CALISIYOR = "dogrula: çalışıyor — "
        /** Panel için; modele giden çıktı ayrıca kırpılır. */
        private const val KULLANICI_CIKTI_SINIRI = 3_000
    }
}
