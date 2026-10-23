# Spec: Çok Dosyalı Düzenleme

- **Spec no:** 008
- **Tarih:** 2026-09-04
- **Durum:** Uygulandı — *2026-10-22*

---

## Problem

Eklenti bugün **tek dosyada** çalışıyor. Sohbet soru cevaplıyor, seçim
aksiyonları seçili koda dokunuyor, tamamlama imlecin bulunduğu yere yazıyor.
Hepsinin ortak sınırı aynı: geliştiricinin o an baktığı dosya.

Ama gerçek işlerin çoğu bu sınırı aşıyor. Bir metodun imzası değişince onu
çağıran her yer değişmeli. Kullanımdan kaldırılan bir kalıp, yayıldığı her
dosyadan temizlenmeli. Yeni bir kural benimsendiğinde eski koda da uygulanmalı.

Bugün bu işler elle yapılıyor: geliştirici arama yapıyor, sonuçları tek tek
geziyor, her birinde aynı kararı tekrar veriyor. Sıkıcı olmasının ötesinde
hataya açık — bir yer atlanırsa derleme hatası ya da daha kötüsü, sessiz bir
davranış farkı çıkıyor.

Eklentinin bildiği her şey (model, bağlam, diff önizleme, geri alma) bu işi
yapmaya yetiyor; eksik olan **birden fazla dosyayı bir arada ele alabilmesi**.

## Amaç

Geliştirici bir değişikliği tarif edecek; eklenti projede nerelerin
etkilendiğini kendisi bulacak, her yerde ne yazılacağına karar verecek ve
sonucu geliştiricinin gözden geçirip topluca uygulayabileceği biçimde
sunacak.

## Kapsam dışı

- **IDE'nin kendi refactoring'lerinin yerini almak.** Yeniden adlandırma, metot
  çıkarma gibi işleri IDE zaten güvenli ve kesin yapıyor. Bu özellik, IDE'nin
  mekanik olarak yapamadığı — her yerde ayrı karar gerektiren — değişiklikler
  içindir.
- **Yeni dosya veya dizin oluşturmak.** Yalnızca var olan dosyalar düzenlenir.
- **Kod dışı işler.** Komut çalıştırma, test koşturma, bağımlılık yükleme yok.
- **Değişikliği kaydetmek veya göndermek.** Commit, push, PR açma yok.
- **Sohbet, seçim aksiyonları ve tamamlama** → [002](../002-sohbet-paneli/),
  [003](../003-secim-aksiyonlari/), [006](../006-satir-ici-tamamlama/)

---

## Kullanıcı Hikâyeleri

### H1 — Değişikliği tarif et, uygulanacak yerleri eklenti bulsun

**Geliştirici** olarak, **bir değişikliği tarif edip etkilenen yerleri
eklentinin bulmasını** istiyorum, çünkü **arama sonuçlarını tek tek gezip her
birinde aynı kararı tekrar vermek hem yorucu hem de bir yeri atlamaya açık**.

Kabul kriterleri:

- [x] Geliştirici değişikliği kendi cümleleriyle tarif edebilir
- [x] Eklenti projede arama yapıp etkilenen yerleri kendisi bulur
- [x] Bulunan her yer için önerilen yeni hâl üretilir
- [x] Eklenti gerektiğinde ek dosya okuyup bağlam toplayabilir
- [x] Hiçbir yer bulunamazsa bu açıkça söylenir, sessizce boş sonuç dönmez

### H2 — Ne yapıldığını görmeden hiçbir şey değişmesin

**Geliştirici** olarak, **uygulanmadan önce hangi dosyalarda ne değişeceğini
görmek** istiyorum, çünkü **birden fazla dosyaya aynı anda dokunan bir
değişikliğin yanlış olması, tek dosyadakinden çok daha pahalıdır**.

Kabul kriterleri:

- [x] Varsayılan olarak hiçbir dosya, geliştirici onaylamadan değişmez
- [x] Değişecek tüm dosyalar ve içerikleri tek bir önizlemede görülür
- [x] Önizlemede hangi dosyada kaç değişiklik olduğu görünür
- [x] Geliştirici tamamını uygulayabilir veya tamamından vazgeçebilir
- [x] Uygulanan değişikliğin **tamamı tek geri alma adımıyla** geri alınır
- [x] Vazgeçilirse hiçbir dosyaya dokunulmamış olur

### H3 — Çalışırken ne olduğunu gör ve durdurabil

**Geliştirici** olarak, **eklenti çalışırken ne yaptığını görmek ve gerekirse
durdurmak** istiyorum, çünkü **ne kadar süreceği baştan belli değil ve yanlış
yola girdiğini fark ettiğimde beklemek zorunda kalmak istemem**.

Kabul kriterleri:

- [x] Çalışırken hangi adımda olduğu görünür (ne aradığı, hangi dosyayı okuduğu)
- [x] Her an durdurulabilir; durdurma anında etkili olur
- [x] Durdurulduğunda hiçbir dosya değişmiş olmaz
- [x] Adım sayısı sınırlıdır; sınıra ulaşılırsa durur ve bunu söyler
- [x] Sonsuz döngüye girmez

### H4 — Kullanıcı kontrolü

**Geliştirici** olarak, **eklentinin dosyalara nasıl dokunacağını seçebilmek**
istiyorum, çünkü **kimi zaman her adımı görmek isterim, kimi zaman güvendiğim
bir işi hızlıca bitirmek isterim**.

Kabul kriterleri:

- [x] İki çalışma biçimi arasında seçim yapılabilir:
      **önizle ve onayla** (varsayılan) veya **doğrudan uygula**
- [x] Varsayılan **önizle ve onayla**'dır
- [x] "Doğrudan uygula" seçiliyken de değişiklik tek geri alma adımıyla geri alınır
- [x] Seçim IDE yeniden başlatıldığında korunur
- [x] Yazarken sohbette mi çok dosyalı düzenlemede mi olunduğu görülür:
      seçici giriş kutusunun üstündedir, ipucu metni seçili kipe göre değişir
      *(T90 kullanıcı bulgusu, 2026-10-22 — T93)*
- [x] Adım sayısı ve dosya sayısı üst sınırları ayarlanabilir

---

## Davranış Kuralları

- **Varsayılan hiçbir şeye dokunmamaktır.** "Doğrudan uygula" bilinçli bir
  tercihtir; 006 ve 007'de konan "öneri asla onay olmadan koda yazılmaz"
  kuralından ayrılan tek yer burasıdır ve kullanıcının açıkça seçmesi gerekir.
- **Değişiklik ya tamamen uygulanır ya hiç uygulanmaz.** Yarısı uygulanmış bir
  çok dosyalı değişiklik, hiç uygulanmamış olmasından kötüdür.
- **Geri alma tek adımdır.** On dosyaya dokunulmuş olsa bile.
- Eklenti **var olmayan bir dosyayı oluşturmaz**, kapsam dışındaki dosyalara
  dokunmaz.
- Kullanıcının kodu günlük kayıtlarına yazılmaz.
- İş bitmeden veya durdurulmadan önce hiçbir dosya değişmez (önizleme kipinde).

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Hiçbir yer bulunamadı | Açıkça söylenir; boş önizleme gösterilmez |
| Model geçersiz bir düzenleme üretti | O yer atlanır, diğerleri sürer, atlananlar bildirilir |
| Düzenlenecek dosya bu arada değişti | O dosya atlanır; bayat içerik üzerine yazılmaz |
| Adım sınırına ulaşıldı | Durulur, o ana kadar üretilenler gösterilir |
| Dosya sayısı sınırına ulaşıldı | Durulur ve sebebi söylenir |
| Sunucu hatası veya zaman aşımı | İş durur, hata gösterilir, hiçbir dosya değişmez |
| Kullanıcı durdurdu | Anında durur, hiçbir dosya değişmez |

---

## Belirsizlikler

- [x] **Model, çok adımlı keşif döngüsünü gerçek bir projede sürdürebiliyor mu?**
      → **Cevap: evet, sistem yönergesiyle** (tasks T10): bu depoda 3 adım,
      9 düzenleme, 4 dosya. Yönergesiz 20 adımda sıfır düzenleme. Kullanıcının
      sunucusunda tekrarı T11'e kaldı (erişim olduğunda).

      Spike'ta (2026-09-04) sahte araç sonuçlarıyla iki adımlık bir döngü
      başarıyla koştu: doğru aracı çağırdı, sonucu kullanıp iki dosya için doğru
      düzenlemeyi üretti (parametre sırası dahil).

      ⚠️ Ama bu **tek ve temiz** bir senaryoydu, üstelik OpenRouter'da ölçüldü.
      Gerçek projede arama gürültülü sonuç verir, dosyalar büyüktür, bağlam
      bütçesi sıkışır. **Kod yazmadan önce gerçek bir depoda ölçülmeli.**

- [x] **Kaç adım yeterli?**
      → **Cevap: varsayılan 20, ayardan değişir** (T60). T10'da başarılı koşu
      3 adım sürdü; 20 sınırına yalnızca yönergesiz, başarısız koşu dayandı.

      Sınır çok düşükse iş yarım kalır, çok yüksekse kullanıcı bekler ve maliyet
      öngörülemez olur. Ölçümle belirlenecek.

- [x] **Bağlam bütçesi nasıl paylaşılacak?**
      → **Cevap: eski araç sonuçlarının içeriği kırpılır, mesaj silinmez**
      (`BaglamKirpici`, T70) — `tool` mesajı `tool_calls` ile eşleşmek zorunda.

      Keşif ilerledikçe konuşma büyüyor. Hangi noktada eski adımların özetlenmesi
      veya atılması gerekiyor?

- [x] **Etkilenen yerleri kim buluyor?**
      → **Cevap:** Model, kendisine verilen araçlarla **keşfediyor**.

      Elenen alternatif: yalnızca IDE'nin araması. Daha ucuz ve deterministik
      olurdu ama tarif edilen değişikliğin nerelere dokunduğunu her zaman metin
      araması belirleyemez.

      ⚠️ Bedeli kabul edildi: adım sayısı ve süre öngörülemez, her adımda model
      hata yapabilir. H3'teki durdurma ve sınırlar bunun karşılığıdır.

- [x] **Değişiklikler nasıl uygulanacak?**
      → **Cevap:** Ayardan seçilir — **önizle ve onayla** (varsayılan) veya
      **doğrudan uygula**.

      Gerekçe: ikisinin de yeri var. Tanımadığın bir değişiklikte her satırı
      görmek istersin; güvendiğin bir işte önizleme fazladan tıklamadır.
      Varsayılanın güvenli olması, 006'daki "varsayılan kapalı" kararıyla aynı
      mantık.

---

## Bağımlılıklar

- [001 — Çekirdek ve model bağlantısı](../001-cekirdek-ve-model-baglantisi/) —
  istemci ve ayar altyapısı. Araç çağırma bu spec'te ilk kez kullanılacak;
  ilgili tip 001'de bu düşünülerek genişletilebilir bırakılmıştı.
- [003 — Seçim aksiyonları](../003-secim-aksiyonlari/) — diff önizleme, tek
  adımlı geri alma ve bayatlık koruması oradan geliyor; bu spec onları **çok
  dosyaya** genişletiyor.
