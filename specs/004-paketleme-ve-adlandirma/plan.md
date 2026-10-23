# Plan: Paketleme ve Adlandırma

- **Spec no:** 004 — [spec.md](spec.md)
- **Tarih:** 2026-09-03
- **Durum:** Onaylandı

---

## Yaklaşım

Görünen ad `gradle.properties` içinde `acp.displayName` olarak tutulur — **tek
doğruluk kaynağı burasıdır**. Build sırasında iki yere akar:

1. `pluginConfiguration.name` → paketlenen `plugin.xml`'deki `<name>`
2. Üretilen bir kaynak dosyası (`acp-branding.properties`) → çalışma zamanında
   panel ve ayar başlıkları için okunur

Kimlik (`pluginConfiguration.id`) bundan tamamen ayrıdır ve sabittir.

## Değerlendirilen alternatifler

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Build zamanı, `gradle.properties` | Tek paket = tek ad, otomatik dağıtımla tutarlı | Her ad için ayrı paketleme | **Seçildi** |
| Çalışma zamanı, makinedeki dosya | Tek paket herkese | Eklenti listesindeki ad IDE başlarken sabitlendiği için orada değişmez | Elendi |
| İkisi birden | En esnek | İki mekanizma bakımı, hangisinin kazandığı karışır | Elendi |

## Arayüzler

```kotlin
internal object AcpBranding {
    const val VARSAYILAN = "ACP"      // yalnızca acil düşüş; doğru build'de kullanılmaz
    val displayName: String           // üretilen kaynaktan, lazy
    internal fun oku(stream: InputStream?): String   // kaynak yüklemeden test edilebilsin diye ayrı
}
```

Gradle tarafı:

```kotlin
val gorunenAd: Provider<String> = providers.gradleProperty("acp.displayName")  // yedeği YOK
val generateBranding = tasks.register("generateBranding") { /* kaynağı üretir */ }
sourceSets.named("main") { resources.srcDir(...) }
tasks.named("processResources") { dependsOn(generateBranding) }
```

Build tarafında kasten yedek değer yok: `gradle.properties` tek kaynak, eksikse
build anlaşılır biçimde patlar. Sessizce yanlış adla paketlemektense iyidir.

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `gradle.properties` | `acp.displayName` eklendi |
| `build.gradle.kts` | `generateBranding` görevi, `pluginConfiguration.name` |
| `dev/acp/AcpBranding.kt` | yeni |
| `dev/acp/ui/chat/ChatToolWindowFactory.kt` | `stripeTitle` ayarlanır |

## Yeniden Kullanılacak Mevcut Kod

- `java.util.Properties` — kendi ayrıştırıcımızı yazmıyoruz
- `AcpBundle` — kullanıcıya görünen sabit metinler zaten oradan geliyor

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok
- [x] `dev.acp.llm` etkilenmiyor
- [x] Dile özgü API yok
- [x] Tek doğruluk kaynağı `gradle.properties`

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Türkçe karakter bozulur | Ad "Ã…iÌ‡rket" görünür | `Properties.load(InputStream)` ISO-8859-1 varsayar; UTF-8 `Reader` kullanılır, testi var |
| Ad `=` veya `:` içerir | Kaynak bozulur | Yazarken kaçırılır |
| Kimlik yanlışlıkla adla birlikte değişir | Kullanıcıda iki kopya, güncelleme bozulur | `plugin.xml`'de `<id>` ayrı; yorumla işaretli |

## Test Stratejisi

- **Saf birim:** `AcpBranding.oku` — Türkçe karakter, eksik anahtar, boş değer,
  boşluk kırpma, dosya yok
- **Bütünleşme:** üretilen kaynağın gerçekten pakete girdiği (`displayName` boş değil)
- **Elle:** `-Pacp.displayName=...` ile paketleyip `plugin.xml`'deki `<name>` ve
  `<id>` kontrol edilir

## Uygulama Sırası

1. `AcpBranding` + testleri
2. Gradle görevi ve `pluginConfiguration.name`
3. Araç penceresi başlığı
4. Paketleme doğrulaması
