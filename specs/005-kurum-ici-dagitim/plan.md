# Plan: Kurum İçi Dağıtım

- **Spec no:** 005 — [spec.md](spec.md)
- **Tarih:** 2026-09-04
- **Durum:** Taslak

---

## Yaklaşım

IDE'nin **kendi özel eklenti deposu** mekanizması kullanılıyor. Eklenti kendi
indirme veya güncelleme mantığını yazmıyor (spec kapsam dışı).

Mekanizma şu: kurumsal artifact deposunda iki şey duruyor — paket dosyaları ve
onları listeleyen bir **dağıtım kaydı**. Kullanıcı IDE'ye o kaydın adresini bir
kez tanıtıyor; sonrasında IDE eklentiyi kendi eklenti ekranında gösteriyor ve
yeni sürüm çıktığında kendi güncelleme akışında bildiriyor.

Yayınlama, `./gradlew yayinla` benzeri **tek bir komut**: testleri çalıştırır,
paketler, dağıtım kaydını günceller, ikisini birlikte yükler.

## Değerlendirilen alternatifler

### Dağıtım yeri

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Kurumsal artifact deposu | Kimlik doğrulama, yedekleme, erişim denetimi hazır | Depo yapılandırmasına bağımlı | **Seçildi** |
| Kurum içi web sunucusu | En sade | Ayrıca sunucu kurmak/işletmek gerekir | Elendi |
| Paylaşılan dosya alanı | Sıfır altyapı | IDE HTTP bekliyor; otomatik güncelleme güvenilmez | Elendi |

### Yayınlama yeri

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Geliştirici makinesinden elle | Ek altyapı yok, hemen çalışır | Yayınlayanın ortamına bağlı | **Seçildi** |
| CI hattından | Tekrarlanabilir | CI erişimi ve gizli anahtar yönetimi gerekir | Sonraya |

Komut ortam değişkenlerinden kimlik okuyacak şekilde yazılacak; böylece CI'a
taşımak yalnızca değişkenleri tanımlamak olacak.

### Sürüm numarası

Elle, `gradle.properties`. Tarih tabanlı üretim elendi: sürümden içeriği anlamak
zorlaşıyor ve günde iki yayın sorun oluyor.

---

## Arayüzler

### Dağıtım kaydı

Depoda duracak, paketleri listeleyen bir kayıt dosyası. Alanları: eklenti kimliği,
sürüm, paket adresi, uyumlu IDE aralığı (`sinceBuild` / açık uçlu `untilBuild`),
ve değişiklik notu.

Kayıt **elle yazılmıyor**, paketleme bilgisinden üretiliyor — ikisinin ayrışması
kullanıcıya bozuk güncelleme olarak yansır (spec Davranış Kuralı 3).

### Gradle görevleri

```kotlin
// gradle.properties
acp.repo.url=https://depo.kurum.local/.../acp/      // yükleme kökü
acp.repo.publicUrl=https://depo.kurum.local/.../acp/ // kullanıcıların gördüğü kök
// Kimlik ortam değişkenlerinden: ACP_REPO_USER / ACP_REPO_TOKEN
```

| Görev | Ne yapar |
|---|---|
| `dagitimKaydiUret` | `updatePlugins.xml`'i sürüm ve kimlikten üretir |
| `surumKontrol` | Depoda aynı sürüm var mı diye bakar; varsa **durur** |
| `yayinla` | `check` → `buildPlugin` → `surumKontrol` → yükle (paket + kayıt) |

`yayinla` **`check`'e bağlı**: testler kırmızıysa yayın olmaz (spec H3).

### Kurulum belgesi

`docs/kurulum.md` — kopyalanabilir adımlar, dört IDE için aynı. Depo adresi
belgede **değişken olarak** değil gerçek değeriyle yazılır ki kopyala-yapıştır
çalışsın; ama kaynak koda gömülmez (Altın Kural 6 yalnızca **eklenti** için
geçerli — belge ayrı).

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `gradle.properties` | `acp.repo.url`, `acp.repo.publicUrl` |
| `build.gradle.kts` | `dagitimKaydiUret`, `surumKontrol`, `yayinla` görevleri |
| `docs/kurulum.md` | yeni — ekip için kurulum adımları |
| `AGENTS.md` | yeni komut ve sürüm artırma kuralı |
| `specs/005-.../tasks.md` | uygulama kaydı |

## Yeniden Kullanılacak Mevcut Kod

- `buildPlugin` — paketi zaten üretiyor
- `verifyNoBundledJars` — `check`'e bağlı, yayın öncesi otomatik koşuyor
- `pluginConfiguration` (spec 004) — kimlik ve sürüm ayrımı orada kuruldu;
  dağıtım kaydı bu ikisini oradan alacak
- IDE'nin **özel eklenti deposu** mekanizması — kendi güncelleme mantığımızı
  yazmıyoruz

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok — bu spec eklenti koduna dokunmuyor
- [x] `dev.acp.llm` etkilenmiyor
- [x] Dile özgü API yok
- [x] Eklenti kodunda yeni sabit yok; depo adresi **build** yapılandırmasında
      (eklentinin içinde değil)

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| **Depodan anonim okuma kapalı çıkar** | Dağıtım yeri komple değişmeli | **T10'da, her şeyden önce** gerçek depoya karşı doğrulanır |
| Paket ve kayıt ayrışır | Kullanıcıda bozuk güncelleme | Tek komut ikisini birlikte yükler; kayıt paketten üretilir |
| Aynı sürüm üzerine yazılır | Kullanıcıda karışık içerik | `surumKontrol` yayından önce durdurur |
| Kimlik bilgisi repoya sızar | Güvenlik | Kimlik yalnızca ortam değişkeninden; `gradle.properties`'e yazılmaz |
| Güncelleme ayarları siler | Kullanıcı anahtarını yeniden girer | Kimlik sabit kaldığı için ayarlar korunur (spec 004); elle doğrulanacak |

## Test Stratejisi

- **Saf birim:** dağıtım kaydı üretimi — sürüm, kimlik ve `sinceBuild` doğru mu;
  kayıt geçerli biçimde mi
- **Gradle:** `surumKontrol` aynı sürümde durur mu; `yayinla` testler kırmızıyken
  çalışmaz mı
- **Elle:** gerçek depoya yayınla, temiz bir IDE'de depo adresini tanımla, kur,
  sonra ikinci bir sürüm yayınlayıp güncellemenin göründüğünü doğrula

Bu spec'in doğrulaması ağırlıklı olarak **elle** — dağıtımın çalıştığı ancak
gerçek depo ve gerçek IDE ile anlaşılır.

## Uygulama Sırası

Riskli olan **ilk sırada**: anonim okuma çalışmıyorsa planın tamamı değişir.

1. **Depoya erişim doğrulaması** — bir dosya yükle, kimliksiz indirilebiliyor mu
2. **Dağıtım kaydı üretimi** — saf, testlenebilir
3. **Sürüm kontrolü** — aynı sürüm ikinci kez çıkmasın
4. **Yayınlama komutu** — testler → paket → kayıt → yükleme
5. **Kurulum belgesi**
6. **Uçtan uca doğrulama** — temiz IDE'de kur, ikinci sürümle güncelle
