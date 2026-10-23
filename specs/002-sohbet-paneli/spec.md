# Spec: Sohbet Paneli

- **Spec no:** 002
- **Tarih:** 2026-09-03
- **Durum:** Uygulandı — *2026-09-04*'te biçimlendirmeyle genişletildi

---

## Problem

[001](../001-cekirdek-ve-model-baglantisi/) tamamlandığında eklenti modele
bağlanabiliyor olacak, ama kullanıcının bunu kullanabileceği hiçbir yüzey yok.
Geliştirici hâlâ soru sormak için IDE'den çıkıp kodu bir tarayıcıya yapıştırmak
zorunda — ki denetimsiz olduğu ve dikkat dağıttığı için baştaki sorun bu.

## Amaç

Geliştirici, IDE içindeki bir panelden modele soru sorabilecek; üzerinde çalıştığı
dosya ve seçtiği kod soruya otomatik eklenecek; yanıt yazılırken görünecek ve
istenirse durdurulabilecek.

## Kapsam dışı

- **Seçime dayalı hazır eylemler ve kodu değiştirme** → [003](../003-secim-aksiyonlari/)
- **Yazarken otomatik kod tamamlama.** Ayrı spec.
- **Proje genelinde arama veya indeksleme.** Bağlam yalnızca açık dosya ve seçimden
  toplanır; projenin tamamı taranmaz.
- **Konuşma geçmişinin kalıcı saklanması.** Geçmiş IDE kapanınca kaybolur.
- **Birden fazla eşzamanlı sohbet oturumu.** Tek oturum.

---

## Kullanıcı Hikâyeleri

### H1 — Panelden soru sorma

**Geliştirici** olarak, **IDE içinde bir panelden modele soru sormak** istiyorum,
çünkü **kodu kopyalayıp dışarı taşımak dikkatimi dağıtıyor ve denetimsiz**.

Kabul kriterleri:

- [x] Panel IDE'nin yan çubuğundan açılır ve dört IDE'de de görünür
- [x] Soru gönderildiğinde yanıt **parça parça, yazılırken** görünür
- [x] Yanıt gelirken iptal düğmesi görünür; basıldığında akış durur ve o ana kadar
      gelen metin ekranda **kalır**
- [x] Aynı oturumda arka arkaya sorulan sorular birbirinin bağlamını görür
- [x] Uzun bir yanıt akarken IDE'de yazmaya devam edilebilir; arayüz donmaz
- [x] Kullanıcı yukarı kaydırıp eski mesajı okurken yeni gelen metin ekranı
      zorla aşağı kaydırmaz
- [x] Ayarlar eksikken soru sorulmaya çalışılırsa ayarlara yönlendiren uyarı çıkar
- [x] Panel sekmesi kapatılıp yeniden açıldığında konuşma yerinde durur

### H2 — Yanıtın okunabilir görünmesi

**Geliştirici** olarak, **yanıttaki kodun düzgün görünmesini** istiyorum, çünkü
**düz metin yığını içinde kod okumak zor**.

Kabul kriterleri:

- [x] Yanıttaki kod blokları, IDE'nin editöründeki gibi renklendirilmiş görünür
- [x] Her kod bloğu tek tıkla kopyalanabilir
- [x] Model bir düşünme bölümü üretirse bu bölüm cevaptan ayrı, katlanmış ve soluk
      gösterilir; varsayılan olarak kapalıdır
- [x] Açık/koyu tema değiştiğinde panel de değişir; sabit renk kalmaz
- [x] Uzun bir sohbet sonrası panel kapatıldığında bellekte artık kalmaz

**2026-09-04'te eklenen kriterler.** İlk sürümde yalnızca kod blokları
biçimlendiriliyordu; düz metin ham Markdown olarak görünüyordu (`###`, `**`,
`-` işaretleri okunur hâlde). H2'nin amacı "yanıtın okunabilir görünmesi"
olduğu için bu kriterler ayrı bir spec yerine buraya eklendi:

- [x] Başlıklar, kalın ve italik metin, madde ve numaralı listeler, satır içi kod
      **biçimlendirilmiş** görünür; `#` ve `**` gibi işaretler ekranda kalmaz
- [x] Biçimlendirme, IDE'nin açık/koyu temasına ve yazı tipine uyar
- [x] Modelin ürettiği metinde HTML geçerse **düz metin olarak** görünür,
      görünümü bozmaz
- [x] Akış sırasında metin düz görünmeye devam eder; biçimlendirme yanıt
      bittiğinde uygulanır (yarım gelmiş işaretler render edilmez)
- [x] Biçimlendirilemeyen bir metin, kaybolmaz — en kötü ihtimalle olduğu gibi
      gösterilir

### H3 — Açık dosya ve seçim bağlam olarak gider

**Geliştirici** olarak, **soruma açık dosyamın ve seçtiğim kodun otomatik
eklenmesini** istiyorum, çünkü **her seferinde elle yapıştırmak istemiyorum**.

Kabul kriterleri:

- [x] Bir dosya açıkken sorulan soruya o dosyanın yolu ve içeriği eklenir
- [x] Kod seçiliyken seçim ayrıca ve öncelikli olarak eklenir
- [x] Panelde, gönderilen bağlamın ne olduğu (dosya adı, satır aralığı) görünür —
      kullanıcı ne paylaştığını bilir
- [x] Bağlam ayarlardaki sınırı aşarsa kırpılır; **seçim asla kırpılmaz**, kırpma
      satır ortasından yapılmaz ve kırpıldığı yerde belirtilir
- [x] Hiçbir dosya açık değilken de soru sorulabilir

---

## Davranış Kuralları

- Kullanıcıya gösterilen tüm metinler Türkçedir.
- Eklenti dört IDE'de de aynı şekilde çalışır.
- Model yanıt verirken kullanıcının IDE'de yaptığı hiçbir iş engellenmez.
- İptal bir hata değildir; hata olarak gösterilmez.
- Kullanıcının kodu günlük kayıtlarına yazılmaz.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Ayarlar eksik | Ayarlara yönlendiren uyarı; istek gönderilmez |
| Yanıt ortasında bağlantı kopar | Gelen metin korunur, altında hata şeridi ve "yeniden dene"; düğme son soruyu baştan sorar |
| Sunucu hatası | Sade Türkçe mesaj; teknik ayrıntı günlüğe |

---

## Belirsizlikler

- [x] Panel kapatılıp açıldığında konuşma geçmişi korunsun mu?
      → **Cevap:** Korunur. Panel sekmesi kapatılıp açıldığında konuşma yerinde
      kalır; yalnızca IDE kapanınca kaybolur. Kullanıcı sekmeyi kazara kapatınca
      işini kaybetmemeli. Bellekte tutulur, diske yazılmaz.

- [x] "Yeniden dene" ne yapar?
      → **Cevap:** Son soruyu baştan sorar. Yarım yanıt silinir, aynı soru
      yeniden gönderilir. Öngörülür ve basit; karşılığında token maliyeti
      tekrarlanır. "Kaldığı yerden devam" elendi — modeller bunu çoğu zaman
      kötü yapıyor, kopuk veya tekrar eden metin üretiyor.

## Bağımlılıklar

- [001 — Çekirdek ve model bağlantısı](../001-cekirdek-ve-model-baglantisi/)
  tamamlanmadan uygulanamaz.
