# Plan: Uzman Kod Anlama

- **Spec no:** 010 — [spec.md](spec.md)
- **Tarih:** 2026-10-22
- **Durum:** Onaylandı — *2026-10-22*

---

## Yaklaşım

Dört parça, aralarındaki sınır bilinçli:

1. **İlişki araçları (IDE)** — `kullanimlari_bul`, `uygulamalari_bul`,
   `tanima_git`. IDE'nin anlam dizinini kullanır. İsteğe bağlı modülde
   (`com.intellij.modules.lang`), 006'daki tamamlamayla aynı kalıp.
2. **Döngü (saf)** — 008'in `AjanDongusu` yeniden kullanılır; yalnızca son
   cevabı **akışlı** verebilmesi için bir seçenek eklenir. 008 davranışı değişmez.
3. **Sohbet** — `ChatSessionService` tek istek yerine döngüyü çalıştırır;
   kipe göre araç seti, yönerge ve adım sınırı değişir.
4. **Arayüz** — üç seçenekli kip seçici; cevabın üstünde araştırma adımları.

```text
Kip       Araçlar                                          Yönerge           Adım
Sohbet    dosya_ara, dosya_oku, kullanimlari_bul,          sohbet-arastirma  6
          uygulamalari_bul, tanima_git
Uzman     (aynı — salt okunur)                             uzman             15
Çok dos.  yukarıdakiler + dosya_duzenle                    AjanYonergesi     20 (mevcut)
```

**Salt okunurluk bir yetenek sınırıdır:** `dosya_duzenle` Sohbet ve Uzman'a hiç
verilmez, yürütücü de o iki kipte bu adı tanımaz. Yönergeye güvenilmez.

## Değerlendirilen alternatifler

### Sembol modele nasıl verilir

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| `yol` + `ad` (+ isteğe bağlı `satir`) | Model zaten yol ve ad görüyor (arama/okuma çıktısı); belirsizlikte adaylar listelenir | Aynı dosyada aynı adla iki şey olabilir | **Seçildi** — `satir` ayırır |
| Tam nitelikli ad (`paket.Sinif.metot`) | Kesin | Dile özgü; JS/Python'da anlamı farklı → Altın Kural 3 | Elendi |
| Dosya içi ofset | Kesin | Model ofset hesaplayamaz | Elendi |

### Son cevap nasıl akar

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| `AjanDongusu`'na `akisli` seçeneği | Tek döngü; sınırlar, tekrar koruması, kırpma ortak | Döngüye yeni olay tipi | **Seçildi**; varsayılan `false` → 008 testleri değişmez |
| Sohbet için ayrı döngü | Bağımsız | Aynı mantığın iki kopyası; düzeltme birinde unutulur | Elendi |
| Akışsız (cevap tümüyle gelince göster) | Basit | Uzman cevabı uzun; 15 adım + uzun metin beklemek | Elendi |

Araç çağıran turlardaki ara metin ("önce kullanımları arayayım…") cevaba
karışmaz; mevcut `reasoning` alanına gider ve katlanmış görünür.

### Üst tanım (polimorfizm)

Platformda dilden bağımsız bir "üst metoda git" var mı, **T00'da** denenir
(aday: dile göre kaydedilen "Go to Super" işleyicisi). Programatik ve dilden
bağımsız kullanılamıyorsa: araç yok, yönerge modelin `uygulamalari_bul` +
`kullanimlari_bul`'u birleştirmesini ister ve cevapta sınırı söyletir (spec kararı).

---

## Arayüzler

### İlişki araçları (`dev.acp.anlama` — isteğe bağlı modül)

```kotlin
/** null döner: modül yüklü değil (dil desteği yok) → metin aramasına düşülür. */
interface IliskiArayici {
    /** Dizinleme sürüyor mu. Sürüyorsa araçlar çağrılmaz, söylenir. */
    fun hazirMi(): Boolean

    suspend fun kullanimlar(s: SembolIstegi, max: Int): IliskiSonucu
    suspend fun uygulamalar(s: SembolIstegi, max: Int): IliskiSonucu
    suspend fun tanim(yol: String, satir: Int, ad: String): IliskiSonucu

    companion object {
        fun getInstance(project: Project): IliskiArayici? = project.serviceOrNull()
    }
}

data class SembolIstegi(val yol: String, val ad: String, val satir: Int? = null)

sealed interface IliskiSonucu {
    /** konum: "src/A.java:12", kapsayan: "Kullanici.yaz", satirMetni: kırpılmış satır */
    data class Bulundu(val yerler: List<Yer>, val toplam: Int) : IliskiSonucu
    data class Belirsiz(val adaylar: List<Yer>) : IliskiSonucu
    data class Yok(val sebep: String) : IliskiSonucu
}
data class Yer(val konum: String, val kapsayan: String?, val satirMetni: String)
```

Uygulama (`PsiIliskiArayici`): `smartReadAction` içinde
`PsiTreeUtil` ile dosyadaki `PsiNamedElement`'ler → ad (ve satır) eşleşmesi →
`ReferencesSearch` / `DefinitionsScopedSearch` (proje kapsamı, `max`'ta durur) →
her referans için kapsayan adlı eleman. `tanim`: verilen satırdaki adın
referansı çözülür (`findReferenceAt(...).resolve()`). Hepsi `platform.lang`'da;
denemede (2026-10-22) üç dilde doğrulandı.

`acp-anlama.xml` (`<depends optional="true">com.intellij.modules.lang`):
`projectService` olarak `PsiIliskiArayici` kaydı.

### Yürütücü

`IdeAracYurutucu`'ya üç araç ve bir **kip** eklenir:

```kotlin
class IdeAracYurutucu(
    ...,
    private val duzenlemeAcik: Boolean = true,   // Sohbet/Uzman: false
)
```

`duzenlemeAcik == false` iken `dosya_duzenle` → `HATA: bu kipte dosya
değiştirilemez`. İlişki aracı `IliskiArayici` yoksa veya hazır değilse →
`HATA: ...; dosya_ara kullan` (model metin aramasına geçer; spec H2).

Araç listeleri `AjanService.ARACLAR`'dan ayrı bir yere taşınır
(`AracSetleri.OKUMA`, `AracSetleri.DUZENLEME`) ki sohbet ve ajan aynı tanımları
kullansın.

### Döngü

```kotlin
class AjanDongusu(..., private val akisli: Boolean = false)

sealed interface AjanOlayi {
    // mevcutlar değişmiyor
    /** Yalnızca akisli=true iken, araç çağırmayan (son) turda. */
    data class MetinParcasi(val metin: String) : AjanOlayi
}
```

Bir turun araç çağırıp çağırmayacağı akış bitmeden bilinmez. Akışlı kipte
metin parçaları **hemen** `MetinParcasi` olarak akar; aynı turda araç çağrısı
gelirse döngü `MetinGeriAl` yayar ve servis o turun metnini cevaptan
`reasoning`'e taşır (T00 kararı — sunucunun parça sırasına bağımlı değil).

### Sohbet servisi

```kotlin
enum class SohbetKipi { SOHBET, UZMAN }
fun send(soru: String, kip: SohbetKipi = SohbetKipi.SOHBET)
```

- Araştırma kapalıysa (`sohbetArastirma == false`) → bugünkü yol, birebir.
- Açıksa → `AjanDongusu(akisli = true)`; sistem mesajı kipe göre
  `prompts/sohbet-arastirma.md` veya `prompts/uzman.md` (pakette, 003'teki
  şablon felsefesi). Geçmiş: önceki **son cevaplar** gider; ara araç mesajları
  sonraki soruya taşınmaz (bağlam bütçesi).
- `AracCagirdi` → `ChatMessageUi.adimlar`; `MetinParcasi` → metin;
  `Dusunuyor` → `reasoning`; `Bitti(ADIM_SINIRI/TEKRAR)` → cevabın sonuna
  açık bir not.
- **Durdurma** (mevcut `cancel`): araştırma veya akış yarıda kesilirse mesajın
  sonuna "— durduruldu" eklenir; yarım cevap tamamlanmış gibi görünmez (spec H5).
- **Sembol bulunamazsa** `Yok.sebep` dosyadaki en yakın adları (en fazla 5) önerir.
- **Sunucu araçları reddederse** (400, gövdede `tool`) → aynı soru araçsız
  yeniden gönderilir, oturum boyunca araçsız kalınır, bir kez bildirilir.

### Seçim eylemi

`ACIKLA` → `send(..., UZMAN)`. Talimat, seçimin **proje köküne göreli yolunu**
ve satır aralığını içerir (araçların dosyayı bulabilmesi için; bugünkü bağlam
yalnızca dosya adını taşıyor).

### Ayarlar (`AcpSettings.State`)

```kotlin
var sohbetArastirma by property(true)
var sohbetMaxAdim by property(6)
var uzmanMaxAdim by property(15)
```

### Arayüz

- Kip seçici: `Sohbet | Uzman | Çok dosyalı`; `ChatViewAction.Gonder(metin, kip)`.
- `ChatMessageUi.adimlar: List<String>` → asistan mesajının üstünde soluk, tek
  satırlık özet ("3 adım: kullanımları buldu · …"), tıklanınca açılır.
- Uzman'da ipucu metni: "Bir metodu veya kodu sorun — kullanımları ve ilişkileri
  araştırılıp anlatılır".

---

## Riskler

| Risk | Karşılık |
|---|---|
| **Test ortamında dil eklentisi yok** (deneme bulgusu) ve eklemek tuzak #2'ye çarpıyor | İlişki araçlarının biçimlendirme/sınır mantığı saf testle; gerçek PSI davranışı `runIde`'de **iç eylemle** (`internal="true"`, yalnızca sandbox'ta görünür) doğrulanır. AGENTS.md'ye yazılır |
| Sunucu araç çağırmayı desteklemiyor | Araçsız yeniden deneme (yukarıda) |
| Uzman uydurur ("mimari şudur") | Yönerge: her iddia için konum; çıkarımlar "çıkarım" diye işaretlenir; ölçüm (T90) |
| Büyük projede kullanım araması yavaş | `max`'ta durur; dizin araması iptal edilebilir (`ProgressManager` iptali coroutine'e bağlı) |
| 3 seçenek 280 px'e sığmıyor | Yerleşim testi (`KipSeciciTest` genişler); sığmazsa etiket kısaltılır |
| Sohbet gecikmesi artar | Araç gerekmeyen soruda tek tur, ek gecikme yok; araştırma ayardan kapatılabilir |

## Test stratejisi

- **T00 (deneme, atılacak):** üst tanım yolu; gerçek sunucuda araç çağrısı ve
  metin sırası. Sonuç plana yazılır.
- **Saf:** akışlı döngü (tamponlama, araçlı turda metin sızmıyor, son turda
  akıyor, varsayılan kipte 008 olayları birebir); yönerge metinleri sabitlenir.
- **Yürütücü:** salt okunur kipte `dosya_duzenle` reddedilir (mutasyonla);
  `IliskiArayici` yok/hazır değil → metin aramasına yönlendirme metni.
- **Sohbet servisi:** araştırma kapalıyken istek gövdesi bugünküyle birebir
  (T22 kalıbı); 400 + araç hatası → araçsız tekrar; adımlar mesaja yazılıyor.
- **İlişki biçimlendirme (saf):** `Yer` listesi → model metni; `max` kırpma +
  toplam; belirsiz adayların listelenmesi.
- **Arayüz:** 280 px'te üç seçici; adım satırı.
- **Gerileme kapısı:** mevcut 478 test değiştirilmeden geçer.
- **Gerçek IDE (iç eylem):** Java/JS/Kotlin örneklerinde kullanımlar, uygulamalar,
  tanım; aynı adlı metot ayrımı (deneme senaryosu kalıcı hâle gelir).
- **Ölçüm (T90):** gerçek bir kod tabanında 5 "açıkla" sorusu; her cevapta
  konumların doğruluğu elle kontrol, uydurma iddia sayısı.
