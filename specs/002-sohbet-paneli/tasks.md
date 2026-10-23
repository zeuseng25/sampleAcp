# Görevler: Sohbet Paneli

- **Spec no:** 002 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Genişletildi — 2026-09-04, biçimlendirme (T100+)

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Durum ve servis

- [x] T10 `ChatUiState` + `ChatMessageUi` + `ChatError` → değişmez tipler ✅
- [x] T11 `ChatSessionService.send()` → soru gönderilince yanıt akıyor ve tamamlanıyor ✅ *(gerçek istemci zincirinden, sahte sunucuya karşı)*
- [x] T12 `ChatSessionService.cancel()` → akış duruyor, gelen metin kalıyor, hata gösterilmiyor ✅
- [x] T13 Konuşma bağlamı → ikinci soruda önceki soru ve yanıt da gönderiliyor ✅
- [x] T14 Ayarlar eksikken `send()` → istek gitmiyor, ayarlara yönlendiren hata çıkıyor, "yeniden dene" sunulmuyor ✅
- [x] T15 `retry()` → yarım yanıt siliniyor, son soru baştan gönderiliyor, mesaj sayısı artmıyor ✅

## Düz metin panel — ilk kez sohbet edilebilir

- [x] T20 `ChatView` arayüzü + `ChatViewAction` ✅
- [x] T21 `SwingChatView`: transcript + giriş + Gönder; Enter gönderir, Shift+Enter satır atlar ✅
- [x] T22 Akış panele bağlandı → yanıt yazılırken görünüyor ✅
- [x] T23 İptal düğmesi → akışta görünüyor, basınca duruyor, metin kalıyor ✅
- [x] T24 `coalesceText` bağlandı ✅
- [x] T25 Hata şeridi + "Yeniden dene" ✅

## Geçmiş

- [x] T30 Panel kapanıp açılınca konuşma yerinde ✅ *(durum serviste; T31 testiyle de doğrulanıyor)*
- [x] T31 Panel dispose edilince konuşma serviste kalıyor, dinleyici çağrılmıyor ✅ *testle*

## Markdown ve kod blokları

- [x] T40 [P] `MarkdownFenceSplitter` → kapanmamış blok, `~~~`, iç içe çit, girintili çit ✅ *14 test*
- [x] T41 `CodeBlockComponent` → IDE'nin gerçek editör renklendirmesi; 25 dil etiketi eşlemesi ✅
- [x] T42 Kopyala bağlantısı → kod bloğunun üst barında ✅ *görsel doğrulama bekliyor*
- [x] T43 Editör sızıntısı → dispose sonrası sayı başlangıca dönüyor; yeniden çizimde birikmiyor; akışta hiç yaratılmıyor ✅ *4 test*
- [x] T44 Editör tavanı → 30 mesajda ≤20 canlı editör; eskiler sade tek aralıklı metne düşüyor ✅ *testle*

## Düşünme bölümü

- [x] T50 `ReasoningSection` → ayrı, katlanmış, varsayılan kapalı; açılan bölüm akış sürerken açık kalıyor ✅

## Kaydırma ve tema

- [x] T60 Otomatik kaydırma → yalnızca zaten dipteyse kaydırıyor ✅ *(öne alındı)*
- [x] T61 Tema uyumu → `LafManagerListener` + `EditorColorsManager.TOPIC`; bileşenler baştan kuruluyor ✅ *görsel doğrulama bekliyor*

## Bağlam

- [x] T70 `ContextProvider` uzantı noktası (`dynamic=true`) + `CompositeContextProvider` bütçe uygulaması ✅ *5 test*
- [x] T71 [P] `TokenEstimator` + `Truncator` → ortadan kırpma, satır sınırında, işaretli ✅ *8 test*
- [x] T72 `SelectionContextProvider` → seçim + açık dosya öğeleri; editör anlık görüntüsü EDT'de alınıyor ✅ *(SURROUNDING öğesi elendi — bkz. Notlar)*
- [x] T73 `PromptBuilder` → öğeler tek kullanıcı mesajına çevriliyor; saf, IDE'siz ✅
- [x] T74 Bağlam rozeti → soru mesajının üstünde dosya adı ve satır aralığı ✅ *görsel doğrulama bekliyor*
- [x] T75 Dosya açık değilken → sağlayıcı boş liste döner, soru bağlamsız gider; bağlam toplama hatası da soruyu engellemiyor ✅

## Doğrulama ve kapanış

- [x] T90 Kabul kriterlerinin tamamı `runIde` ile elle doğrulandı ✅ *2026-09-04*
- [x] T91 `spec.md` durumu "Uygulandı" ✅
- [x] T92 `AGENTS.md` durum tablosu güncellendi ✅

---

## Notlar

### Kopyala düğmesi — kod hatası değildi (2026-09-04)

Kullanıcı kopyalamanın çalışmadığını bildirdi. Üç testle ölçüldü (bağlantı var mı,
tıklama panoya yazıyor mu, çok satırlı kod tam geçiyor mu) ve **mekanizma
çalışıyordu**.

En olası açıklama zamanlama: kod blokları yalnızca **akış bittiğinde** çiziliyor;
akış sırasında düz metin gösteriliyor ve orada Kopyala bağlantısı yok. Akış
sürerken tıklanmışsa tıklanacak bir şey yoktu.

Yine de bir eksik vardı: **geri bildirim yoktu.** Kullanıcı tıklamanın kaydedilip
kaydedilmediğini panoya bakmadan anlayamıyordu. Bağlantı artık 1,5 saniye
"Kopyalandı" gösteriyor.


### HATA — bağlam hiç toplanmıyordu, savunma bunu gizledi (2026-09-04)

Kullanıcı fark etti: model seçili kodu göremiyordu.

**Kök sebep:** uzantı noktasının tam adı `<eklenti kimliği>.<name>`. `plugin.xml`'de
`name="contextProvider"` ve kimlik `dev.acp.assistant` olduğu için gerçek ad
`dev.acp.assistant.contextProvider`; kodda ise `dev.acp.contextProvider` aranıyordu.

**Neden fark edilmedi:** `baglamiEkle` içindeki `runCatching { }.getOrElse { emptyList() }`
istisnayı yuttu. Niyet doğruydu (bağlam toplanamazsa soru yine gitmeli) ama
**sessiz** yutmak yanlıştı. 162 test yeşil kaldı, hatayı kullanıcı buldu.

**Düzeltmeler:**
1. EP adı düzeltildi, koda uyarı yorumu eklendi.
2. Yakalayıcı artık `LOG.warn` ile kaydediyor — yutuyor ama susmuyor.
3. `ContextKaydiTest`: uzantı noktası çözülemezse test patlar.
4. `ChatSessionServiceTest.testAcikDosyaVeSecimModeleGider`: sahte sunucuya giden
   gövdede dosya içeriğinin ve seçimin bulunduğunu doğrular. Doğrudan regresyon testi.

**Ders:** "bir kolaylık, zorunluluk değil" diye konan her yakalayıcı, gizlediği
şeyi loglamalı. Aksi hâlde özellik hiç çalışmasa da her şey yolunda görünür.

### Test kilitlenmesi — EDT (2026-09-04)

EP düzelince testler takıldı: bağlam toplama editörü okumak için EDT'ye geçiyor,
`runBlocking` ise EDT'yi bloke ediyor. **Üretimde sorun yok** (servis arka planda
çalışıp EDT'ye normal şekilde geçer); sorun testin EDT üzerinde koşmasından.

`PlatformTestUtil.waitWithEventsDispatching` ile beklerken olay kuyruğu döndürülüyor.


### Bağlam katmanında iki küçük sapma (2026-09-04)

**1. Tipler `dev.acp.llm.prompt` altında.** Plan `ContextItem`'ı `dev.acp.context`
altında öngörüyordu. Oraya konsaydı saf olan `PromptBuilder`, IDE'ye bağlı bir
paketten tip almak zorunda kalırdı — katmanlama tersine dönerdi. Şimdi saf tipler
`llm.prompt`'ta, IDE'ye bağlı sağlayıcılar `context`'te; bağımlılık yönü doğru.

**2. `SURROUNDING` öğesi elendi.** Plan seçim çevresini ayrı bir öğe olarak
öngörüyordu. Açık dosyanın tamamı zaten gönderiliyor ve bütçe aşılırsa **ortadan**
kırpılıyor (başı ve sonu korunarak), dolayısıyla ayrı bir çevre öğesi v1'de değer
katmıyordu. Gerekirse sonradan eklenir — uzantı noktası zaten hazır.

### Bağlam bir kolaylıktır, zorunluluk değil

`baglamiEkle` hata yakalarsa boş listeye düşüyor ve soru yine gidiyor. Bağlam
toplama IDE durumuna bağlı (dosya kapanmış, editör yok, okuma kilidi); bunun
sohbeti tamamen engellemesi kabul edilemezdi.


### `coalesceText`'te gerçek hata yakalandı (2026-09-03)

T-listesinde olmayan bir kusur, T15'in kardeşi olan "yarıda kesilen akış" testiyle
ortaya çıktı: `coalesceText` tamponu **yalnızca akış normal bitince** boşaltıyordu.
Akış hata fırlatarak bittiğinde (`StreamTruncated`) tamponda bekleyen metin
sessizce yok oluyordu — spec ise "yarıda kesilen akışta gelen metin korunur"
diyor.

Düzeltildi: `collect` try/catch içine alındı; hata durumunda tampon boşaltılıp
hata yeniden fırlatılıyor. İptalde **boşaltılmıyor** — toplayıcı zaten gitmiş
olduğu için `emit` hata verirdi.

Kusur 001'de yazılmıştı ama oradaki testler onu göremiyordu: `OpenAiLlmClient`
testleri `coalesceText`'ten geçmiyor. Ancak servis katmanı ikisini birleştirince
görünür oldu.


---

## Metin biçimlendirme — 2026-09-04'te eklendi

Kullanıcı bildirdi: kod blokları render ediliyor ama düz metin ham Markdown
olarak görünüyor (`###`, `**kalın**`, `-` listeleri ekranda okunuyor).

Bu bir gerileme değil, **hiç yapılmamış bir iş**: ilk H2 yalnızca kod bloklarını
vaat ediyordu. Ayrı spec yerine buraya eklendi (kullanıcı kararı), çünkü H2'nin
amacını tamamlıyor.

- [x] T100 `MarkdownToHtml` — saf dönüştürücü, 22 test
  - Alt küme: başlık, kalın, italik, satır içi kod, madde/numaralı liste, yatay çizgi
  - **Model çıktısı HTML olarak kaçırılır** — yanıtta `<div>` geçmesi görünümü bozmamalı
- [x] T101 Kapanmamış işaret metni yutmuyor; harflerin kaybolmadığı ayrıca test ediliyor
- [x] T102 Metin blokları `HTMLEditorKitBuilder` ile çiziliyor; sabit renk yok
- [x] T103 Tema ve yazı tipi uyumu → `runIde`'de doğrulandı ✅ *2026-09-04*
- [x] T104 Akış sırasında düz metin, bitince biçimlendirme ✅ *2026-09-04*
- [x] T105 Mevcut testler değiştirilmeden geçti — 385 = 363 + 22

### Notlar — 2026-09-04 biçimlendirme

**İki gerçek hata testlerle çıktı:**

1. **Sıralama.** Satır içi kodu `<code>` yapıp sonra `*` değişimini çalıştırmak
   yetmiyordu: yıldızlar `<code>` etiketinin *içinde* kalıyor ve italik değişimi
   onları yakalıyordu — `` `a*b*c` `` → `<code>a<i>b</i>c</code>`. Kod içeriği
   artık biçimlendirmeden **çıkarılıp** sonda geri konuyor.
2. **Boş girdi** `<br>` üretiyordu; artık boş dönüyor.

**Bir testim fazla katıydı:** "hiçbir karakter kaybolmaz" derken rakamları da
sayıyordum, oysa `1.` liste işareti **kasten** kayboluyor — `<ol>` numarayı
kendisi üretiyor. Test harflerle sınırlandı.

**Akış sorunu yok:** panel zaten iki aşamalı çalışıyordu — akış sırasında düz
metin, yanıt bitince bloklara ayırma. Biçimlendirme yalnızca ikinci aşamada
uygulanıyor, yarım gelmiş `**` işareti hiç render edilmiyor.

**Kaçırma kritik:** model çıktısında `<div>` veya `i < n` geçmesi sıradan.
Kaçırma **önce**, biçimlendirme sonra yapılıyor; tersi olsaydı ürettiğimiz
`<b>` etiketleri de kaçırılıp ekranda `&lt;b&gt;` görünürdü.
