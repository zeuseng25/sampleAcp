# Görevler: Çekirdek ve Model Bağlantısı

- **Spec no:** 001 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Kapatıldı — 1 kriter doğrulanamadı (T91)

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Hazırlık

- [x] T01 Gradle iskeleti, `plugin.xml` (yalnız `modules.platform`), boş tool window, `AcpBundle` → `./gradlew build` yeşil ✅
- [x] T02 `verifyNoBundledJars` görevi → `./gradlew check` çıktısı `Dağıtım temiz: [acp-0.1.0.jar]` ✅
- [x] T03 `ArchitectureTest` → `llm` paketinde `com.intellij` importu bulunursa test kırmızı ✅
- [x] T04 `runIde` ile gözle doğrulama → panel sağ çubukta açıldı, Türkçe metin doğru göründü ✅ *sandbox'ta 0 ERROR*

## Saf çekirdek — IDE gerekmez

- [x] T10 `SseLineSplitter` → 9 test yeşil (parça sınırı, CRLF bölünmesi, flush) ✅
- [x] T11 `SseParser` → 11 test yeşil (`data:` boşluklu/boşluksuz, `[DONE]`, heartbeat, çok satırlı) ✅
- [x] T12 `ThinkTagFilter` → 8 test yeşil (etiket parça sınırında bölünse de) ✅
- [x] T13 `ChatModels` + `LlmError` → `Flow<ChatDelta>` sealed ✅
- [x] T14 `FakeSglangServer` (8 mod) → `main()` çalışır, adres yazdırır ✅
- [x] T15 [P] `OpenAiCodec.encodeRequest` → `stream_options.include_usage` gönderiliyor ✅ *20 test*
- [x] T16 [P] `OpenAiCodec.decodeChunk` → `content`, `reasoning_content`, boş `choices` usage parçası, `finish_reason` ✅
- [x] T17 [P] `OpenAiCodec.decodeModels` → `/v1/models` gövdesinden `List<ModelInfo>` ✅
- [x] T18 [P] `awaitCancellable` → job iptal edilince `future.isCancelled` true ✅ *4 test*
- [x] T19 [P] `coalesceText` → 100 token 50ms pencerede ≤3 delta ✅ *6 test*

## Ayarlar ve ağ — riskli, öne alındı

- [x] T20a `BasePlatformTestCase` altyapısı çalışır hâle getirildi → `PlatformSmokeTest` 3 test yeşil ✅ *(bkz. Notlar)*

- [x] T20 [P] `UrlNormalizer` → dört yazım aynı sonuca gidiyor; şemasız/bozuk/desteklenmeyen girdi reddediliyor ✅ *11 test*
- [x] T21 `AcpSettings` (`PersistentStateComponent`) → değerler serileşiyor, varsayılanlar `acp.xml`'i şişirmiyor, sunucu/model varsayılanı boş ✅ *8 test*
- [x] T22 `AcpSecrets` (`PasswordSafe`) → anahtar yazılıp okunuyor, sunucu başına ayrı, silinebiliyor; **`acp.xml`'e sızmadığı regresyon testiyle korunuyor** ✅ *6 test*
- [x] T23 `HttpClientFactory` → IDE'nin `JdkProxyProvider` vekil seçicisi ve `CertificateManager.sslContext`'i bağlandı; testler JDK varsayılanına düşmediğimizi doğruluyor ✅ *3 test*
- [x] T24 `AcpSettingsConfigurable` → 9 alan, URL doğrulaması, anahtar durumu etiketi, model açılır listesi, test düğmesi; derleniyor ✅ *görsel doğrulama T25 ile birlikte*
- [x] T25 "Bağlantıyı test et" → **gerçek sunucuya** (OpenRouter) karşı 425 model döndü, seçici doldu, ekran donmadı ✅ ⚠️ *kurumsal LiteLLM'e karşı doğrulanmadı — bkz. T91*

## İstemci

- [x] T30 `OpenAiLlmClient.listModels` → sahte sunucuda liste döner; 401, 404, bağlantı reddi ayrı hata tiplerine eşleniyor ✅ *7 test*
- [x] T31 `OpenAiLlmClient.streamChat` → token'lar sırayla akıyor, heartbeat bozmuyor, usage bitişe taşınıyor ✅
- [x] T32 Hata eşlemesi → 401/400/500 ayrı tiplere eşleniyor; yarıda kesilen akışta gelen metin korunup `StreamTruncated` bildiriliyor ✅
- [x] T33 İptal → job iptal edilince sunucu kopmayı gördü (`clientDisconnected`) ✅ *donanımın serbest kaldığının kanıtı*
- [x] T34 Düşünme bölümü → her iki yol da testli: ayrı alan → doğrudan, cevap içinde → `<think>` ayıklanarak ✅

## Doğrulama ve kapanış

- [x] T90 Kabul kriterleri doğrulandı — **biri hariç** (aşağıya bak)
- [~] T91 ⚠️ **DOĞRULANAMADI — altyapı yok.** *2026-09-04*
  - H2'nin son kriteri: "kurumsal ağdaki sertifika ve vekil yapılandırması ek
    ayar gerektirmeden çalışır."
  - **Test edilemedi:** projenin tek erişilebilir uç noktası OpenRouter ve o
    kurumsal TLS kesmesinden geçmiyor, dolayısıyla kurumsal LiteLLM'in yerini
    tutmuyor.
  - Spec, bu kriter **açık kalarak** kapatıldı (kullanıcı kararı). Kod tarafında
    yapılması gereken yapıldı: `HttpClientFactory` IDE'nin `CertificateManager`
    ve `JdkProxyProvider`'ına bağlı. Eksik olan kanıt, uygulama değil.
  - **Kurumsal sunucuya erişim olduğu ilk gün yapılacak ilk iş budur** —
    Ayarlar → Bağlantıyı Test Et. Başarısız olursa 005 (dağıtım) durur, çünkü
    ekipteki herkes aynı duvara çarpar.
- [x] T92 `AGENTS.md` güncellendi (build tuzakları, test sınıf yolu kuralı, Kural 6)

---

## Notlar

### Ayar ekranında iki UI denemesi çürüdü (2026-09-03)

Model seçimi için sırayla denendi:

1. **Düzenlenebilir `JComboBox`** — IntelliJ temasında sıradan bir metin alanı
   gibi görünüyor, açılır liste olduğu anlaşılmıyordu.
2. **`ComboBox` + `ComboboxSpeedSearch`** — arama kutusu combo'nun üstünde,
   ilgisiz satırların yanında beliriyordu; ayrıca speed search yalnızca
   düzenlenemeyen combo'da çalıştığı için modeli elle yazma imkânı kayboluyordu.
3. **Seçildi:** metin alanı + "Seç..." düğmesi →
   `JBPopupFactory.createPopupChooserBuilder` + `setNamerForFiltering` +
   `setFilterAlwaysVisible`. Arama kutusu kendi içinde ve doğru yerde; metin
   alanı durduğu için model elle de yazılabiliyor.

Gerekçe kodda da yazılı, biri "arama ekleyeyim" deyip aynı yola girmesin diye.

### plugin.xml'de key/bundle atlanamıyor (2026-09-03)

Ayar sayfası başlığı markalı addan gelsin diye `key`/`bundle` verilmemişti;
platform bunu SEVERE olarak günlüğe düşürüyordu:

```text
No display name specified in plugin descriptor XML file for configurable
```

Çözüm: bundle'ı da `generateBranding` üretiyor
(`messages/AcpBrandingBundle.properties` → `settings.title`). Böylece hem ad
markalı kalıyor hem platform sınıfı erkenden yüklemek zorunda kalmıyor.


### Sıra düzeltmesi — T30 öne alındı (2026-09-03)

`plan.md`'deki uygulama sırası "ayarlar → ağ → bağlantı testi → istemci" diyordu.
Ama bağlantı testi düğmesi `listModels()`'a ihtiyaç duyuyor, o da istemcinin
parçası. Sıra hatalıydı: `listModels` (T30), ayar ekranından (T24) önce yazıldı.

`streamChat` (T31) hâlâ istemci bloğunda; yalnızca `listModels` öne alındı.

### searchableOptions jar'ı (2026-09-03)

Ayar ekranı eklenince dağıtıma `acp-0.1.0-searchableOptions.jar` katıldı. Bu
platformun ürettiği bizim artifact'ımızdır (ayarları IDE aramasından bulunabilir
yapar), yabancı bağımlılık değil — `verifyNoBundledJars` doğru davrandı.

Yan etkisi: `buildSearchableOptions` indeksleme için bir IDE örneği başlattığından
`./gradlew check` 0,5 s yerine ~16 s sürüyor. Rahatsız ederse kapatılabilir,
ama ayarların aranabilir olması kullanıcı için gerçek bir kazanç.


### Platform test altyapısı — iki engel aşıldı (2026-09-03)

`testFramework(TestFrameworkType.Platform)` plugin 2.17.0 ile artık bağımlılık
çözümlemesini bozmuyor, ama iki sorun daha çıktı:

1. **`NoClassDefFoundError: junit/framework/TestCase`** — platformun test
   dinleyicisi JUnit 4 arıyor. `BasePlatformTestCase` JUnit3/4 biçiminde.
   Çözüm: `junit:junit:4.13.2` + `junit-vintage-engine`. Saf testler JUnit 5
   kalmaya devam ediyor; ikisi birlikte koşuyor.

2. **`NoSuchMethodError: runBlockingWithParallelismCompensation`** —
   `kotlinx-coroutines-test` eklenmişti; vanilya `coroutines-core`'u sürükleyip
   platformun yamalı sürümünü gölgeledi. Çözüm: bağımlılığı kaldırmak. Zaten
   hiçbir testte kullanılmıyordu (`runTest` yok, hepsi `runBlocking`).

İkincisi Altın Kural 1'in test sınıf yolunda da geçerli olduğunu gösterdi;
`AGENTS.md`'ye örnekle birlikte yazıldı. `verifyNoBundledJars` orayı koruyamıyor
çünkü test bağımlılıkları pakete zaten girmiyor — kontrol edilen sızıntı değil,
**gölgeleme**.

Kazanç: T22'nin "anahtar `acp.xml`'de bulunmaz" güvenlik kriteri artık elle
değil, otomatik testle doğrulanabilecek.


### SAPMA — kod spec'ten önce yazıldı (2026-09-03)

T01–T04 ve T10–T19, **bu spec yazılmadan önce** uygulandı. Sıra
`spec.md → plan.md → tasks.md → kod` olmalıyken doğrudan koda geçildi.

**Neden oldu:** Tasarım sohbette onaylandı ve plan repo dışında, `~/.claude/plans/`
altında tutuldu — ekibin göremeyeceği bir yerde. Projenin spec-driven kapıları
henüz kurulmamıştı.

**Sonuç:** Kod geriye dönük olarak bu spec'in kapsamına alındı ve `plan.md`'nin
"Yeniden Kullanılacak Mevcut Kod" bölümünde listelendi. Atılmadı çünkü onaylanan
tasarımla örtüşüyor ve testleri geçiyor — ama doğrulaması bu spec'in kriterlerine
göre yeniden yapılacak (T90).

**Önlem:** `AGENTS.md` Altın Kural 0: *spec olmadan kod yazılmaz.* Repo dışı plan
dosyası kullanılmıyor; tek kaynak `specs/`.

### Küçük sapma — `coalesceText`'te enjekte saat (2026-09-03)

`plan.md` `runTest` sanal zamanını öngörüyordu; yerine `now: () -> Long` enjekte
edildi. Testler `kotlinx-coroutines-test` sürüm uyumuna bağlı kalmıyor ve tamamen
deterministik. Doğrulama kriteri (100 token → ≤3 delta) korundu.

### Build ortamı kararı (2026-09-03)

IntelliJ Platform Gradle Plugin **2.17.0'a sabitlendi**. 2.18.1'de platform 2026.1
ile `:test` görevi çözümlenemiyor (`The 'Core' plugin (ID: 'com.intellij') was not
found`). `compileKotlin` geçtiği için hata geç fark ediliyor — build yeşil sanılırken
testler hiç koşmuyor. Üstkaynak: JetBrains/intellij-platform-gradle-plugin#2189 (açık).

Build IDE'yi indirmiyor, kurulu olanı kullanıyor (`acp.ide.path`).

### Spec bölünmesi (2026-09-03)

Bu spec başlangıçta sohbet panelini, seçim eylemlerini ve paketlemeyi de içeriyordu
(altı hikâye). Okunurluğu düştüğü için bölündü: sohbet → [002](../002-sohbet-paneli/),
seçim eylemleri → [003](../003-secim-aksiyonlari/), paketleme →
[004](../004-paketleme-ve-adlandirma/). Bundan sonra her yeni konu kendi spec'ini alır.
