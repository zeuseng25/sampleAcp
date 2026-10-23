# Görevler: Çok Dosyalı Düzenleme

- **Spec no:** 008 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı — *2026-10-22* (T11 altyapı bekliyor)

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Ölçüm — her şeyden önce

- [x] T10 **Gerçek depoda döngü ölçüldü — sistem yönergesi belirleyici** ✅ *2026-09-04*
  - Bu deponun kendisinde gerçek bir çok dosyalı değişiklik (`UIUtil.getContextHelpForeground()`
    → `JBColor.GRAY`, 9 kullanım, 4 dosya), gerçek `grep` ve dosya okumasıyla.

  | | Zayıf yönerge | Güçlü yönerge |
  |---|---|---|
  | Adım | 20 (sınırda) | **3** |
  | Token | 204.490 | **4.683** |
  | Süre | 77s | 22s |
  | Düzenleme | **0** | **9** |

  - **Sonuç: model yeterli, tasarım geçerli.** Ama sistem yönergesi olmadan
    tamamen başarısız oluyor.

- [~] T11 Aynı ölçüm kullanıcının sunucusunda tekrarlanır — **ertelendi** *2026-10-22*
  - Kullanıcı kararı: kurumsal sunucudaki model OpenRouter'dakine benzer yapıda,
    T10 temsil edici sayılıyor. Erişim olunca `GercekAjanTest` `ACP_AJAN_*` ile koşulur.

## İstemcide araç çağırma

- [x] T20 `AracTanimi`, `AracCagrisi`, `ChatDelta.ToolCall`, `Role.TOOL` eklendi
- [x] T21 Kodek: `tools` gövdeye yazılıyor, araç mesajları kodlanıyor
- [x] T22 **Araç yokken gövde birebir eskisi gibi** — mesaj alanları `{role, content}` ile sınırlı
- [x] T23 `ToolCallBiriktirici` → parçalar birleşiyor, yarım JSON tüketiciye ulaşmıyor
- [x] T24 **Gerçek sunucuya** karşı uçtan uca — `GercekAjanTest` (sahte sunucudan daha güçlü)

## Ajan döngüsü — saf

- [x] T30 `AjanDongusu` mutlu yol → 15 saf test + gerçek modelle uçtan uca
- [x] T31 Adım sınırı → `ADIM_SINIRI` bildiriliyor
- [x] T32 İptal → akış kapatılınca sonraki istek gitmiyor
- [x] T33 Araç hatası → modele bildiriliyor, döngü sürüyor
- [x] T34 Bilinmeyen araç adı → çökme yok
- [x] T35 Bozuk argüman JSON'u → ham metin yürütücüye gidiyor, ayrıştırma orada
- [x] T36 `DuzenlemePlani` → dosya bazında gruplanıyor, aynı düzenleme iki kez eklenmiyor
- [x] T37 `AjanYonergesi` ayrı sabit; her istekte gönderildiği testle doğrulanıyor
- [x] T38 **Tekrar kilitlenmesi kırılıyor** → ikinci turda yakalanıyor, 10 tur boşa gitmiyor
  - Meşru tekrar ayrılıyor: aynı araç farklı argümanla çağrılabilir.

- [x] T39 **Gerçek araçlarla uçtan uca test** (kullanıcı isteği) → `GercekAjanTest`
  - Gerçek `grep`, gerçek dosyalar, gerçek model; bu deponun kendisinde.
  - `-Dacp.canliTest=true` ile açılır. Sonuç: `TAMAMLANDI`, 9 düzenleme, 4 dosya.
  - Hiçbir dosya değiştirilmiyor — `dosya_duzenle` yalnızca plana ekliyor.

## Araçların IDE uygulaması

- [x] T40 `dosya_ara` → `ProjectFileIndex.iterateContent`, dile özgü API yok
- [x] T41 Arama sonucu 40'ta kırpılıyor, fazlası sayıyla bildiriliyor
- [x] T42 `dosya_oku` → 20.000 karakterde kırpılıyor
- [x] T43 `dosya_duzenle` **diske yazmıyor** — dosya içeriği testte birebir karşılaştırılıyor
  - Ayrıca: birden fazla eşleşme varsa reddediliyor (belirsiz düzenleme sessizce
    yanlış yere uygulanmamalı), değişiklik içermeyen düzenleme reddediliyor.
- [x] T44 `..` içeren yol baştan reddediliyor; okuma ve düzenleme için ayrı test

## Toplu uygulama

- [x] T50 `hesapla` → yeni içerikler bellekte; dosyaya dokunulmadığı testle doğrulandı
- [x] T51 Atlanan düzenlemeler `TopluSonuc.atlananlar` içinde **bildiriliyor**
- [x] T52 Çok dosyalı önizleme `SimpleDiffRequestChain` ile; boş sonuçta pencere açılmıyor
  - Gözle doğrulama T90'a bırakıldı.
- [x] T53 **Tek geri alma adımı** → 3 dosya, tek `undo` **(mutasyonla doğrulandı)**
  - Ayrı komutlara bölününce test kırmızıya döndü: yalnızca son dosya geri geliyordu.
- [x] T54 Hesaplama dosyaya dokunmuyor; vazgeçmek `uygula`'yı hiç çağırmamak demek
- [x] T55 **Bayatlık → hiçbiri uygulanmıyor** — bir dosya değiştiyse ilki de yazılmıyor

## Ayarlar ve arayüz

- [x] T60 Ayarlar: kip (varsayılan `ONIZLE`, metin olarak), adım ve dosya sınırı — 9 test
- [x] T61 `DOGRUDAN` kipi **aynı uygulama yolunu** kullanıyor; tek geri alma adımı korunuyor
- [x] T62 Çalışırken adımlar görünüyor ✅ *2026-09-04, runIde*
- [x] T63 Durdurma düğmesi ✅ *2026-09-05, runIde*
- [x] T64 Hiçbir yer bulunamadıysa açıkça söyleniyor; boş sonuçta önizleme açılmıyor

## Sağlamlaştırma

- [x] T70 `BaglamKirpici` → eski araç sonuçları kırpılıyor, **mesaj silinmiyor** (11 test)
- [x] T71 Dosya sınırı → aşılınca sebebi söyleniyor; sınırdaki dosya düzenlenmeye devam edebiliyor
- [x] T72 Ajan yolunda tek günlük çağrısı var ve yalnızca istisna sınıf adını yazıyor ✅
  - Yan bulgu: atlananların ayrıntısı hesaplanıyordu ama gösterilmiyordu; ipucuna eklendi.
- [x] T73 Ajan durumu sohbetten yalıtık; proje seviyesinde servis (5 test)
- [x] T74 `verifyPlugin` → üç IDE sürümünde **Compatible**, dahili API **0** ✅ *2026-09-06*
  - `ChainDiffVirtualFile` ve `SimpleDiffRequestChain` dahili sayılmıyor.

## Gerileme kapısı

- [x] T80 001–007'nin **385 testi değiştirilmeden** geçti ✅ — toplam **471**
  - 86 yeni test; hiçbir eski test değiştirilmedi.
  - `UygulaSeridiTest` yol boyunca bir kez kırmızıya döndü ve **kod düzeltildi**,
    test değil. Kapının amacı buydu.

## Doğrulama ve kapanış

- [x] T90 `spec.md` kabul kriterlerinin tamamı `runIde` ile elle doğrulanır ✅ *2026-10-22*
  - İlk turda 1–4 geçti. **5 (DOGRUDAN) kaldı:** kip yalnızca etiketi
    değiştiriyordu, hiçbir yer `uygula`'yı otomatik çağırmıyordu. T61 aynı yolun
    kullanıldığını doğrulamıştı ama yolu kimin çağırdığını değil.
    `AjanService.sonucuIsle` düzeltildi, `AjanKipiTest` (3 test) — toplam **474**.
  - Yan bulgu: `uygula`'nın hata dönüşü arayüzde atılıyordu (bayat dosya
    sessizce yutuluyordu); artık `durum.hata`'ya yazılıyor.
  - Düzeltmeden sonra runIde'de 5 de geçti: önizlemesiz uygulama, tek Cmd+Z,
    kip yeniden başlatmada korunuyor.
- [x] T91 `spec.md` durumu "Uygulandı" yapılır ✅ — belirsizlikler T10/T60/T70'e bağlanarak kapatıldı
- [x] T92 `AGENTS.md` güncellendi — mimari, üç katman, yönerge tablosu, yerleşim kuralı
- [x] T93 **Kip seçici görünür** → `KipSeciciTest` (2 test, önce kırmızı) + runIde'de gözle ✅ *2026-10-22*
  - Bulgu (T90): "Çok dosyalı" anahtarı düğme sütununun dibindeydi; istek
    sohbete gitti ve "çalışmadı" sanıldı.
  - Anahtar → giriş kutusunun üstünde `Sohbet | Çok dosyalı` seçici; ipucu
    metni kipe göre değişiyor. Yönlendirme mantığı değişmedi. Toplam **476**.
- [x] T94 **Geçmişteki araç argümanı her zaman geçerli JSON nesnesi** → `AracCagirmaTest` (2 test, önce kırmızı) ✅ *2026-10-22*
  - Kurumsal sunucu (LiteLLM → vLLM) geçersiz argümanı tüm isteği reddederek
    cezalandırıyor: `Assistant tool call function.arguments must be valid JSON.` (400).
    T35 bozuk argümanı ham saklıyordu; bir sonraki turda olduğu gibi geri gidiyordu.
  - `OpenAiCodec` gönderirken boş/geçersiz/nesne-olmayan argümanı `{}` yapıyor.
    Yürütücü yine ham metni görüyor; modelin hatası araç cevabında duruyor. Toplam **478**.

---

## Notlar

Uygulama sırasında ortaya çıkan sapmalar, kararlar ve nedenleri.
Plandan sapıldıysa **neden** sapıldığı buraya yazılır.

### T10 — sistem yönergesi bir ayrıntı değil, özelliğin kendisi

İlk koşuda model 5. adımda yerleri buldu, sonra **aynı aramayı 12 kez tekrarladı**
ve adım sınırına dayandı. Tek düzenleme üretmedi, 204 bin token harcadı.

İkinci koşuda tek fark bir sistem yönergesiydi. Aynı model, aynı araçlar, aynı
görev: 3 adım, 4.683 token, 9 doğru düzenleme.

Bu, planda hafife alınmış bir şeydi. Yönerge, kodun içine gömülmüş bir dizge
değil — **özelliğin çalışıp çalışmamasını belirleyen bileşen**. T37 onu testle
sabitliyor.

İkinci ders: yönerge davranışı *iyileştirir* ama **garanti etmez**. Modelin
kilitlenme ihtimali duruyor, ve bedeli gerçek: 204 bin token, 77 saniye. Bu
yüzden T38'de mekanik bir güvenlik ekleniyor — aynı araç aynı argümanla arka
arkaya çağrılırsa döngü kırılır. Yönergeye güvenip sayacı atlamak, "model iyi
davranır" varsayımına bahse girmek olurdu.

### T30-T39 — sahte ve gerçek testler farklı işler yapıyor

Kullanıcı "sahte araçlarla değil gerçek testlerle yap" dedi. İkisi de yazıldı,
çünkü farklı şeyleri doğruluyorlar:

**Sahte araçlar (15 test)** hata yollarını doğruluyor: iptal, adım sınırı,
tekrar kilitlenmesi, aracın istisna fırlatması, bozuk argüman JSON'u. Bunları
gerçek bir modele **istendiği anda** yaptırmak mümkün değil — model bozuk JSON
üretsin diye beklenemez.

**Gerçek araçlar (`GercekAjanTest`)** ise sahtelerin göremediğini görüyor:
modelin gerçekten çalışıp çalışmadığını. T10'da sistem yönergesinin belirleyici
olduğunu ancak gerçek koşuyla anladık; sahte araçlarla o bulgu asla çıkmazdı.

Gerçek test T10 ile aynı sonucu verdi: `TAMAMLANDI`, 10 araç çağrısı,
9 düzenleme, 4 dosya. Artık tekrar koşulabilir — model veya sunucu
değiştiğinde ilk bakılacak yer.

### `tools` parametresinin yeri — sessiz bir tuzak

`ChatRequest.tools`'u önce `extraBody`'den **önce** koydum ve konumsal çağrı
yapan mevcut bir test kırıldı. Testi düzeltmek yerine parametre sona alındı.

Burada derleyici yakaladı çünkü tipler uyuşmuyordu. **Uyumlu olsaydı
yakalamazdı** — mevcut çağrılar sessizce yanlış parametreye bağlanırdı. Yeni
alanlar veri sınıflarının **sonuna** eklenir.

### T40-T44 — `project.basePath` kırılgan çıktı

İlk uygulamada yollar `project.basePath`'e göre çözülüyordu. Dört test kırıldı:
`HATA: bulunamadı: Hedef.txt`.

Sebep yalnızca test ortamı değil, gerçek bir tasarım zayıflığı: `basePath` null
olabilir ve **çok modüllü projelerde dosyalar onun altında olmayabilir**.
Üstelik arama zaten `iterateContent` ile içerik köklerini geziyordu — arama bir
kaynağa, yol çözümü başka bir kaynağa bakıyordu.

Düzeltme: ikisi de `ProjectRootManager.contentRoots` kullanıyor. Aramanın
bulduğu her dosya artık `dosya_oku` ve `dosya_duzenle` ile de erişilebilir;
öncekinde bu garanti yoktu.

Bu, ancak `BasePlatformTestCase` ile — yani gerçek bir proje modeliyle —
görülebilecek bir hataydı. Saf testler bu tutarsızlığı asla göstermezdi.

### T50-T55 — bayatlık kontrolü yazmadan ÖNCE, hepsi için

`uygula` önce **tüm** dosyaların hâlâ beklenen içerikte olduğunu doğruluyor,
sonra yazmaya başlıyor. Dosya dosya kontrol edip yazsaydık, üçüncü dosyada
bayatlık bulunduğunda ilk ikisi çoktan yazılmış olurdu — tam olarak spec'in
yasakladığı durum.

Tek geri alma adımı `withGroupId` ile değil, **tek `WriteCommandAction`** ile
sağlanıyor: bir komut birden fazla belgeye dokunduğunda platform geri almayı
kendiliğinden global yapıyor. Mutasyon testi bunu kanıtladı — dosya başına ayrı
komut kullanıldığında tek geri alma yalnızca son dosyayı döndürdü.

### Diff görünümü — kullanıcı bulgusu

Kullanıcı ilk denemede ajanın çalıştığını, ama **çok dosyalı diff'in iyi
görünmediğini** bildirdi ve "yapılabilecek bir şey yok gibi" dedi.

Vardı, ama tamamı değil. İki API denendi:

| Aday | Sonuç |
|---|---|
| `ChainDiffVirtualFile` | ✅ Var. Diff'i kipsiz pencere yerine **editör sekmesinde** açıyor |
| `SimpleChangesBrowser` (ağaç + diff) | ❌ Sınıf yolunda yok — VCS modülünde |

Uygulanan: sekme görünümü + başlıklara değişiklik sayısı
(`SwingChatView.kt — 2 değişiklik`). Pencere artık kodun üstünü kapatmıyor ve
hangi dosyada ne kadar iş olduğu listeden görülüyor.

**Çözülmeyen:** solda dosya ağacı olan gerçek "değişiklikler" görünümü. O
bileşen VCS modülünde ve eklenti yalnızca `com.intellij.modules.platform`'a
bağlı. Almak mümkün (spec 006'daki `lang` deseni: isteğe bağlı bağımlılık +
ayrı config dosyası + geri düşüş), ama Altın Kural 3'ü ikinci kez esnetir.

**Karar bekliyor** — kullanımda ne kadar rahatsız ettiği görüldükten sonra.
Ayrı bir spec'e bırakılabilir.

### Arayüz bulguları — ikisi de ekran görüntüsüyle geldi

**1. Ajan şeridi dar panelde kırpıldı.** `FlowLayout` satır kaydırdı, "Hepsini
uygula" ve "Vazgeç" ekran dışında kaldı. Spec 003'te aynı hata yaşanmış, çözümü
koda yorum olarak yazılmıştı — ajan şeridini yazarken uygulamadım.
`UygulaSeridiTest` bunu göremedi çünkü yalnızca sohbetin kendi şeridine bakıyor.

**2. Sorulan görev panelde görünmüyordu ve uzun özet kırpılıyordu.** Sohbette
soru ekranda kalıyor; ajanda kalmıyordu.

### Kırpma testini iki kez yanlış yazdım

**Birinci deneme işe yaramıyordu:** "metin bileşenin içinde var mı" diye
bakıyordu. `JBLabel` da metnin tamamını tutar — kırpma görseldir. Mutasyonla
denendi, test **geçti**.

**İkinci deneme daha tehlikeliydi:** yüksekliği ölçüp sarılma aradı, kırmızı
kaldı ve neredeyse çalışan kod değiştirilecekti. Şüphelenip **bilinen-iyi** bir
bileşen aynı testten geçirildi: kullanıcının doğruladığı sohbet gövdesi de 16 px
verdi. Başsız test ortamında gerçek boyama olmadığı için o ölçüm hiçbir şey
söylemiyor.

Sonunda ölçülebilir olan ölçüldü: bileşenin **türü**. Gerçek gerileme riski
birinin alanı tek satırlık bir etikete çevirmesi; test onu yakalıyor
(mutasyonla doğrulandı).

**Ders:** bir test kırmızı kalıyorsa, kodu değiştirmeden önce **testin
kendisinin geçerli olduğunu** doğrula. Bilinen-iyi bir girdiyi aynı testten
geçirmek bunun en ucuz yolu.
