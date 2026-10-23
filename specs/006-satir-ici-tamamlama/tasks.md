# Görevler: Satır İçi Kod Tamamlama

- **Spec no:** 006 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı — *2026-09-04*

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Model doğrulaması — her şeyden önce

- [x] T10 Gerçek modele FIM isteği → **destekleniyor** ✅ *2026-09-04*
  - Model: **`qwen/qwen3.8-27b`**, Qwen biçimi belirteçleriyle denendi.
  - Yanıt: `text: "a + b"`, `completion_tokens: 4`, `matched_stop: 248044`.
  - Yorumu: yalnızca ortadaki parça geldi, sonek tekrarlanmadı, model özel
    belirteçte durdu — yani belirteçleri **tanıyor**, düz metin sanmıyor.
  - Yan bulgu: `Authorization: Bearer` çalışıyor; eklentinin mevcut kimlik
    başlığı doğru, değiştirmeye gerek yok.

## Saf çekirdek

- [x] T20 `FimBicimi` + `FimPromptKurucu` → beş biçim için doğru belirteçler; `KAPALI` ve `SOHBET` istisna fırlatır (9 test)
- [x] T21 `OneriTemizleyici` → kod çiti ayıklanır, ilk satırdan sonrası atılır, kaçan belirteçler kesilir
- [x] T22 `OneriTemizleyici` sonek tekrarı → tam ve kısmi örtüşme kırpılıyor **(mutasyonla doğrulandı)**
- [x] T23 Boş/anlamsız çıktı → `null` döner, öneri gösterilmez (6 test)

## İstemci

- [x] T30 `LlmClient.complete()` → `/v1/completions`'a ham prompt gider; `messages` gönderilmediği ayrıca doğrulanıyor
- [x] T31 Zaman aşımı → sunucu susarsa 800 ms'de kesiliyor (süre ölçülerek doğrulandı)
- [x] T32 Hatalar tipli → 500/401/bağlantı/bozuk gövde ayrı `LlmError`'lara düşüyor
  - Not: `null`'a çevirme **sağlayıcı katmanında** yapılacak (T50), istemcide değil — bkz. Notlar

## Ayarlar

- [x] T40 Beş yeni ayar alanı → varsayılan **KAPALI**; XML'e yazıldığı ve dokunulmamışken yazılmadığı doğrulandı
- [x] T41 Ayar ekranında "Satır içi tamamlama" bölümü → biçim açılır listesi, ayrı model, gecikme, zaman aşımı
  - Elle doğrulama T90'a bırakıldı (`runIde`).
- [x] T42 Tamamlama modeli boşsa sohbet modeline düşer; ikisi de boşsa özellik açılmaz

## Sağlayıcı — ilk kez öneri görünür

- [x] T49 `TamamlamaBaglami` (planda yoktu) → imleç çevresini bütçeye indirir, satır sınırına yuvarlar (13 test)

- [x] T50 `AcpInlineCompletionProvider` + kayıt → EP'ye bağlandığı testle doğrulandı **(mutasyonla)**
  - ✅ *2026-09-04* `runIde` ile gerçek modelde öneri görüldü — uçtan uca çalışıyor.
- [x] T51 `isEnabled` kapalıyken **sunucuya paket gitmediği** sahte sunucuyla kanıtlandı
- [x] T52 Öneri tek tuşla kabul ediliyor ve **tek geri alma adımıyla** geri alınıyor ✅ *2026-09-04, runIde*
- [x] T53 Yazmaya devam edilince öneri kayboluyor, bayat öneri kalmıyor ✅ *2026-09-04, runIde*
- [x] T54 IDE'nin kendi tamamlama listesi açıkken öneri onu bozmuyor ✅ *2026-09-04, runIde*

## Sağlamlaştırma

- [x] T60 Yazma akıcılığı → kullanıcı hızlı yazarken gecikme hissetmedi ✅ *2026-09-04*
- [x] T61 Hatalar sessiz → sunucu kapalı / 500 / zaman aşımı / anlamsız yanıt, dördü de sessizce boş öneri (5 test)
- [x] T62 Kullanıcının kodu günlüğe yazılmıyor ✅ *2026-09-04* — bkz. Notlar
- [x] T63 `./gradlew verifyPlugin` → üç IDE sürümünde **Compatible**, dahili API kullanımı **0** ✅ *2026-09-04*
  - `lang.impl` hakkında hiç bulgu yok — plandaki risk abartılmış (bkz. Notlar).

## Kullanıcı bulgusu — öneri kalitesi

- [x] T70 Üye erişiminden (`.`, `::`, `->`) sonra öneri üretilmez — alan IDE'nin
- [x] T71 Ondalık sayıdaki nokta üye erişimi sayılmaz (`3.14`) — testle

## Doğrulama ve kapanış

- [x] T90 `spec.md` kabul kriterleri `runIde` ile elle doğrulandı ✅ *2026-09-04*
- [x] T91 `spec.md` durumu "Uygulandı" yapıldı
- [x] T92 `AGENTS.md` güncellendi — mimari, EP adı uyarısı, FIM doğrulama komutu, 4. build tuzağı

---

## Notlar

Uygulama sırasında ortaya çıkan sapmalar, kararlar ve nedenleri.
Plandan sapıldıysa **neden** sapıldığı buraya yazılır.

### T10 — FIM doğrulandı *(2026-09-04)*

Kullanıcının modeli (`qwen/qwen3.8-27b`) Qwen biçimini destekliyor. Ayırt edici kanıt yalnızca
çıktının doğru görünmesi değil, **`matched_stop: 248044`** alanı: model özel bir
belirteçte durdu, yani FIM belirteçlerini tanıyor. Çıktı düz metin gibi
üretilseydi bu alan boş kalırdı.

Yan bulgu: `Authorization: Bearer` çalışıyor. Eklentinin mevcut kimlik başlığı
doğru; spec 001'de açık kalan "başlık biçimi ayarı gerekir mi" sorusu **kapandı**,
ayar eklenmiyor.

### T20-T23 — `temizle` imzası plandan farklı

Plan `temizle(hamYanit, imlecSonrasiSatir)` diyordu; uygulamada araya `bicim`
parametresi girdi. Sebep: kaçan belirteç listesi biçime bağlı — DeepSeek'in
`<|EOT|>`'si Qwen'de yok, Qwen'in `<|fim_pad|>`'i DeepSeek'te yok. Biçimi
bilmeden temizlemek ya eksik keser ya da başka modelin belirtecini kullanıcının
kodundan siler.

### T30-T32 — sahte sunucu mu, canlı sunucu mu

Kullanıcı canlı sunucu (OpenRouter) sordu. Karar: **ikisi de**, farklı işler için.

T31 ve T32 canlı sunucuya taşınamaz, çünkü tam olarak sunucunun *bozulmasını*
test ediyorlar: 500 dönmesi, susması. Gerçek sunucudan bunlar istenemez — düzgün
çalıştığı sürece o testler hiçbir şey doğrulamaz, bozulduğunda ise ilgisiz bir
sebepten kırmızıya döner.

Buna karşılık `CanliSunucuTest`'e iki test eklendi:
- `gercek sunucuda ham tamamlama ucu calisiyor` — taşıma katmanı; gövde
  biçimimizi gerçek bir ağ geçidi kabul ediyor mu
- `FIM destegi gercek modelde dogrulanir` — **T10'un tekrar koşulabilir hâli**

İkincisi asıl kazanç: GLM/Kimi'ye geçildiğinde `ACP_FIM_*` değişkenleri
tanımlanıp koşulur. Üç ayrı belirtiye bakıyor (belirteç sızıntısı, sonek
tekrarı, token sayısı) çünkü yalnızca çıktıya bakmak yanıltıcı: model tesadüfen
doğru şeyi yazmış olabilir.

### `matched_stop` çözülüyor

`CompletionResult.matchedStop` eklendi. Gerekli değil ama tanı için değerli:
T10'da FIM'in çalıştığını asıl bu alan kanıtladı. SGLang sayı, bazı sunucular
metin döndürüyor; ikisi de kabul ediliyor.

### Hata → `null` dönüşümü nerede

Plan "hata durumunda `null`" diyordu; `complete()` yine de tipli `LlmError`
fırlatıyor. Sebep: istemci saf ve genel amaçlı — hatayı yutarsa *neden* öneri
gelmediği hiçbir yerden anlaşılamaz. Yutma işi, sessizliğin gerçekten
istendiği yerde (satır içi sağlayıcı, T50) yapılacak.

### T70 — model tip bilmiyor, IDE biliyor

Kullanıcı bulgusu: `int toplam` için model `toplam.toString()` önerdi. Java'da
ilkel tipin `toString()`'i yok — öneri **derlenmiyor**.

Kök neden bizim kodumuzda değil: modele yalnızca metin gidiyor, tip bilgisi
gitmiyor. Model "nokta geldiyse metot çağrısı gelir" diye devam ediyor.
Bağlamı büyütmek çözmez; bu bir tip sorunu.

Ama IDE `toplam`'ın `int` olduğunu **biliyor** ve tam orada kendi listesini
açıyor. Yani noktadan sonra ikimiz yarışıyoruz ve bizim kaybetmemiz gereken bir
yarış. Çözüm: üye erişiminden sonra istek bile göndermemek. Spec'in "IDE'nin
kendi kod tamamlamasının yerini almak" kapsam dışı maddesiyle de tutarlı.

Bu, öneri kalitesinin **tamamını** düzeltmez — model zayıfsa başka yerlerde de
kötü öneri üretir. Yalnızca en kötü olduğumuz ve IDE'nin en iyi olduğu noktayı
kapatıyor.

### T71 — kendi testim yanlış yazılmıştı

Ondalık sayı testini önce `"double x = 3|"` diye yazdım — o konumda nokta bile
yok, yani test hiçbir şeyi doğrulamıyordu ve yeşil geçti. Gerçek durumu
(`3.1|`) yazınca kırmızıya döndü: kod ondalık sayı yazarken de öneriyi
bastırıyordu.

"Test yeşil, davranış bozuk" kalıbının bu spec'teki üçüncü örneği — ve bu sefer
yanlış olan testin kendisiydi.

### T63 — doğrulayıcı bu projede hiç çalışmamış

İlk koşuda `verifyPlugin` başarısız oldu ama uyarı listesi **boştu**. Sebep:
`plugin.xml`'de `<description>` yoktu, doğrulayıcı eklentiyi "geçersiz yapı"
sayıp hiçbir kontrol yapmadan durdu. "Uyarı yok" ile "kontrol çalışmadı"
dışarıdan aynı görünüyor — spec 004'ten beri böyleymiş.

İkinci tuzak: açıklamanın **başı ASCII olmalı**. Doğrulayıcı Türkçe
diyakritikleri (ü, ı, ş) Latin harfi saymıyor. Deneyle doğrulandı: yalnızca ilk
satırdaki "ü" kaldırılınca kontrol geçti. Bu yüzden açıklamanın ilk cümlesi
İngilizce — projenin Türkçe kuralından bilinçli sapma, gerekçesi
`build.gradle.kts`'te.

**Bulunanlar:**

| Bulgu | Nereden | Çözüm |
|---|---|---|
| `<description>` eksik | spec 004 | eklendi |
| `JBFont.smallOrNewUiMedium()` — dahili, ×4 | spec 002 | `JBFont.medium()` |
| `SimpleListCellRenderer.create` — kaldırılacak | **spec 006, T41** | `textListCellRenderer` |
| `ToolWindowFactory` deneysel/eski (10) | spec 002 | bırakıldı, build'i düşürmüyor |

**Plandaki risk yanlış çıktı.** Plan `InlineCompletionProvider` için "lang.impl
içinde, kararlılığı platform API'si kadar yüksek değil" diyordu. Doğrulayıcı bu
API hakkında **tek bulgu üretmedi** — ne dahili ne deneysel sayıyor.

Buna karşılık T41'de yazdığım renderer, kaldırılmak üzere işaretli bir API
kullanıyordu: derleyici susmuş, testler geçmiş, özellik çalışmıştı. Risk,
tahmin edilen yerde değil başka yerde çıktı — T63'ün varlık sebebi tam bu.

### T61 — hata yutmanın fazla geniş olmadığının kanıtı

Dört "kötü gün" testi (`Empty` bekleyen) tek başına yanıltıcı olurdu: **her
koşulda** `Empty` döndüren bozuk bir uygulama da hepsini geçerdi. Bu yüzden
beşinci test karşıt yönde: geçerli yanıtta öneri **üretildiğini** doğruluyor.

Testlerde `runBlocking` yerine `PlatformTestUtil.waitWithEventsDispatching`
kullanıldı. Sağlayıcı belge okumak için okuma eylemi istiyor; EDT bloke
edilirse test kilitlenir — bu tuzağa spec 001'de yakalanmıştık.

### T62 — kod günlüğe sızmıyor, ve neden

Tamamlama yolunda tek günlük çağrısı var ve yalnızca `e.javaClass.simpleName`
yazıyor — istisnanın **mesajını değil**. Fark önemli: mesaj yazılsaydı,
sunucunun 400 yanıtı gönderdiğimiz prompt'u (yani kullanıcının kodunu)
yankılıyor olabilirdi.

Sohbet tarafında `ChatSessionService` istisnayı olduğu gibi günlüğe veriyor
(`LOG.warn(..., hata)`), ama orası da güvenli — çünkü `LlmError` sunucu
gövdesini `body` alanında **tutuyor, mesaja koymuyor**. Mesajlar sabit Türkçe
metinler. Bu, spec 001'de verilmiş bir karardı ve burada karşılığını verdi.

Yani kural şu: *`LlmError.body` hiçbir zaman günlüğe geçirilmemeli.*

### Çok satır isteği — ayrı spec'e

Kullanıcı 2026-09-04'te "fonksiyon gibi kod tamamlama" istedi. 006'ya
eklenmedi: tek satır, spec'in Belirsizlik 4'ünde bilinçli olarak verilmiş bir
karardı ve çok satır ayrı bir problem kümesi (önerinin nerede biteceği,
girinti hizalaması, kısmi kabul). Projenin "her konu kendi spec'ini alır"
kuralı gereği `specs/README.md`'de planlanan spec olarak kaydedildi.

### `runIde` tuzağı — sandbox açıkken çalıştırmayın

Sandbox açıkken `runIde` çalıştırıldı. Sandbox jar'ın değiştiğini fark edip
kendini yeniden başlatmaya kalktı, ama yeniden başlatıcı IDE'yi **sandbox
parametreleri olmadan** çalıştırdı (`restarter.log` kanıtı). Sandbox kapandı,
yerine geliştiricinin normal IDE'si açıldı.

Teşhis yanıltıcı: "ayar görünmüyor" diye eklentide hata arandı, oysa yanlış
IDE'ye bakılıyordu. `ps` ile ayırt edilemiyor — ikisi de aynı `idea` binary'si.
Kesin kontrol `lsof -p <pid> | grep intellijPlatform/sandbox`.
`AGENTS.md`'ye üçüncü build tuzağı olarak yazıldı.

### T50 — EP adı: plandaki tahmin YANLIŞTI

Plan `inlineCompletionProvider` diyordu. Gerçek ad **`inline.completion.provider`**;
IDE'nin `EditorExtensionPoints.xml` dosyasından okundu.

Bu, bağlam sağlayıcıda yediğimiz hatanın aynısı olacaktı. Kritik nokta: yanlış
adla **IDE hata vermiyor**, sessizce hiçbir şey kaydetmiyor. Eklenti yükleniyor,
ayarlar görünüyor, ama sağlayıcı hiç çağrılmıyor.

`testSaglayiciUzantiNoktasinaKayitli` bunun için var ve mutasyonla doğrulandı:
adı bozunca beş test birden kırmızıya döndü ve mesaj doğrudan nedeni söylüyor.

### T50 — kayıt neden isteğe bağlı modülde

Uzantı noktası platform seviyesinde tanımlı, ama uyguladığımız sınıflar
(`DebouncedInlineCompletionProvider`, `InlineCompletionGrayTextElement`)
`intellij.platform.lang.impl` içinde. Koşulsuz kayıt, `lang` bulunmayan bir
üründe eklentinin **tamamen** yüklenememesine yol açardı.

Bu yüzden kayıt `acp-completion.xml`'e alındı ve
`<depends optional="true">com.intellij.modules.lang</depends>` ile bağlandı.
Dört hedef IDE'de de `lang` var; bu, işlev kaybettirmez, yalnızca sohbetin
tamamlama yüzünden çökmemesini garanti eder.

### T49 — planda olmayan bir parça: `TamamlamaBaglami`

Plan bağlam kırpmayı hiç konuşmamıştı; `Truncator`'ın yeterli olacağı örtük
varsayımı vardı. Değil: `Truncator` dosyanın **ortasını** atıp baş ve sonu
korur. FIM'de tam tersi gerekiyor — imlecin **yakını** en değerli bilgi.
Dosya başındaki import satırları, imlecin bir üstündeki satır kadar işe yaramaz.

### T40-T42 — `tamamlamaAcikMi()` tek karar noktası

Sağlayıcının "istek göndereyim mi" kararı tek bir yerde toplandı. Dağıtılsaydı
(biçim kontrolü sağlayıcıda, model kontrolü istemcide) birinin unutulması
sessizce kod göndermeye yol açardı — geri alınamaz bir hata sınıfı.

Biçim `acp.xml`'e **enum değil metin** olarak yazılıyor. Sebep: ileride bir
biçim adı değişir veya kaldırılırsa eski dosyayı okurken çökmemeli. Tanınmayan
değer `KAPALI`'ya düşüyor — en güvenli taraf.

### İkinci mutasyon testi

`fimBicimi`'nin geri düşüş değeri `KAPALI` yerine `QWEN` yapıldı: iki test
kırmızıya döndü. Bu, kullanıcının fark etmeden kod göndermesine yol açabilecek
tek satırlık bir hata; testin gerçekten koruduğu doğrulandı.

### Mutasyon testi

Sonek kırpma, "test yeşil davranış bozuk" riskinin en yüksek olduğu yer:
kırpma çalışmasa bile öneri *makul görünür*, sadece kabul edilince kod iki kez
yazılır. Bu yüzden `sonekTekrariniKirp` geçici olarak no-op yapılıp testler
koşuldu — **4 test kırmızıya döndü**. Testler gerçekten koruyor.
