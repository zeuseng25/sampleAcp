# Spec: Seçim Aksiyonları

- **Spec no:** 003
- **Tarih:** 2026-09-03
- **Durum:** Uygulandı

---

## Problem

[002](../002-sohbet-paneli/) ile geliştirici modele soru sorabiliyor olacak, ama
sık tekrarlanan istekleri (bunu açıkla, buna test yaz, bunu yeniden düzenle) her
seferinde elle yazmak gerekiyor. Ayrıca modelin ürettiği kodu editöre taşımak
kopyala-yapıştırla yapılıyor — bu hem yavaş hem de yanlış yere yapıştırma riski
taşıyor.

## Amaç

Geliştirici, seçtiği koda sağ tıklayıp hazır bir eylem çalıştırabilecek; üretilen
değişikliği uygulamadan önce karşılaştırmalı olarak görebilecek ve uyguladıktan
sonra tek adımda geri alabilecek.

## Kapsam dışı

- **Kendi başına birden fazla dosya değiştiren otomatik asistan.** Buradaki her
  değişikliği kullanıcı tek tek onaylar. Ayrı spec.
- **Editör içinde satır satır kabul/reddet arayüzü.** v1'de karşılaştırma ayrı
  pencerede gösterilir.
- **Testin doğru kaynak klasörüne ve pakete yerleştirilmesi.** v1'de sonuç ayrı
  bir geçici dosyada gösterilir.

---

## Kullanıcı Hikâyeleri

### H1 — Hazır eylemler

**Geliştirici** olarak, **seçtiğim koda sağ tıklayıp hazır bir eylem seçmek**
istiyorum, çünkü **her defasında aynı isteği yazmak zaman kaybı**.

Kabul kriterleri:

- [x] Kod seçiliyken sağ tık menüsünde ve hızlı düzeltme menüsünde dört eylem
      görünür: açıkla, test yaz, yeniden düzenle, doküman ekle
- [x] Eylemler IDE'nin Keymap ekranında listelenir; kullanıcı isterse kısayol atayabilir
- [x] Hiçbir şey seçili değilken bu eylemler pasif görünür
- [x] Eylem seçildiğinde sonuç sohbet panelinde akar; kullanıcı ilerlemeyi görür
      ve iptal edebilir
- [x] Dosyanın türüne göre uygun bir istek gönderilir — aynı eylem farklı dillerde
      dile uygun sonuç verir
- [x] Eylem çalışırken IDE donmaz

### H2 — Sonucu koda uygulama

**Geliştirici** olarak, **modelin önerdiği değişikliği uygulamadan önce görmek ve
tek adımda geri alabilmek** istiyorum, çünkü **kodumun sessizce bozulmasını
istemiyorum**.

Kabul kriterleri:

- [x] "Yeniden düzenle" ve "doküman ekle" sonucunda önce **karşılaştırmalı
      önizleme** gösterilir; kullanıcı onaylamadan dosya değişmez
- [x] Uygulanan değişiklik **tek geri alma adımıyla** tamamen geri alınır
- [x] Geri alma listesinde değişikliğin ne olduğu okunabilir bir adla görünür
- [x] Yanıt beklenirken kullanıcı o kodu değiştirdiyse değişiklik **uygulanmaz**;
      durum açıkça bildirilir ve sonucu kopyalama seçeneği sunulur
- [x] Uygulanan kod projenin biçimlendirme kurallarına göre düzenlenir
- [x] "Test yaz" mevcut dosyayı **değiştirmez**; sonucu ayrı bir yerde gösterir
- [x] "Açıkla" hiçbir dosyayı değiştirmez

---

## Davranış Kuralları

- Kullanıcıya gösterilen tüm metinler Türkçedir.
- Hiçbir değişiklik kullanıcı onayı olmadan diske yazılmaz.
- Eklenti dört IDE'de de aynı şekilde çalışır — dile özgü API kullanılmaz.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Uygulanacak kod bu arada değişmiş | Uygulama yapılmaz, durum bildirilir, kopyalama sunulur |
| Model kod bloğu döndürmedi | Uygulama önerilmez; sonuç yalnızca panelde gösterilir |
| Dosya salt okunur | Uygulama reddedilir, sebebi söylenir |

---

## Belirsizlikler

- [x] Dört eylemin prompt metinleri nerede tutulsun?
      → **Cevap:** Pakette, kullanıcı **düzenleyemez**. Prompt'lar ürün davranışıdır,
      ayar değil: iyileştirme sürümle gelir ve herkes aynı kaliteyi alır. Kullanıcı
      düzenleyebilseydi bozuk prompt'lar destek yükü yaratır, ayrıca prompt
      iyileştirmesi yayınlandığında kullanıcının eski kopyası kalırdı.

      Not: Altın Kural 6 ("ayarlanabilir değerin tek tanım yeri State") burada
      geçerli değil — prompt bir yapılandırma değeri değil, ürünün kendisi.

- [x] Eylemlere klavye kısayolu atanacak mı?
      → **Cevap:** Hayır. Sağ tık menüsü ve Alt+Enter yeterli. IDE'nin kısayol
      alanı zaten dolu ve ekipte farklı keymap kullananlar var. Eylemler Keymap
      ekranında görünecek; isteyen kendisi atar.

## Bağımlılıklar

- [001 — Çekirdek ve model bağlantısı](../001-cekirdek-ve-model-baglantisi/)
- [002 — Sohbet paneli](../002-sohbet-paneli/) — sonuç panelde akıyor
