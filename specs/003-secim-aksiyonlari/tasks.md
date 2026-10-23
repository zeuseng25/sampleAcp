# Görevler: Seçim Aksiyonları

- **Spec no:** 003 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Prompt şablonları

- [x] T10 [P] Dört şablon + üç dile özgü varyant (kt/py/ts) → hepsi pakette, testle doğrulandı ✅
- [x] T11 [P] `PromptSelector` → dile özgü varsa o, yoksa genel; şablon hiç yoksa yüksek sesle patlıyor ✅ *8 test*

## Açıkla — en basit yol, menü ve akış kurulur

- [x] T20 `BaseSelectionAction` → seçim yokken pasif ve çalışmıyor; seçim varken etkin ✅
- [x] T21 Dört eylem + grup `plugin.xml`'de kayıtlı, metinler bundle'dan ✅ *görsel doğrulama bekliyor*
- [x] T22 Sonuç panelde akıyor; panel kapalıysa `activate()` ile açılıyor ✅ *görsel doğrulama bekliyor*
- [x] T23 Doğru şablon gidiyor; `.py` için pytest şablonu seçiliyor ✅ *testle*
- [x] T24 Eylemler `ActionManager`'da kayıtlı (Keymap'te görünmesinin ön şartı) ✅ *görsel doğrulama bekliyor*

## Hedef sabitleme — veri kaybı riski burada kapanır

- [x] T30 `TargetAnchor` → `RangeMarker` + metin karşılaştırması; öncesine/sonrasına yazmak bayatlatmıyor ✅ *7 test*
- [x] T31 Bayatlık koruması → kullanıcının yazdığı ezilmiyor, `Sonuc.Bayat` dönüyor ✅ *testle*

## Uygulama

- [x] T40 `diffIstegiKur` + `onizle` ayrıldı — istek kurulumu pencere açmadan test edilebiliyor ✅ *görsel doğrulama bekliyor*
- [x] T41 `uygula` → değişiklik yazılıyor, **tek** geri alma adımı eski hâle döndürüyor ✅
- [x] T42 Geri alma adı → listede "ACP: ..." görünüyor ✅
- [x] T43 Biçimlendirme yazma ile **aynı** `WriteCommandAction` içinde → geri alma ikisini birlikte alıyor ✅
- [x] T44 Salt okunur dosya → `Sonuc.SaltOkunur`, içerik değişmiyor ✅
- [x] T45 Kod bloğu yoksa `kodBlogunuAyikla` null dönüyor → uygulama önerilmeyecek ✅ *4 test*

## Kalan üç eylem

- [x] T50 `RefactorSelectionAction` → çapa yakalanıyor, uygulanabilir sonuç üretiliyor, onaysız dosya değişmiyor ✅
- [x] T51 `AddDocsAction` → aynı zincir (`editoruDegistirir` üzerinden) ✅
- [x] T52 `GenerateTestAction` → mevcut dosya değişmiyor, sonuç geçici dosyada açılıyor ✅
- [x] T53 Dile duyarlılık → `.py` için pytest şablonu (T23'te doğrulandı) ✅

## Alt+Enter ve sağlamlaştırma

- [x] T60 `AcpIntentionAction` → Alt+Enter'da **tek** giriş; seçim yoksa görünmüyor; `startInWriteAction() = false` ✅ *5 test*
- [x] T61 Ağ çağrısı yazma kilidi altında değil (`startInWriteAction=false`), akış arka planda ✅ *görsel doğrulama bekliyor*
- [x] T62 "Açıkla" dosyayı değiştirmiyor, uygulama da önermiyor ✅

## Sonradan çıkanlar

- [x] T70 Alt+Enter `description.html` → IntelliJ her intention için `intentionDescriptions/<SınıfAdı>/description.html` bekliyor; yoksa önizleme SEVERE ile çöküyor ✅ *testle*
- [x] T71 Önizleme penceresi uygulandıktan sonra kapanıyor → `DiffDialogHints` ile pencere tutamağı alınıyor ✅
- [x] T72 Akışta boşta kalma gözcüsü → sunucu susarsa `LlmError.Timeout(IDLE)`; gelen metin korunuyor; heartbeat'ler boşta saymıyor ✅ *3 test, ayardan değiştirilebilir*

## Doğrulama ve kapanış

- [x] T90 Kabul kriterlerinin tamamı `runIde` ile elle doğrulandı ✅ *2026-09-04*
- [x] T91 `spec.md` durumu "Uygulandı" ✅
- [x] T92 `AGENTS.md` durum tablosu güncellendi ✅

---

## Notlar

### Alt+Enter için description.html zorunlu (2026-09-04)

Kullanıcı Alt+Enter'ın çalışmadığını bildirdi. Log:

```text
SEVERE — Intention Description Dir URL is null: AcpIntentionAction;
         while looking for description.html
```

IntelliJ her intention için `intentionDescriptions/<SınıfAdı>/description.html`
bekliyor. **Testler göremedi** çünkü `myFixture.availableIntentions` bu dosyayı
aramıyor; hata yalnızca kullanıcı Alt+Enter'a basıp önizleme hesaplanırken çıkıyor.
Kaynağa doğrudan bakan bir test eklendi.

Bu, oturumdaki üçüncü "test yeşil, davranış bozuk" vakası (diğerleri: sessiz
bağlam yakalayıcısı, yalnızca yatay sınır kontrol eden yerleşim testi).

### Zaman aşımı semantiği ve boşta kalma gözcüsü (2026-09-04)

`readTimeoutMs` (180 sn) `HttpRequest.timeout()`'a gidiyor ve bu **toplam süre
değil, yanıt başlığına kadar geçen süre**. Akışlı yanıtta sunucu başlıkları hemen
gönderdiği için uzun üretimler (araç çağırma vb.) kesilmiyor — bu iyi.

**Boşluk kapatıldı (T72):** akış üzerinde bir gözcü coroutine'i son veri anını
izliyor; `idleTimeoutMs` (varsayılan 120 sn, ayardan değiştirilebilir) boyunca
hiç veri gelmezse gövde kapatılıyor ve `LlmError.Timeout(IDLE)` üretiliyor.

Okuma bloke olduğu için kesmenin tek yolu gövdeyi kapatmaktı; bloke okuma
`IOException` ile düşüyor ve gözcü bayrağına bakarak bunun ağ hatası değil
zaman aşımı olduğu anlaşılıyor.

Gözcü **sessizliği** ölçüyor, toplam süreyi değil. Bir test bunu ayrıca
koruyor: 1 sn boşta kalma eşiğiyle 1,5 sn süren normal bir akış kesilmemeli.
Vekil sunucuların gönderdiği heartbeat yorum satırları da bağlantıyı canlı
sayıyor (delta üretmeseler bile).


### Alt+Enter'da tek giriş (2026-09-04)

Dört ayrı intention yerine tek giriş konuldu; seçilince dört eylemi içeren küçük
bir liste açılıyor. Alt+Enter listesi zaten kalabalık, dört satır daha eklemek
kullanıcıyı boğardı.

Test bu sırada bir platform davranışı öğretti: IntelliJ kendi **"Disable 'ACP'"**
girişini otomatik ekliyor. Beklenen davranış; test onu saymayacak şekilde
düzeltildi.

### İki giriş noktası tek yoldan geçiyor

Sağ tık menüsü ve Alt+Enter, `SecimEylemi.calistir` üzerinden aynı mantığı
kullanıyor. Ayrı ayrı yazılsalardı çapa yakalama gibi kritik adımlar birinde
unutulabilirdi.


### "Uygula" bağlantısı dar panelde kırpılıyordu (2026-09-04)

Kullanıcı ekran görüntüsüyle bildirdi: şeritte "Önizle" görünüyor, "Uygula"
görünmüyordu.

**Sebep:** şerit `FlowLayout` kullanıyordu. Araç penceresi ~280 piksel; etiket +
iki bağlantı tek satıra sığmayınca FlowLayout ikinciyi alt satıra sardı, ama
şeridin yüksekliği tek satıra göre hesaplandığı için bağlantı kırpıldı.

**Düzeltme:** `BorderLayout`. Bağlantılar DOĞU'da (yerlerini önce alır), açıklama
etiketi MERKEZ'de. Dar alanda kaybolması gereken şey etiket, düğmeler değil.

**Test hakkında bir not:** ilk yazdığım regresyon testi yalnızca *yatay* sınırı
kontrol ediyordu ve **eski kodla da geçiyordu** — yani hiçbir şey korumuyordu.
Dikey sınır kontrolü eklenip düzeltme geçici olarak geri alınarak testin
gerçekten kırmızıya döndüğü doğrulandı:

```text
Uygula dikeyde kabının dışına taşmış — kırpılır (y=25+16 > 25)
```

Ders: yazılan regresyon testinin eski kodla **kırmızıya döndüğü** görülmeden
"koruma eklendi" denemez.


### Scratch dosyası EDT gerektiriyor (2026-09-04)

"Test yaz" testi zaman aşımına uğradı. Sebep: `ScratchRootType.createScratchFile`
ve editör açma **EDT** gerektiriyor, ama `sonucuDegerlendir` servis coroutine'inde
(arka planda) çalışıyordu. Fırlayan istisna `finally` bloğunu bozunca akış hiç
"bitti" durumuna geçemedi ve panel takılı kalırdı.

İki düzeltme:
1. `tamamla` artık **önce durumu kapatıyor**, sonra sonucu değerlendiriyor —
   değerlendirmede hata çıksa bile akış bitmiş sayılır.
2. Scratch işi `Dispatchers.EDT`'ye alındı ve `runCatching` + `LOG.warn` ile
   sarıldı.

Birincisi genel bir ders: `finally` içinde riskli iş yapılmaz.


Uygulama sırasında ortaya çıkan sapmalar, kararlar ve nedenleri.
Plandan sapıldıysa **neden** sapıldığı buraya yazılır.
