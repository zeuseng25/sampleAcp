package dev.acp.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.acp.anlama.IliskiArayici
import dev.acp.komut.CiktiKirpici
import dev.acp.komut.KomutSonucu
import dev.acp.llm.AracCagrisi
import dev.acp.llm.agent.AracSetleri
import dev.acp.llm.agent.AracYurutucu
import dev.acp.llm.agent.Duzenleme
import dev.acp.llm.agent.DuzenlemePlani
import dev.acp.llm.agent.IliskiBicimi
import dev.acp.llm.agent.IliskiSonucu
import dev.acp.llm.agent.SembolIstegi

/**
 * Ajanın araçlarının IDE tarafı (spec 008).
 *
 * Araçlar: arama, okuma, düzenleme toplama (008) ve IDE'nin anlam dizininden
 * ilişkiler (010). **Dosya oluşturma, silme ve komut çalıştırma yok.**
 *
 * **Arama neden `ProjectFileIndex` ile:** IDE'nin "Find in Path" makinesi
 * (`FindInProjectUtil`) impl paketinde ve dahili API sayılıyor. Dosyaları
 * kendimiz gezip düz metin eşleştirmek daha yavaş ama dilden bağımsız
 * (Altın Kural 3), dahili API kullanmıyor ve dört IDE'de de aynı çalışıyor.
 */
internal class IdeAracYurutucu(
    private val project: Project,
    val plan: DuzenlemePlani,
    /** Bağlamı doldurmamak için (spec 008 T41). */
    private val maxSonuc: Int = 40,
    /** Üretilmiş veya küçültülmüş dosyalar aramayı boğmasın. */
    private val maxDosyaBoyutu: Long = 300_000,
    private val maxDosya: Int = 20,
    /** Sohbet ve Uzman: false — salt okunurluk yetenek sınırıdır, yönerge değil (spec 010). */
    private val duzenlemeAcik: Boolean = true,
    private val iliski: IliskiArayici? = IliskiArayici.getInstance(project),
    /** Doğrulama komutu (spec 009); null = tanımsız. Model bunu seçemez, değiştiremez. */
    private val komut: List<String>? = null,
    private val onay: OnayKapisi? = null,
    private val calistirici: Calistirici? = null,
    /** Komutun başlayacağı kök; yerel diskte değilse null (komut çalışmaz). */
    private val komutKoku: () -> java.nio.file.Path? = { varsayilanKok(project) },
) : AracYurutucu {

    private val mapper = ObjectMapper()

    /** Atlanan düzenlemeler kullanıcıya bildirilecek (spec 008 T51). */
    val atlananlar = mutableListOf<String>()

    override suspend fun calistir(cagri: AracCagrisi): String {
        val a = try {
            mapper.readTree(cagri.argumanlarJson)
        } catch (e: Exception) {
            // Modelin düzeltebileceği bir durum, protokol hatası değil.
            return "HATA: argümanlar geçerli JSON değil: ${e.message}"
        }

        return when (cagri.ad) {
            "dosya_ara" -> ara(a.path("desen").asText(""))
            "dosya_oku" -> oku(a.path("yol").asText(""))
            AracSetleri.DUZENLE ->
                if (!duzenlemeAcik) {
                    "HATA: bu kipte dosya değiştirilemez. Yalnızca okuyup anlatabilirsin."
                } else {
                    duzenle(a.path("yol").asText(""), a.path("eski").asText(""), a.path("yeni").asText(""))
                }
            "kullanimlari_bul" -> sembol(a) { s -> iliski!!.kullanimlar(s, maxSonuc) }
            "uygulamalari_bul" -> sembol(a) { s -> iliski!!.uygulamalar(s, maxSonuc) }
            "tanima_git" -> {
                val satir = a.path("satir").asInt(0)
                if (satir <= 0) {
                    "HATA: 'satir' (1'den başlayan satır numarası) gerekli"
                } else {
                    sembol(a) { s -> iliski!!.tanim(s.yol, satir, s.ad) }
                }
            }
            AracSetleri.DOGRULA -> dogrula()
            else -> "HATA: bilinmeyen araç: ${cagri.ad}"
        }
    }

    /**
     * Doğrulama komutu (spec 009): argüman almaz, ayarlı komutu **onayla** çalıştırır.
     *
     * Sıra önemli: tanımsız ve reddedilen komut kullanıcıya hiç sorulmaz;
     * "Atla"da çalıştırıcı hiç çağrılmaz.
     */
    private suspend fun dogrula(): String {
        if (!duzenlemeAcik) return "HATA: bu kipte komut çalıştırılamaz."
        val k = komut ?: run {
            // Kullanıcıya da söylenir, yalnızca modele değil (spec 009 H4, bulgu 4).
            onay?.bitti(KomutSonucu.Tanimsiz)
            return "HATA: doğrulama komutu tanımlı değil. Kullanıcıdan hatayı yapıştırmasını iste."
        }
        val kok = komutKoku() ?: return "HATA: proje kökü yerel diskte bulunamadı; komut çalıştırılamaz."
        dev.acp.komut.KomutDenetimi.denetle(k, kok)?.let { return "HATA: komut reddedildi: $it" }
        val kapi = onay ?: return "HATA: onay alınamıyor; komut çalıştırılmadı."
        if (!kapi.iste(k)) {
            kapi.bitti(KomutSonucu.Atlandi)
            return "Kullanıcı komutu çalıştırmayı reddetti. Elindeki bilgiyle devam et."
        }
        val c = calistirici ?: return "HATA: komut çalıştırılamıyor."
        val sonuc = try {
            c.calistir(k, kok)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            kapi.bitti(KomutSonucu.Durduruldu) // satır "çalışıyor"da kalmasın (bulgu 2)
            throw e
        }
        kapi.bitti(sonuc)
        return when (sonuc) {
            is KomutSonucu.Basarili -> "BAŞARILI\n" + CiktiKirpici.kirp(goreli(sonuc.cikti, kok))
            is KomutSonucu.Basarisiz ->
                "BAŞARISIZ (çıkış ${sonuc.cikisKodu})\n" + CiktiKirpici.kirp(goreli(sonuc.cikti, kok))
            is KomutSonucu.ZamanAsimi -> "HATA: zaman aşımı (${sonuc.sureMs / 1000} sn); hata çıktısı yok."
            is KomutSonucu.Bulunamadi -> "HATA: '${sonuc.program}' bulunamadı. Bu bir kod hatası değil."
            is KomutSonucu.Reddedildi -> "HATA: komut reddedildi: ${sonuc.sebep}"
            KomutSonucu.Atlandi -> "Kullanıcı komutu çalıştırmayı reddetti. Elindeki bilgiyle devam et."
            KomutSonucu.Durduruldu -> "HATA: durduruldu."
            is KomutSonucu.Baslatilamadi -> "HATA: '${sonuc.program}' başlatılamadı: ${sonuc.sebep}"
            KomutSonucu.Tanimsiz -> "HATA: doğrulama komutu tanımlı değil."
        }
    }

    /**
     * Derleyiciler mutlak yol basar (`/proje/src/A.java`, `file:///proje/...`); dosya
     * araçları köke göreli yol ister ve model kökü bilmez (bulgu 5).
     */
    private fun goreli(cikti: String, kok: java.nio.file.Path): String {
        val k = kok.toString().trimEnd('/', '\\')
        val kUnix = k.replace('\\', '/')
        return cikti
            .replace("file://$kUnix/", "").replace("file:///$kUnix/", "")
            .replace("$k${java.io.File.separator}", "").replace("$kUnix/", "")
    }

    /** İlişki araçlarının ortak girişi: argüman, modül ve dizin kontrolü (spec 010 H2). */
    private suspend fun sembol(
        a: com.fasterxml.jackson.databind.JsonNode,
        ara: suspend (SembolIstegi) -> IliskiSonucu,
    ): String {
        val yol = a.path("yol").asText("")
        val ad = a.path("ad").asText("")
        if (yol.isBlank() || ad.isBlank()) return "HATA: 'yol' ve 'ad' gerekli"
        if (yol.split('/', '\\').any { it == ".." }) return "HATA: proje dışına çıkan yol"
        val i = iliski
            ?: return "HATA: bu IDE'de ilişki araması yok. Bunun yerine dosya_ara kullan " +
                "ve cevapta metin aramasıyla bulunduğunu belirt."
        if (!i.hazirMi()) {
            return "HATA: IDE dizinleme yapıyor, ilişki araması şu an yapılamıyor. Bunun yerine " +
                "dosya_ara kullan ve cevapta metin aramasıyla bulunduğunu belirt."
        }
        val satir = a.path("satir").asInt(0).takeIf { it > 0 }
        return IliskiBicimi.metin(ara(SembolIstegi(yol, ad, satir)))
    }

    // ---- araçlar ----

    private suspend fun ara(desen: String): String {
        if (desen.isBlank()) return "HATA: 'desen' boş olamaz"

        val bulunanlar = readAction {
            val sonuc = mutableListOf<String>()
            var toplam = 0
            ProjectFileIndex.getInstance(project).iterateContent { dosya ->
                if (uygunMu(dosya)) {
                    val metin = runCatching { metin(dosya) }.getOrNull()
                    if (metin != null && desen in metin) {
                        metin.lineSequence().forEachIndexed { i, satir ->
                            if (desen in satir) {
                                toplam++
                                if (sonuc.size < maxSonuc) {
                                    sonuc += "${ProjeYollari.goreli(project, dosya)}:${i + 1}:$satir"
                                }
                            }
                        }
                    }
                }
                // Sonuç sınırının çok üstüne çıkınca taramayı sürdürmenin anlamı yok.
                toplam < maxSonuc * 10
            }
            sonuc to toplam
        }

        val (satirlar, toplam) = bulunanlar
        if (satirlar.isEmpty()) return "sonuç yok"
        val fazla = toplam - satirlar.size
        return satirlar.joinToString("\n") + if (fazla > 0) "\n... ($fazla sonuç daha)" else ""
    }

    private suspend fun oku(yol: String): String {
        if (yol.isBlank()) return "HATA: 'yol' boş olamaz"
        val metin = readAction {
            val dosya = ProjeYollari.bul(project, yol) ?: return@readAction null
            runCatching { metin(dosya) }.getOrNull()
        } ?: return "HATA: bulunamadı veya okunamadı: $yol"
        return if (metin.length > OKUMA_SINIRI) {
            metin.take(OKUMA_SINIRI) + "\n... (kısaltıldı)"
        } else {
            metin
        }
    }

    /**
     * **Diske YAZMAZ.** Yalnızca plana ekler.
     *
     * Spec'in "ya tamamen ya hiç" kuralı buna dayanıyor: döngü ortasında iptal
     * edilirse hiçbir dosya değişmiş olmamalı. Bu kuralın ihlali **sessizce**
     * olur — kod çalışır, testler geçer, ama iptal edilen bir iş dosyaları
     * yarım bırakır.
     */
    private suspend fun duzenle(yol: String, eski: String, yeni: String): String {
        if (yol.isBlank() || eski.isBlank()) return "HATA: 'yol' ve 'eski' boş olamaz"
        if (eski == yeni) return "HATA: 'eski' ve 'yeni' aynı; değişiklik yok"

        val metin = readAction {
            val dosya = ProjeYollari.bul(project, yol) ?: return@readAction null
            runCatching { metin(dosya) }.getOrNull()
        } ?: return "HATA: bulunamadı veya okunamadı: $yol"

        if (eski !in metin) {
            return "HATA: 'eski' metin dosyada birebir bulunamadı. " +
                "Satırın tamamını (baştaki boşluklar dahil) kullan."
        }
        if (metin.indexOf(eski) != metin.lastIndexOf(eski)) {
            return "HATA: 'eski' metin dosyada birden fazla yerde geçiyor. " +
                "Hangi yeri kastettiğini belirtmek için daha fazla satır ekle."
        }

        if (plan.dosyaSayisi >= maxDosya && yol !in plan.dosyaBazinda()) {
            return "HATA: dosya sınırına ulaşıldı ($maxDosya). Daha fazla dosya düzenlenemez."
        }

        return if (plan.ekle(Duzenleme(yol, eski, yeni))) "kaydedildi" else "zaten kayıtlı"
    }

    // ---- yardımcılar ----

    /**
     * Editörde açık ve kaydedilmemişse belgedeki metin, değilse diskteki.
     *
     * Satır numaraları (seçim konumu, ilişki araması) belgeden geliyor; okuma
     * diskten yapılırsa model "satır 12" diye başka bir kodu okur (spec 010).
     */
    private fun metin(dosya: VirtualFile): String =
        FileDocumentManager.getInstance().getCachedDocument(dosya)?.text ?: VfsUtilCore.loadText(dosya)

    private fun uygunMu(dosya: VirtualFile): Boolean =
        !dosya.isDirectory && !dosya.fileType.isBinary && dosya.length <= maxDosyaBoyutu

    companion object {
        private const val OKUMA_SINIRI = 20_000

        /** İlk içerik kökü, yerel diskteyse. */
        fun varsayilanKok(project: Project): java.nio.file.Path? =
            ProjeYollari.kokler(project).firstOrNull()?.let { runCatching { it.toNioPath() }.getOrNull() }
    }
}
