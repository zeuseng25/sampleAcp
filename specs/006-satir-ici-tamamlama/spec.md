# Spec: Satır İçi Kod Tamamlama

- **Spec no:** 006
- **Tarih:** 2026-09-04
- **Durum:** Uygulandı — *2026-09-04*

---

## Problem

Eklenti şu ana kadar hep **kullanıcı isteyince** çalışıyor: soru sorulur, eylem
seçilir. Ama geliştiricinin zamanının çoğu soru sormakla değil **yazmakla**
geçiyor.

Bilinen bir kalıbı yazarken (bir döngü, bir null kontrolü, bir test kurulumu)
geliştirici ya tamamını elle yazıyor ya da panele geçip sorup cevabı geri
taşıyor. İkisi de akışı bölüyor. Oysa niyet zaten imlecin bulunduğu yerden belli.

## Amaç

Geliştirici yazmaya ara verdiğinde, imlecin bulunduğu yere uygun bir devam
önerisi soluk metin olarak belirecek; beğenirse tek tuşla kabul edecek,
beğenmezse yazmaya devam ederek yok sayacak. Panele geçmesi gerekmeyecek.

## Kapsam dışı

- **IDE'nin kendi kod tamamlamasının yerini almak.** Onun listesi (sınıf, metot,
  değişken adları) yerinde kalır; bu özellik onunla yarışmaz.
- **Birden fazla dosyayı ilgilendiren üretim.** Öneri yalnızca imlecin bulunduğu
  yere yazılır.
- **Proje genelinde arama veya indeksleme.** Bağlam imlecin çevresinden alınır.
- **Öneri geçmişi veya alternatifler arasında gezinme.** Tek öneri gösterilir.
- **Sohbet paneli ve seçim eylemleri** → [002](../002-sohbet-paneli/),
  [003](../003-secim-aksiyonlari/)

---

## Kullanıcı Hikâyeleri

### H1 — Yazarken öneri belirir

**Geliştirici** olarak, **yazmaya ara verdiğimde devam önerisi görmek**
istiyorum, çünkü **bilinen kalıpları elle yazmak zaman kaybı ve panele geçmek
akışı bölüyor**.

Kabul kriterleri:

- [x] Yazmaya ara verildiğinde imlecin bulunduğu yerde soluk bir öneri belirir
- [x] Öneri, imlecin **önündeki ve arkasındaki** koda uyar — yalnızca öncesine değil
- [x] Yazmaya devam edilince eski öneri **anında** kaybolur; bayat öneri ekranda kalmaz
- [x] Kullanıcı yazarken her tuşta istek gönderilmez; yalnızca ara verilince
- [x] Öneri gelmezse veya gecikirse yazma hiçbir şekilde yavaşlamaz
- [x] IDE'nin kendi tamamlama listesi açıkken bu öneri onu bozmaz

### H2 — Öneriyi kabul veya ret

**Geliştirici** olarak, **öneriyi tek tuşla kabul edip beğenmezsem yok saymak**
istiyorum, çünkü **her öneri için karar vermek zorunda kalmak yazmaktan daha
yorucu olurdu**.

Kabul kriterleri:

- [x] Öneri tek bir tuşla kabul edilir ve koda yazılır
- [x] Kabul edilen öneri **tek geri alma adımıyla** geri alınır
- [x] Yazmaya devam etmek öneriyi sessizce yok sayar; ek bir tuş gerekmez
- [x] Kabul tuşu, IDE'nin aynı tuşa bağlı mevcut davranışını öneri yokken bozmaz

### H3 — Kullanıcı kontrolü

**Geliştirici** olarak, **bu özelliği kapatabilmek ve nerede çalışacağını
sınırlayabilmek** istiyorum, çünkü **her dosyada ve her an kod göndermesini
istemeyebilirim**.

Kabul kriterleri:

- [x] Özellik ayarlardan tamamen kapatılabilir
- [x] Kapalıyken hiçbir istek gönderilmez
- [x] Açık/kapalı durumu IDE yeniden başlatıldığında korunur
- [x] Kullanıcı, önerinin hangi modeli kullanacağını sohbetten **ayrı** seçebilir
- [x] Kullanıcı **tamamlama biçimini** seçebilir; model ailesi değiştiğinde
      biçimi de değiştirebilir
- [x] Biçim "Kapalı" seçiliyken özellik hiç çalışmaz

---

## Davranış Kuralları

- **Yazma deneyimi hiçbir koşulda yavaşlamaz.** Öneri gecikirse, hata verirse
  veya sunucu yanıt vermezse kullanıcı bunu fark etmemeli; yazmaya devam eder.
- **Kod sürekli dışarı gider.** Bu, sohbetten farklı bir denetim durumudur:
  kullanıcı her seferinde bilinçli bir eylem yapmıyor. Bu yüzden özelliğin açık
  mı kapalı mı olduğu kullanıcıya net görünmeli ve kapatması kolay olmalı.
- Öneri asla kullanıcı onayı olmadan koda yazılmaz.
- Hatalar kullanıcıya **gösterilmez**; sessizce öneri üretilmez ve günlüğe yazılır.
  Yazarken hata penceresi açmak kabul edilemez.
- Kullanıcının kodu günlük kayıtlarına yazılmaz.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Sunucuya ulaşılamıyor | Öneri gösterilmez; kullanıcı rahatsız edilmez |
| Öneri geç geliyor | İmleç oynadıysa öneri atılır, gösterilmez |
| Model uygun olmayan çıktı döndürdü | Öneri gösterilmez |
| Ayarlar eksik | Özellik sessizce çalışmaz; sohbetteki gibi uyarı çıkarmaz |

---

## Belirsizlikler

- [x] Sunucuda fill-in-the-middle yeteneği olan bir model var mı?
      → **Cevap:** Şu an Qwen3 27B; ileride GLM ve Kimi denenecek. Bu yüzden tek
      bir modele bağlanmıyoruz: **tamamlama biçimi ayardan seçiliyor**
      (Kapalı / Qwen / DeepSeek / StarCoder / CodeLlama / Sohbet tarzı).

      ⚠️ Gerekçe: FIM belirteçleri model ailesine göre değişiyor
      (`<|fim_prefix|>` ≠ `<｜fim▁begin｜>` ≠ `<PRE>`). Tek biçim varsayılsaydı
      model değişince model belirteçleri **düz metin** sanır ve sessizce kötü
      öneri üretirdi — hata vermeden.

      ⚠️ Ayrıca: Qwen3 ile Qwen3-**Coder** aynı değil; FIM eğitimi coder
      varyantlarında. Elindeki modelin FIM'i gerçekten destekleyip desteklemediği
      denenmeden bilinmez (T10).

- [x] Öneri hangi anda tetiklensin?
      → **Cevap:** Yazmaya **ara verilince otomatik**.
      Gerekçe: varsayılan kapalı olduğu için gizlilik endişesi zaten karşılandı;
      ayrıca yalnızca kısayolla çalışan bir öneri "satır içi" olmaktan çıkar,
      seçim eylemlerinin zayıf bir kopyası olurdu.
      *Bu karar kullanıcı tarafından değil, planlama sırasında verildi — yanlışsa
      düzeltilmeli.*

- [x] Varsayılan açık mı kapalı mı?
      → **Cevap:** **Kapalı.** Kullanıcı bilinçli olarak açar.
      Gerekçe: sohbette kullanıcı her seferinde bir eylem yapıyor; satır içi
      tamamlamada kod yazdıkça kendiliğinden gidiyor. Projenin başlangıç problemi
      "kod nereye gidiyor denetlenemiyor" idi — varsayılan kapalı olması bununla
      tutarlı.

- [x] Öneri kaç satır olabilsin?
      → **Cevap:** **Tek satır.** Hızlı, ucuz, yanlış öneri az rahatsız eder.
      Çok satır sonradan ayrı bir iş olarak açılabilir.

## Uygulama sonrası notlar

Kabul kriterlerinin tamamı 2026-09-04'te `runIde` ile gerçek modele
(`qwen/qwen3.8-27b`) karşı elle doğrulandı.

Spec yazılırken öngörülmeyen, uygulama sırasında ortaya çıkan iki şey:

1. **Üye erişiminden sonra öneri üretilmiyor.** Kullanıcı `int toplam` için
   modelin `toplam.toString()` önerdiğini bildirdi — Java'da ilkel tipin
   `toString()`'i yok, öneri derlenmiyor. Modele tip bilgisi gitmiyor; IDE ise
   biliyor. Alan IDE'ye bırakıldı. Bu, spec'in "IDE'nin kendi kod tamamlamasının
   yerini almak → kapsam dışı" maddesinin doğal sonucuydu ama spec yazılırken
   fark edilmemişti.

2. **Çok satırlı tamamlama istendi.** Tek satır kararı (Belirsizlik 4) yerinde
   duruyor; çok satır kendi spec'ini alacak — bkz. `specs/README.md`.

## Bağımlılıklar

- [001 — Çekirdek ve model bağlantısı](../001-cekirdek-ve-model-baglantisi/) —
  istemci ve ayar altyapısı oradan geliyor.
