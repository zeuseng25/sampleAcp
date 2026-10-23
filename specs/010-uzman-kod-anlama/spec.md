# Spec: Uzman Kod Anlama

- **Spec no:** 010
- **Tarih:** 2026-10-22
- **Durum:** Uygulandı — *2026-10-23*

---

## Problem

Eklenti bugün kodu **metin** olarak görüyor. "Seçili kodu açıkla" yalnızca
seçimi modele gönderiyor; model o kodu kimin çağırdığını, neyi çağırdığını,
mimarinin neresinde durduğunu bilmiyor ve cevabı tahminle dolduruyor.

Çok dosyalı düzenleme de yerleri metin aramasıyla buluyor. Aynı adı taşıyan
farklı metotlar karışıyor; bir arayüz üzerinden yapılan çağrı, aynı adlı ama
ilgisiz bir metodun çağrısından ayırt edilemiyor.

IDE ise kodu zaten **anlamıyla** dizinlemiş durumda: bir metodu gerçekten kimin
çağırdığını, bir arayüzü kimin uyguladığını, bir adın nerede tanımlandığını
kesin olarak biliyor. Bu bilgi bugün modele hiç ulaşmıyor.

Terminalden çalışan açık kaynak ajanlar bu bilgiye erişemez; yalnızca IDE'nin
içinde çalışan bir eklenti erişebilir. Ürünün farkı burada.

## Amaç

Geliştirici yeni bir **Uzman** kipinde "bu metodu açıkla" veya "bu kod ne işe
yarıyor" diye sorduğunda, eklenti kodu IDE'nin anladığı ilişkilerle araştıracak
ve kıdemli bir uzman gibi anlatacak: ne yaptığını, neyi çağırdığını, kimin
çağırdığını, mimarideki yerini, değiştirilirse neyin etkileneceğini ve gördüğü
riskleri. Her iddia bulunan bir yere dayanacak.

Aynı kesin bilgi sohbete (salt okunur) ve çok dosyalı düzenlemeye de verilecek.

## Kapsam dışı

- **Uzman kipinde dosya değiştirmek.** Uzman yalnızca okur ve anlatır.
- **Çağrı ağacını veya mimariyi görsel olarak çizmek.** Cevap metindir.
- **Dile özgü derin analizler** (tip çıkarımı, veri akışı, ölü kod tespiti).
  Yalnızca IDE'nin tüm dillerde sunduğu ilişkiler kullanılır.
- **Yeni dosya oluşturmak** — 008'deki sınır geçerli.
- **Derleme ve test** → [009](../009-derleme-hatasi-duzeltme/)

---

## Kullanıcı Hikâyeleri

### H1 — Uzman kipinde kod anlatılır

**Geliştirici** olarak, **tanımadığım bir kodu kıdemli birinin anlatacağı gibi
öğrenmek** istiyorum, çünkü **kodu kimin kullandığını ve neye dokunduğunu
bilmeden değiştirmek risklidir; bunu elle bulmak saatler sürer**.

Kabul kriterleri:

- [x] Kip seçicide **Sohbet**, **Uzman** ve **Çok dosyalı** seçenekleri yan yana görünür
- [x] Son seçilen kip proje bazında hatırlanır; ilk açılışta **Sohbet**'tir
      *(009 T90 kullanıcı bulgusu, 2026-10-23: yeniden açılışta Sohbet'e dönüyordu,
      çok dosyalı istek sohbete gitti — T53)*
- [x] Uzman kipinde sorulan "X'i açıkla" türü sorulara cevap şu bölümleri içerir:
      ne yapıyor · nereyi çağırıyor · kim çağırıyor · mimarideki yeri ·
      değiştirilirse ne olur · riskler ve öneriler
- [x] Cevaptaki her çağıran, çağrılan ve ilişki **dosya adı ve satırıyla** gösterilir
- [x] Bulunamayan bir bilgi uydurulmaz; "bulunamadı" veya "emin değilim" denir
- [x] Cevap sohbet panelinde görünür ve takip sorusu sorulabilir
- [x] Uzman kipi hiçbir dosyayı değiştiremez

### H2 — İlişkiler kesin bulunur

**Geliştirici** olarak, **bir metodu gerçekten kimin çağırdığını** bilmek
istiyorum, çünkü **aynı adı taşıyan başka bir metodun çağrısını onunkine
karıştıran bir cevap beni yanlış yere götürür**.

Kabul kriterleri:

- [x] Bir metodun çağıranları, aynı adlı ilgisiz metotların çağrılarından ayrılır
- [x] Her çağıran için **içinde bulunduğu metot veya fonksiyon** söylenir
- [x] Bir arayüzün veya soyut metodun uygulamaları bulunabilir
- [x] Bir adın tanımlandığı yer bulunabilir
- [x] Aynı ada sahip birden fazla aday varsa hepsi listelenir, biri sessizce seçilmez
- [x] Bu, IDE'nin desteklediği her dilde aynı biçimde çalışır; desteklenmeyen
      dilde metin aramasına düşülür ve **bu söylenir**

### H3 — Sohbet de kodu araştırabilir

**Geliştirici** olarak, **normal sohbette "bu servis nerede kullanılıyor?" diye
sorduğumda doğru cevap almak** istiyorum, çünkü **her soru için kip değiştirmek
istemem**.

Kabul kriterleri:

- [x] Sohbet kipinde model gerekirse kodu araştırabilir; gerekmezse araştırmaz
- [x] Sohbet kipi hiçbir dosyayı değiştiremez
- [x] Sohbette cevaplar kısa kalır; Uzman yapısı kullanılmaz
- [x] Araştırma yapan sorularda hangi adımın atıldığı cevabın üstünde görünür

### H4 — Çok dosyalı düzenleme kesin yerlerle çalışır

**Geliştirici** olarak, **"bu metodu değiştir ve bütün çağıranları güncelle"
dediğimde gerçekten bütün çağıranların bulunmasını** istiyorum, çünkü
**birini atlamak derlenmeyen veya yanlış çalışan kod bırakır**.

Kabul kriterleri:

- [x] Çok dosyalı kip H2'deki ilişkileri kullanabilir
- [x] 008'in tüm kuralları geçerlidir: önizleme, tek geri alma, durdurma, sınırlar

### H5 — Araştırma görünür ve durdurulabilir

**Geliştirici** olarak, **araştırma sürerken ne yapıldığını görmek ve
durdurabilmek** istiyorum, çünkü **derin bir araştırma uzun sürebilir**.

Kabul kriterleri:

- [x] Sohbet ve Uzman kipinde araştırma adımları görünür
- [x] Her an durdurulabilir; durdurulunca o ana kadarki bilgiyle yarım cevap
      verilmez, durdurulduğu söylenir
- [x] Adım sayısı sınırlıdır; sınır Uzman'da Sohbet'ten yüksektir ve ayardan değişir
- [x] Sınıra ulaşılırsa bu söylenir

### H6 — Seçili kodu Uzman'a sorabilirim

**Geliştirici** olarak, **editörde seçtiğim kodu sağ tıkla Uzman'a sormak**
istiyorum, çünkü **en sık soracağım şey önümdeki kod**.

Kabul kriterleri:

- [x] "Seçili kodu açıkla" eylemi seçimi Uzman kipinde sorar
- [x] Seçimin hangi dosyada ve hangi satırlarda olduğu Uzman'a bildirilir

---

## Davranış Kuralları

- **Hiçbir iddia kaynaksız değildir.** Çağıran, çağrılan, uygulama ve mimari
  ilişkiler bulunan bir yere dayanır; dosya ve satır gösterilir.
- **Uzman ve Sohbet yalnızca okur.** Düzenleme yeteneği bu iki kipe hiç verilmez;
  bu bir yönerge değil, yetenek sınırıdır.
- **Sohbette araştırma ayardan kapatılabilir** (varsayılan açık). Kapalıyken
  istek, 002'den beri gönderilenle birebir aynıdır. Sunucu araç çağırmayı
  desteklemiyorsa sohbet bozulmaz: araçsız gönderilir ve bu bir kez söylenir.
- **IDE dizini hazır değilken** (proje açılırken dizinleme sürüyorsa) ilişki
  araması yapılmaz; bu söylenir ve metin aramasına düşülür.
- **Kip seçimi panelde görünür kalır** (008 T93); hangi kipte olunduğu yazarken
  bellidir.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Sembol bulunamadı | "Bulunamadı" denir; benzer adlar varsa önerilir |
| Aynı adla birden fazla aday | Hepsi listelenir; model hangisini kastettiğini seçer veya sorar |
| Dil, ilişki aramasını desteklemiyor | Metin aramasına düşülür ve cevapta bu belirtilir |
| Dizinleme sürüyor | Metin aramasına düşülür; "dizinleme bitince daha kesin cevap verilebilir" denir |
| Çok fazla kullanım | İlk N gösterilir, toplam sayı söylenir |
| Adım sınırı | Durur, sınıra ulaşıldığı söylenir |
| Kullanıcı durdurdu | "Durduruldu"; yarım cevap tamamlanmış gibi sunulmaz |

---

## Belirsizlikler

- [x] **IDE'nin ilişki bilgisi dilden bağımsız alınabiliyor mu?**
      → **Cevap: evet.** Deneme (2026-10-22, gerçek IDE): Java, JavaScript ve
      Kotlin'de kullanımlar, uygulamalar ve çağrı hiyerarşisi desteği
      dile özgü hiçbir şeye bağlanmadan alındı. Aynı adlı altı geçişten yalnızca
      gerçekten ilgili olan çağrı döndü; metin araması altısını da döndürürdü.

- [x] **Cevap nerede görünür?**
      → **Cevap: sohbet panelinde**, takip sorusu sorulabilir (kullanıcı kararı).

- [x] **Araştırma araçları ne zaman kullanılır?**
      → **Cevap:** Sohbette gerekirse, Uzman'da her zaman; ikisinde de yalnızca
      okuma (kullanıcı kararı).

- [x] **Polimorfik çağrılar nasıl bulunur?**
      Denemede bir uygulama metodu arandığında, arayüz üzerinden yapılan çağrı
      gelmedi.
      → **Cevap:** Planda dilden bağımsız bir "üst tanım" yolu aranır. Yoksa
      model uygulamaları ve kullanımları birleştirerek ilerler ve bu sınırı
      cevapta açıkça söyler (kullanıcı kararı, 2026-10-22).

- [x] **Adım sınırları?**
      → **Cevap: Sohbet 6, Uzman 15**, ikisi de ayardan değişir. Ölçümde
      yetmezse değiştirilir (kullanıcı kararı).

- [x] **"Mimarideki yeri" neye dayanır?**
      → **Cevap:** Paket/dizin yapısı, adlandırma ve ilişkilerden **çıkarım**;
      cevap bunun bir çıkarım olduğunu söyler (kullanıcı kararı).

Kullanıcı notu: ölçümde istenen sonuç alınamazsa bu kararlar gözden geçirilir.

## Bağımlılıklar

- [002 — Sohbet paneli](../002-sohbet-paneli/) — cevabın gösterildiği yer, takip soruları.
- [003 — Seçim aksiyonları](../003-secim-aksiyonlari/) — "Seçili kodu açıkla" eylemi.
- [008 — Çok dosyalı düzenleme](../008-cok-dosyali-duzenleme/) — araç çağırma
  döngüsü, sınırlar, durdurma, kip seçici.
