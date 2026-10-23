# Plan: Derleme Hatasını Düzeltme

- **Spec no:** 009 — [spec.md](spec.md)
- **Tarih:** 2026-10-22
- **Durum:** Taslak

---

## Yaklaşım

008'in üç katmanına **tek bir araç** ekleniyor: `dogrula`. Argüman almaz; ayarlı
komutu çalıştırır. Model yalnızca *ne zaman* çalışacağına karar verir, *neyin*
çalışacağına değil.

```
AjanDongusu (saf, değişmiyor)
   └─ AracYurutucu.calistir("dogrula")
        └─ IdeAracYurutucu
             ├─ OnayKapisi.iste(komut)      ← kullanıcı "Çalıştır" / "Atla"
             ├─ KomutDenetimi.denetle(...)  ← proje sınırı, kabuk karakteri
             └─ KomutCalistirici.calistir   ← süreç, zaman aşımı, iptal
                    └─ CiktiKirpici.kirp    ← modele gidecek kısım
```

**Döngü değişmiyor.** Onay beklemek, aracın `suspend` çağrısının uzun sürmesinden
ibaret; iptal (`durdur`) bu beklemeyi de keser. Adım sınırı, tekrar koruması ve
araç hatasının modele bildirilmesi olduğu gibi geçerli.

**Komut diskteki kodla çalışır.** Döngü sırasında biriken düzenlemeler diske
yazılmadığı için komut onları görmez. Bu, kullanıcının bildirdiği hatayı —
zaten uygulanmış kodu — derlemek için doğru davranış. Araç açıklaması ve
yönerge bunu modele söyler.

## Değerlendirilen alternatifler

### Onay nerede beklenir

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Aracın içinde (`suspend`) | Döngü değişmez; iptal kendiliğinden çalışır | Servis, bekleyen onayı durumda tutmalı | **Seçildi** |
| Döngü durur, kullanıcı yeni görev başlatır | Basit | Ajanın bağlamı kaybolur; ikinci tur hatayı baştan arar | Elendi |

### Komut nasıl çalıştırılır

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Doğrudan süreç, argüman listesi | Kabuk yok → `&&`, `\|`, `>`, `$(…)` anlamsız; zincirleme imkânsız | Kullanıcı kabuk sözdizimi yazamaz | **Seçildi** (spec H3) |
| `sh -c "<komut>"` | Kullanıcı her şeyi yazabilir | Proje sınırı denetlenemez; spec H3'ü çiğner | Elendi |
| IDE'nin çalıştırma yapılandırmaları | IDE'ye özgü güzel arayüz | Dile özgü (Maven/Gradle eklentileri) → Altın Kural 3; DataGrip'te yok | Elendi |

Süreç platformun `GeneralCommandLine` + `CapturingProcessHandler`'ı ile
başlatılır (yeni bağımlılık yok, Altın Kural 1). Ortam IDE'nin ortamıdır
(`ParentEnvironmentType.CONSOLE`) — kurum vekil ayarlarını derleme aracı görür.

### Proje bazında ayar

Spec H4 komutun **proje bazında** saklanmasını istiyor; `AcpSettings` uygulama
düzeyinde. Yeni bir proje düzeyi bileşen: `AcpProjeAyarlari`, çalışma alanı
dosyasında (`workspace.xml`) — depoya girmez, her geliştiricinin kendi makinesine
göre kalır.

**Altın Kural 6 genişliyor:** "Ayarlanabilir değerin tek tanım yeri
`AcpSettings.State`, **proje bazında** olanlarınki `AcpProjeAyarlari.State`."
AGENTS.md bu cümleyle güncellenir. Zaman aşımı uygulama düzeyinde kalır.

### Komut tahmini

Proje kökündeki dosyalara bakılır; ilk eşleşen kazanır. Sarmalayıcı (`mvnw`,
`gradlew`) varsa o tercih edilir — projenin istediği sürümü getirir.

| Kökte | Komut (Unix) | Windows |
|---|---|---|
| `mvnw` | `./mvnw -q compile` | `mvnw.cmd -q compile` |
| `pom.xml` | `mvn -q compile` | `mvn.cmd -q compile` |
| `gradlew` | `./gradlew build -q` | `gradlew.bat build -q` |
| `build.gradle(.kts)` | `gradle build -q` | `gradle.bat build -q` |
| `package.json` + `scripts.build` | `npm run build` | `npm.cmd run build` |
| `package.json` + `scripts.test` | `npm test` | `npm.cmd test` |
| hiçbiri | — (tanımsız) | — |

`npm test` izleme kipinde takılabilir; zaman aşımı ve kapatılmış standart girdi
bunu yakalar. Tablo plan aşamasında sabit; genişletmek yeni bir satır ve testtir.

---

## Arayüzler

### Saf (IDE'siz test edilir)

```kotlin
// dev.acp.agent.komut — com.intellij importu YOK (ArchitectureTest kapsamına eklenir)

/** Tahmin; null = tanımsız. */
object KomutTahmini {
    fun tahmin(kokDosyalari: Set<String>, packageJson: String?, windows: Boolean): List<String>?
}

/** Kullanıcının yazdığı komutu argümanlara böler; tırnaklar korunur. */
object KomutSatiri {
    fun ayristir(metin: String): List<String>
}

/** null = geçerli; değilse kullanıcıya gösterilecek sebep. */
object KomutDenetimi {
    fun denetle(argumanlar: List<String>, kok: java.nio.file.Path): String?
}

/** Hata satırları korunur, gerisi sondan kırpılır. */
object CiktiKirpici {
    fun kirp(cikti: String, maxKarakter: Int = 8_000): String
}

sealed interface KomutSonucu {
    data class Basarili(val cikti: String) : KomutSonucu
    data class Basarisiz(val cikisKodu: Int, val cikti: String) : KomutSonucu
    data class ZamanAsimi(val sureMs: Long) : KomutSonucu
    data class Bulunamadi(val program: String) : KomutSonucu
    data class Reddedildi(val sebep: String) : KomutSonucu
    data object Atlandi : KomutSonucu
}
```

`KomutDenetimi` reddeder: boş komut; `&& || ; | > < ` ` `$(` içeren argüman;
`..` bileşeni olan yol; `kok` dışını gösteren mutlak yol.

### IDE tarafı

```kotlin
// dev.acp.agent
interface OnayKapisi {
    /** Kullanıcı karar verene kadar askıda kalır. true = Çalıştır. İptal edilebilir. */
    suspend fun iste(komut: List<String>): Boolean
}

class KomutCalistirici(private val zamanAsimiMs: Long) {
    /** İptal edilirse süreç AĞACI öldürülür. Yakalanan çıktı en fazla 2 MB. */
    suspend fun calistir(argumanlar: List<String>, kok: Path): KomutSonucu
}

// IdeAracYurutucu'ya eklenen bağımlılıklar (varsayılanlı → 008 testleri değişmez)
class IdeAracYurutucu(
    ...,
    private val komut: List<String>? = null,      // null = tanımsız
    private val onay: OnayKapisi? = null,
    private val calistirici: KomutCalistirici? = null,
)

// dev.acp.settings
@Service(Service.Level.PROJECT)
@State(name = "AcpProjeAyarlari", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AcpProjeAyarlari : SimplePersistentStateComponent<AcpProjeAyarlari.State>(State()) {
    class State : BaseState() {
        /** Boş = tahmin edilir. */
        var dogrulamaKomutu by string("")
    }
}
// AcpSettings.State'e:
var agentKomutZamanAsimiMs by property(300_000)
```

### Servis ve arayüz

`AjanService`, `OnayKapisi`'nı uygular: `CompletableDeferred<Boolean>` durumda
bekler, `AjanDurumu.bekleyenKomut: String?` dolar. Yeni eylemler:
`ChatViewAction.AjanKomutCalistir`, `AjanKomutAtla`. `AjanUi.bekleyenKomut`
şeritte **kendi satırında** gösterilir (AGENTS.md "Dar panelde bağlantı
yerleşimi"): komut metni + "Çalıştır" / "Atla".

Çalışırken adımlarda `dogrula: çalışıyor — <komut>` görünür ve mevcut "Durdur"
düğmesi süreci de öldürür. Sonuç aynı satıra yazılır: `dogrula: başarılı`,
`dogrula: başarısız (çıkış 1)`, `dogrula: zaman aşımı`, `dogrula: atlandı`.

### Ayar ekranı

- Mevcut ACP ayar sayfasına (uygulama düzeyi) **zaman aşımı** alanı.
- Yeni **proje** ayar sayfası (`projectConfigurable`, ACP'nin altında):
  "Doğrulama komutu" alanı. Boşken tahmin edilen komut alanın içinde soluk
  yazıyla görünür (spec H4: çalışmadan önce görülebilir); tahmin yoksa
  "Tahmin edilemedi — komutu buraya yazın".
- Tanımsız komutta paneldeki mesaj bu sayfanın yolunu söyler.

### Modele giden metin

| Sonuç | Araç cevabı |
|---|---|
| Başarılı | `BAŞARILI` + kırpılmış çıktı |
| Başarısız | `BAŞARISIZ (çıkış N)` + kırpılmış çıktı |
| Zaman aşımı | `HATA: zaman aşımı (N sn); hata çıktısı yok` |
| Bulunamadı | `HATA: '<program>' bulunamadı. Bu bir kod hatası değil.` |
| Reddedildi | `HATA: komut reddedildi: <sebep>` |
| Atlandı | `Kullanıcı çalıştırmayı reddetti. Elindeki bilgiyle devam et.` |
| Tanımsız | `HATA: doğrulama komutu tanımlı değil.` + panelde ayar yolu |

### Yönerge

`AjanYonergesi`'ne altıncı kural: *"dogrula'yı yalnızca kullanıcı bir derleme
veya test hatası bildirdiyse çağır. Hata metni görevde zaten varsa çağırma.
Komut diskteki kodu derler; kaydettiğin düzenlemeler henüz diskte değildir."*
`AjanYonergesiTest` bu cümleyi sabitler (008'deki gerekçeyle).

---

## Riskler

| Risk | Karşılık |
|---|---|
| Model kullanıcı istemeden `dogrula` çağırır | Onay kapısı; yönerge tek başına güvenilmez (spec) |
| Dev çıktı belleği doldurur | Yakalama 2 MB'ta kesilir; modele 8.000 karakter |
| İptalde alt süreçler yaşar | Süreç ağacı öldürülür. Gradle **daemon**'ı bilinçli olarak ayrık yaşar — kabul edilen sınır |
| Komut standart girdi bekler (izleme kipi) | Girdi kapalı başlatılır; zaman aşımı yakalar |
| Windows çıktısı UTF-8 değil (Türkçe `cp1254`) | Çıktı sistem karakter kümesiyle okunur |
| Proje kökü yok (`basePath` null) | Araç "proje kökü bulunamadı" der, çalışmaz |

## Test stratejisi

- **Saf katman** (JUnit 5, saniyeler): tahmin tablosunun her satırı + Windows;
  ayrıştırma (tırnak, boşluk); denetimin her red sebebi + geçerli örnekler;
  kırpma (hata satırı ortada kalsa da korunur, sınır aşılmaz).
- **Çalıştırıcı** (platform testi, Unix'te): `true` → başarılı, `false` →
  başarısız, olmayan program → bulunamadı, `sleep 30` + 500 ms zaman aşımı,
  `sleep 30` + iptal → süreç gerçekten ölü (PID kontrolü).
- **Yürütücü**: sahte kapı ve sahte çalıştırıcıyla — reddedilen onayda
  çalıştırıcı **hiç çağrılmaz** (mutasyonla doğrulanır), tanımsız komut,
  reddedilen komut onaydan **önce** söylenir.
- **Servis**: bekleyen onay durumda görünür; `durdur` beklerken → komut
  çalışmaz, "Durduruldu".
- **Arayüz**: 280 px'te "Çalıştır" ve "Atla" görünür alanda (`AjanSeridiTest`
  kalıbı).
- **Gerileme kapısı**: mevcut 478 test değiştirilmeden geçer.
- **Elle** (`runIde`): Maven ve npm projesinde hata bildir → onay → düzeltme önizlemesi.
