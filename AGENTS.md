# ACP

Kendi sunucumuzda çalışan LLM'e (LiteLLM → SGLang → Qwen) bağlanan JetBrains eklentisi.
Tek kod tabanı; **IntelliJ IDEA, DataGrip, WebStorm ve PyCharm**'da çalışır.

---

## Altın Kurallar

### 0. Spec olmadan kod yazılmaz

`specs/NNN-ozellik/` altında `spec.md` → `plan.md` → `tasks.md` sırası izlenir ve
her aşama onaylanır. Şablonlar: [specs/000-template/](specs/000-template/).
Ayrıntı: [specs/README.md](specs/README.md).

### 1. Çalışma zamanı bağımlılığı eklenmez

Dağıtımda yalnızca kendi jar'ımız olmalı. IntelliJ platformu kendi `kotlin-stdlib`,
`kotlinx-coroutines` ve Jackson'ını sağlar; ikinci bir kopya göndermek kullanıcının
IDE'sinde `LinkageError` / `NoSuchMethodError` üretir — derlemede değil, **çalışma
zamanında**.

JSON için platformun Jackson'ı `JsonNode` seviyesinde kullanılır. Veri sınıfı
yansıması, anotasyon veya `jackson-module-kotlin` yok.

> Zorlayan: `verifyNoBundledJars` (`./gradlew check`).

**Bu kural test sınıf yolunda da geçerlidir** — orada `verifyNoBundledJars`
koruyamaz. Yaşanmış örnek: `kotlinx-coroutines-test` eklenince vanilya
`coroutines-core`'u sürükledi ve platformun **yamalı** sürümünü
(`…-intellij-N`) gölgeledi; `BasePlatformTestCase` o yamanın API'sini
çağırdığı için tüm platform testleri şu hatayla düştü:

```text
NoSuchMethodError: runBlockingWithParallelismCompensation
```

Coroutines, Jackson ve Kotlin stdlib **test tarafında da** platformdan gelir.
`runBlocking` yeterlidir; sanal zaman gerekiyorsa kütüphane eklemek yerine
saat enjekte edin (örnek: `CoalesceFlow`).

### 2. `dev.acp.llm` paketi saf kalır

O pakette `com.intellij` importu yasaktır. LLM katmanı IDE başlatmadan, saniyeler
içinde test edilebilmeli.

> Zorlayan: `ArchitectureTest`.

### 3. Dile özgü API kullanılmaz

Yalnızca `com.intellij.modules.platform`'a bağlıyız. `com.intellij.modules.java`,
Go/JS/Python PSI'ları ve dile özgü inspection'lar kullanılamaz — kullanılırsa eklenti
DataGrip ve WebStorm'da **yüklenmez**.

Dile duyarlılık `FileType` üzerinden yapılır (`.tsx` → React promptu, `.sql` → SQL
promptu). Gerçek PSI gerekirse `<depends optional config-file=...>` ile ayrı modül
olarak eklenir; çekirdeğe karıştırılmaz.

### 4. Kotlin sürümü hedef IDE ile eşleşir

`apiVersion`/`languageVersion` 2.3'e sabitli, çünkü desteklenen en eski IDE (2026.1)
Kotlin 2.3.20 bundle eder. Hedef IDE yükseltilmeden bu yükseltilmez.

Taban 2026.1 (`sinceBuild = 261`), `untilBuild` açık uçlu. Ekipteki tüm IDE'ler
2026 ve üzeri olduğu için daha eski sürüm desteklenmiyor.

### 5. Ağ işini IDE'ye bırak

Eklenti kendi TLS/proxy çözümünü kurmaz; IDE'nin proxy ayarlarını ve
`CertificateManager`'ını kullanır. Kurumsal ağdaki TLS kesmesi ve iç CA sertifikaları
ancak böyle çalışır.

API anahtarı `PasswordSafe`'te (işletim sistemi anahtar zinciri) saklanır, ayar
dosyasına **yazılmaz**.

### 6. Ayarlanabilir değerin tek tanım yeri `AcpSettings.State`'tir

**Varsayılan değer atamak serbesttir** — hatta iyi varsayılanlar beklenir.
Yasak olan, o değerin kullanıldığı yerde sabit olarak durması.

```kotlin
// YANLIŞ — iş mantığında sabit
private const val TIMEOUT_MS = 30_000
httpClient.connectTimeout(Duration.ofMillis(TIMEOUT_MS))

// DOĞRU — tek tanım yeri State, kullanım yeri ayarı okur
class State : BaseState() {
    var connectTimeoutMs by property(10_000)   // varsayılan; kullanıcı değiştirebilir
}
httpClient.connectTimeout(Duration.ofMillis(settings.state.connectTimeoutMs))
```

Kural: bir değer kullanıcıyı ilgilendiriyorsa (adres, model, sınır, zaman aşımı,
sıcaklık) `State`'te tanımlanır, ayar ekranında görünür ve kullanım yerinde
yalnızca ayardan okunur. Aynı varsayılanın iki yerde yazılması da ihlaldir.

**Proje bazında** değerler (spec 009'dan beri: doğrulama komutu) aynı kuralla
`AcpProjeAyarlari.State`'te tanımlanır; `workspace.xml`'de saklanır, depoya
girmez. İki dosya dışında ayarlanabilir değer tanımı yoktur.

**İki istisnasız yasak:** koda gömülü **kurum adresi** ve **model adı**.
Bunların varsayılanı boştur. Model elle yazdırılmaz — bağlantı testi sunucudan
listeyi çeker, kullanıcı listeden seçer.

Gerekçe: adres ve model kuruma/ortama göre değişir; koda gömülü değer hem
yeniden derleme gerektirir hem de kurum bilgisini paketin içine sızdırır.

---

## Mimari

```
dev.acp
  llm/        LLM katmanı — SAF (com.intellij importu yasak)
    openai/   OpenAiLlmClient, OpenAiCodec, SseLineSplitter, SseParser
    text/     ThinkTagFilter, TokenEstimator, Truncator, MarkdownFenceSplitter
    prompt/   PromptTemplate, PromptBuilder
    completion/ FimBicimi, FimPromptKurucu, OneriTemizleyici, TamamlamaBaglami,
                TamamlamaKipi(Secici), GirintiKesici
    agent/      AjanDongusu, AjanYonergesi, DuzenlemePlani, BaglamKirpici — SAF
  net/        HttpClientFactory — IDE proxy + CertificateManager
  settings/   AcpSettings, AcpSecrets, AcpSettingsConfigurable
  service/    LlmClientService (APP), ChatSessionService (PROJECT)
  context/    ContextProvider + uygulamaları
  ui/chat/    ChatToolWindowFactory, ChatView, SwingChatView, CodeBlockComponent
  actions/    Seçim aksiyonları
  editor/     TargetAnchor, ResultApplier — diff önizleme + WriteCommandAction
  completion/ AcpInlineCompletionProvider — satır içi öneri
  agent/      IdeAracYurutucu — ajan araçlarının IDE tarafı
```

Veri akışı: `ChatSessionService` → `LlmClient.streamChat()` → `Flow<ChatDelta>` →
coalesce (~50ms) → EDT → `ChatView`.

`Flow<ChatDelta>` (ham `Flow<String>` yerine) bilinçli: v2'de araç çağırma eklenirken
yeni bir sealed varyant yetecek, imzalar değişmeyecek.

### Satır içi tamamlama (spec 006)

Platformun `InlineCompletionProvider`'ı kullanılır; soluk metnin çizimi, kabul
tuşu, yazınca kaybolma ve tek adımlı geri alma oradan gelir.

Üç şey kolayca yanlış yapılır:

1. **Uzantı noktasının adı `com.intellij.inline.completion.provider`.**
   `inlineCompletionProvider` DEĞİL. Yanlış yazılırsa IDE hata vermez, sessizce
   hiçbir şey kaydetmez. `TamamlamaSaglayiciTest` bunu yakalar.
2. **Kayıt `acp-completion.xml`'de**, `com.intellij.modules.lang`'e isteğe bağlı
   bağımlılıkla. Uygulanan sınıflar `lang.impl` içinde; koşulsuz kayıt, `lang`
   bulunmayan bir üründe eklentinin tamamen yüklenememesine yol açar.
3. **Ham uç kullanılır** (`/v1/completions`), sohbet ucu değil. FIM belirteçleri
   ham prompt'ta gitmeli; sohbet ucu araya kendi şablonunu sokup bozar.

FIM biçimi ayardan seçilir (`Kapalı` / Qwen / DeepSeek / StarCoder / CodeLlama /
Sohbet tarzı). Model adından **tahmin edilmez**: tahmin yanılırsa model
belirteçleri düz metin sanır ve hata vermeden kötü öneri üretir.

Yeni bir model ailesine geçerken FIM desteği şu testle doğrulanır:

```bash
export ACP_FIM_URL=... ACP_FIM_KEY=... ACP_FIM_MODEL=... ACP_FIM_BICIM=QWEN
./gradlew test -Dacp.canliTest=true --tests "*CanliSunucuTest*"
```

Yanıttaki `matched_stop` alanı en değerli sinyaldir: doluysa model özel bir
belirteçte durmuştur, yani FIM belirteçlerini tanıyor. `text` doğru görünse bile
bu alan boşsa şüphelenin.

**Varsayılan kapalıdır** ve öyle kalmalıdır: sohbette kullanıcı her seferinde
bilinçli bir eylem yapar, satır içi tamamlamada kod yazdıkça kendiliğinden
gider. `AcpSettings.State.tamamlamaAcikMi()` bu kararın **tek** yeridir.

### Çok satırlı tamamlama (spec 007)

Kip, imlecin konumundan **otomatik** seçilir — `TamamlamaKipiSecici.sec()`
bu kararın tek yeridir, sağlayıcı yalnızca onu çağırır:

| İmlecin solunda | Kip |
|---|---|
| yalnızca boşluk | `COK_SATIR` — blok önerisi, 256 token tavanı |
| başka bir şey | `TEK_SATIR` — 006 davranışı, 64 token |
| üye erişimi (`.` `::` `->`) | `YOK` — istek bile gitmez |

Öneri `GirintiKesici` ile bloğun içinde tutulur. **Altı kuralı vardır ve hepsi
gerçek model çıktısından doğmuştur** — sınıfın KDoc'unda tek tek yazılı. En kolay
yanlış yapılan ikisi:

- **İlk satır girinti karşılaştırmasına girmez.** Model imlecin satırının
  *devamını* yazar; karşılaştırmaya alınırsa öneri daha başlamadan kesilir.
- **Kesme eşiği modelin kendi hizasından türer.** Model bloğu bazen girintisiz
  yazıyor (aynı sunucu, aynı ayar, farklı fonksiyon); bizim girintimiz eşik
  olsaydı öneri ikinci satırda kesilirdi.

### Tamamlamada bir şey görünmüyorsa

Hatalar bilinçli olarak sessizdir — yazarken hata penceresi açmak kabul edilemez.
Sebebi görmek için:

```
Help → Diagnostic Tools → Debug Log Settings → dev.acp
```

Günlüğe yalnızca **ölçüler** yazılır (kip, token, bitiş nedeni, satır sayıları);
kullanıcının kodu ve modelin ürettiği metin **yazılmaz**.

İki kural, ikisi de acıyla öğrenildi:

- *Sessizce vazgeçen her yol, neden vazgeçtiğini ölçülebilir biçimde bırakmalı.*
- *Emniyet supabı sıradan bir girdiyi kesiyorsa artık emniyet supabı değildir.*
  (`completionMaxLines` 6'yken sıradan bir gövdenin `return`'ünü kesiyordu.)

### Çok dosyalı düzenleme / ajan (spec 008)

Kendi araç çağırma döngümüz — harici bir agent motoru **yok**, hiçbir
geliştirici makinesinde ayrı süreç gerekmiyor.

Üç katman ve aralarındaki sınır önemli:

| Katman | Yer | Neden |
|---|---|---|
| Döngü | `llm/agent/AjanDongusu` — **saf** | Hata yolları IDE'siz test edilebilsin |
| Araçlar | `agent/IdeAracYurutucu` | Arama, okuma, düzenleme toplama |
| Uygulama | `editor/TopluUygulayici` | Önizleme + tek geri alma adımı |

**`dosya_duzenle` diske YAZMAZ.** Yalnızca plana ekler; yazma döngü bittikten
sonra tek işlemde yapılır. Spec'in "ya tamamen ya hiç" kuralı buna dayanıyor ve
ihlali **sessizce** olur: kod çalışır, testler geçer, ama iptal edilen bir iş
dosyaları yarım bırakır.

**Bayatlık kontrolü yazmadan ÖNCE, hepsi için birden.** Dosya dosya kontrol edip
yazsaydık, üçüncü dosyada bayatlık bulunduğunda ilk ikisi çoktan yazılmış olurdu.

**Tek geri alma adımı `withGroupId` ile değil, tek `WriteCommandAction` ile.**
Bir komut birden fazla belgeye dokunduğunda platform geri almayı kendiliğinden
global yapıyor. Mutasyon testiyle doğrulandı.

**`AjanYonergesi` bir ayrıntı değil, özelliğin kendisi.** T10 ölçümü — aynı
model, aynı araçlar, aynı görev:

| | Yönergesiz | Yönergeyle |
|---|---|---|
| Adım | 20 (sınırda) | 3 |
| Token | 204.490 | 4.683 |
| Düzenleme | **0** | **9** |

Yönergeyi kısaltmak özelliği çalışmaz hâle getirebilir ve bu **hiçbir davranış
testinde görünmez**.

**Bağlam kırpma mesaj SİLMEZ.** Her `tool` mesajı, kendisini isteyen `assistant`
mesajının `tool_calls` girdisiyle eşleşmek zorunda; birini atmak eşleşmeyi bozar
ve sunucu isteği geçersiz sayar — bağlamı aşmaktan daha kötü.

**Varsayılan `ONIZLE`.** `DOGRUDAN`, 006/007'de konan "öneri asla onay olmadan
koda yazılmaz" kuralından bilinçli sapma; kullanıcının açıkça seçmesi gerekiyor
ve kip sohbet panelinde de görünüyor (yalnızca ayarlarda gizli kalmamalı).

### Dar panelde bağlantı yerleşimi — iki kez yapılan hata

Sohbet panelinde şerit eklerken **`FlowLayout` satır kaydırır ve kaydırdığını
kırpar**. Bu hata iki kez yaşandı (spec 003 "Uygula", spec 008 ajan şeridi);
ikisini de kullanıcı ekran görüntüsüyle buldu.

Kural: bağlantılar ya `BorderLayout.EAST`'te (dar alanda kaybolması gereken şey
açıklama etiketidir, düğmeler değil) ya da **kendi satırlarında**, kısa
etiketlerle. Yeni bir şerit eklerken 280 px genişlikte yerleşim testi yazın —
`AjanSeridiTest` örnek.

⚠️ Swing'de `isVisible` **etkin görünürlük değildir**: kabı gizliyken bile
`true` döner. Yerleşim testlerinde ataları da gezin.

### Saf testler gerçek model çıktısıyla yazılır

`GercekModelCiktisiTest` gerçek yanıtları birebir tutar. Spec 007'de **iki hata
da** ancak gerçek çıktı kullanılınca görüldü; uydurma girdiyle yazılmış testler
boru hattının çalıştığını gösteriyor ama gerçek girdiyle çalıştığını
göstermiyor. Model ailesi değişince ilk güncellenecek yer burasıdır.

### Testin rengine güvenmeden önce testi sına

İki yönde de yaşandı (spec 008):

- **Kırmızı ama kod doğru.** Bir kırpma testi yüksekliğe göre yazıldı ve
  kırmızı kaldı; neredeyse çalışan kod değiştirilecekti. Aynı testten
  kullanıcının onayladığı sohbet gövdesi geçirilince o da kırmızı çıktı:
  test ortamı o ölçümü yapamıyordu. Kural: kırmızı kalan bir testte kodu
  değiştirmeden önce **bilinen-iyi bir bileşeni aynı testten geçirin**.
- **Yeşil ama davranış bozuk.** `DOGRUDAN` kipinin "aynı uygulama yolunu
  kullandığı" testle doğrulanmıştı (T61); o yolu **kimsenin çağırmadığı**
  ancak runIde'de görüldü (T90). Kural: bir kip ya da ayar için test, ayarın
  okunduğunu değil **sonucunu** doğrulamalı — `AjanKipiTest` örnek.

## Komutlar

| Komut | Ne yapar |
|-------|----------|
| `./gradlew build` | Derler + testleri çalıştırır |
| `./gradlew test` | Yalnız testler |
| `./gradlew runIde` | Eklentinin kurulu olduğu sanal IDE açar |
| `./gradlew buildPlugin` | Dağıtılabilir zip → `build/distributions/` |
| `./gradlew check` | Testler + `verifyNoBundledJars` |
| `./gradlew verifyPlugin` | Plugin Verifier (API uyumluluğu) |

Testler iki türdür: saf JUnit 5 testleri (IDE gerekmez, saniyeler) ve
`BasePlatformTestCase` testleri (JUnit 3/4 biçiminde, vintage motoruyla koşar,
platformu ayağa kaldırır). İkisi aynı `./gradlew test` ile çalışır.

JDK kurmak gerekmez — Gradle toolchain JDK 21'i kendisi indirir.

### Build ortamı — beş tuzak

**1. Yerel IDE kullanılır, indirilmez.** Build, kurulu IDE'ye bağlanır
(varsayılan `/Applications/IntelliJ IDEA.app`). Farklı bir yol için
`gradle.properties` veya `~/.gradle/gradle.properties` içine:

```properties
acp.ide.path=/kendi/yolunuz/IDE.app
```

**2. IntelliJ Platform Gradle Plugin 2.18.1'e YÜKSELTMEYİN.** 2.18.1'de platform
2026.1 ile `:test` görevi çözümlenemiyor:

```
The 'Core' plugin (ID: 'com.intellij') was not found in .../lib
```

`compileKotlin` sorunsuz geçtiği için hata geç fark ediliyor — build'in yeşil
sanıldığı yerde aslında test hiç koşmuyor. 2.17.0 ile 2026.1 sorunsuz.
Üstkaynak: [JetBrains/intellij-platform-gradle-plugin#2189](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2189) (açık).
Yükseltmeden önce mutlaka `./gradlew test` ile doğrulayın.

**3. `runIde`'yi sandbox AÇIKKEN çalıştırmayın.** Çalışan sandbox, jar'ın
değiştiğini fark edip kendini yeniden başlatmaya kalkar; ama yeniden başlatıcı
IDE'yi **sandbox parametreleri olmadan** çalıştırır:

```
# .intellijPlatform/sandbox/acp/IU-*/log/restarter.log
exec-ing: ["/Applications/IntelliJ IDEA.app/Contents/MacOS/idea"]
```

Sonuç: sandbox kapanır, yerine geliştiricinin **normal IDE'si** açılır. Eklenti
orada kurulu olmadığı için "ayar görünmüyor" diye teşhis edilir — oysa sorun
ayarda değil, yanlış IDE'ye bakılıyor olmasındadır.

Sandbox açık mı, tek komutla:

```bash
ps aux | grep "[i]ntellijPlatform/sandbox"   # çıktı varsa sandbox açıktır
```

**`MacOS/idea` diye ARAMAYIN.** `runIde` ile açılan sandbox, IDE'nin gömülü
JBR'ı üzerinden `java` süreci olarak çalışır:

```
/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java -D...
```

Normal IDE ise `/Applications/IntelliJ IDEA.app/Contents/MacOS/idea` olarak
görünür. `MacOS/idea` araması sandbox'ı **bulamaz** ve "kapalı" sanıp `runIde`
çalıştırmanıza yol açar — yukarıdaki tuzağa tam olarak böyle düşülür.

`runIde`'den önce açık sandbox'ı kapatın.

**Aynı kural `verifyPlugin` ve `buildPlugin` için de geçerli** (2026-10-23):
ikisi de `prepareSandbox` ile jar'ı açık sandbox'ın altına yazar. Sandbox eklentiyi
sıcak yeniden yüklemeye çalışır, "not unload-safe" der ve yarım durumda kalır;
`buildSearchableOptions` da "Only one instance of IDEA can be run at a time" ile
düşer. `./gradlew test` güvenlidir (ayrı test sandbox'ı).

**4. `verifyPlugin` başarısız olurken uyarı listesi boş olabilir.** Bu,
"temiz geçti" demek DEĞİLDİR — doğrulayıcının hiç çalışmadığı anlamına gelir.
Eklenti tanımlayıcısı geçersizse hiçbir kontrol yapılmadan durur:

```
Scheduled verifications (0):
The following files specified for the verification are not valid plugins:
```

İki tanımlayıcı kuralı:
- `<description>` **zorunlu**, en az 40 karakter.
- Açıklamanın **başı ASCII olmalı**. Doğrulayıcı Türkçe diyakritikleri
  (ü, ı, ş) Latin harfi saymaz; bu yüzden ilk cümle İngilizcedir.

Ayrıca: `./gradlew verifyPlugin` çıkış kodunu bir alt kabukta okurken
`echo $?` başka bir komuttan sonra gelirse **echo'nun** kodunu okursunuz.
Gerçek sonucu görmek için:

```bash
(./gradlew verifyPlugin > log 2>&1; echo "CIKIS=$?" >> log); tail -1 log
```

**5. Test ortamında dil eklentisi YOK.** `BasePlatformTestCase`'de `.java`,
`.js`, `.kt` dosyaları **düz metin** açılır (`dil=TEXT`); referans, kullanım,
tanım araması hiçbir şey bulmaz. Boş sonuç "API çalışmıyor" demek değildir —
test ortamı bu ölçümü yapamıyor. Dil eklentisini teste eklemek
(`bundledPlugin("com.intellij.java")`) tuzak #2'deki hatayla düşer.

Dile bağlı davranış (spec 010 ilişki araması) gerçek IDE'de doğrulanır:

```bash
./gradlew runIde -Pacp.iliskiDenetimi=true   # sandbox kapalıyken (tuzak #3)
grep "ILISKI-DENETIMI" .intellijPlatform/sandbox/acp/IU-*/log/idea.log | tail -12
```

Sandbox `src/test/testData/iliski`'yi açar, senaryoları koşar, `OZET gecti=N
kaldi=M` yazar ve kendini kapatır. Senaryolar `IliskiDenetimi.kt`'de.

### Ekibe dağıtım (spec 005)

Kurulum ve güncelleme ekip için [docs/kurulum.md](docs/kurulum.md)'de; başka bir
kurumda sıfırdan derleyip Nexus'a yüklemek için [docs/yayinlama-rehberi.md](docs/yayinlama-rehberi.md). Yayın:

```bash
# gradle.properties → version'ı ARTIRIN (aynı sürüm ikinci kez yayınlanamaz)
export ACP_REPO_USER=... ACP_REPO_TOKEN=...   # yalnızca ortam; dosyaya yazılmaz
./gradlew yayinla -Pacp.repo.url=https://<kurumun deposu>/   # sandbox KAPALIYKEN (tuzak #3)
```

- **Sürüm** yalnızca `gradle.properties`'te (`version=`). Kimlik (`dev.acp.assistant`)
  asla değişmez — değişirse kullanıcıda iki kopya olur.
- **Depo adresi** kurumdan kuruma değişir; koda ve `gradle.properties`'e yazılmaz,
  `-Pacp.repo.url` (gerekirse `-Pacp.repo.publicUrl`) ile verilir.
- `yayinla`: `check` → paket → `surumKontrol` (depoda varsa durur) → önce paket,
  sonra `updatePlugins.xml`. Kayıt paketin `plugin.xml`'inden üretilir, elle yazılmaz.

Depo yoksa elle: `./gradlew buildPlugin` → **Settings → Plugins → ⚙ → Install
Plugin from Disk…**. Bu yolun güncelleme mekanizması yoktur.

### GPU sunucusu olmadan geliştirme

`dev.acp.fake.FakeSglangServer` OpenAI-uyumlu uçları taklit eder; `main()`'i
çalıştırıp yazdırdığı adresi ACP ayarlarına girin. Modları: yarıda kesilen akış,
401, 500, heartbeat, reasoning bloğu, usage parçası.

## Konvansiyonlar

- **Dil:** doküman, yorum, UI metinleri ve commit mesajları **Türkçe**; sınıf ve
  fonksiyon adları İngilizce.
- **Commit:** `tip(spec NNN): Türkçe özet` — örn. `feat(spec 001): SSE ayrıştırıcı`.
- **Kullanıcıya görünen her metin** `AcpBundle`'dan geçer; koda gömülmez.
- **TDD:** önce başarısız test, sonra en küçük implementasyon.
- Testler: saf olanlar düz JUnit, IDE gerekenler `BasePlatformTestCase`.

## Kapsam ve durum

Her konu kendi spec'inde — tek dosyaya yığmak okunurluğu düşürür.
Ayrıntı: [specs/README.md](specs/README.md).

| No | Konu | Durum |
|----|------|-------|
| [001](specs/001-cekirdek-ve-model-baglantisi/) | Çekirdek ve model bağlantısı | Uygulandı — kurumsal TLS doğrulanamadı |
| [002](specs/002-sohbet-paneli/) | Sohbet paneli | Uygulandı |
| [003](specs/003-secim-aksiyonlari/) | Seçim aksiyonları | Uygulandı |
| [004](specs/004-paketleme-ve-adlandirma/) | Paketleme ve adlandırma | Uygulandı |
| [005](specs/005-kurum-ici-dagitim/) | Kurum içi dağıtım | Beklemede |
| [006](specs/006-satir-ici-tamamlama/) | Satır içi kod tamamlama | Uygulandı |
| [007](specs/007-cok-satirli-tamamlama/) | Çok satırlı kod tamamlama | Uygulandı |
| [008](specs/008-cok-dosyali-duzenleme/) | Çok dosyalı düzenleme (agent) | Uygulandı |
| [009](specs/009-derleme-hatasi-duzeltme/) | Derleme hatasını düzeltme | Uygulandı |
| [010](specs/010-uzman-kod-anlama/) | Uzman kod anlama | Uygulandı |

**Sonraki sürümler:** kurum içi otomatik dağıtım, satır içi FIM kod tamamlama,
dosya düzenleyen agent. Çekirdek arayüzler (`Flow<ChatDelta>`, `ContextProvider`)
bunları imza değiştirmeden eklenebilecek şekilde tasarlandı.
