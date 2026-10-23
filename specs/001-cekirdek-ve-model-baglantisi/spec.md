# Spec: Çekirdek ve Model Bağlantısı

- **Spec no:** 001
- **Tarih:** 2026-09-03
- **Durum:** Onaylandı

> **Bu dosyanın kuralı:** Sadece **NE** ve **NEDEN** yazılır.

---

## Problem

Kuruma ait bir model sunucusu mevcut ve çalışıyor, ancak IDE içinden erişilebilir
değil. Herhangi bir arayüz yazılmadan önce çözülmesi gereken şey şu: eklenti,
kurumsal ağın arkasındaki bu sunucuya güvenilir biçimde bağlanabilmeli.

Kurumsal ağ TLS trafiğini kesiyor ve kendi sertifika otoritesini kullanıyor.
Dışarıdaki hazır eklentiler bu yüzden kurum içi sunucuya yönlendirilseler bile
çalışmıyor. Bu engel aşılmadan üstüne inşa edilecek her şey boşa gider.

## Amaç

Ekip üyesi eklentiyi kurup sunucu adresini ve erişim anahtarını bir kez
tanımlayabilecek, bağlantının çalıştığını ayarlar ekranından doğrulayabilecek ve
kullanılacak modeli sunucudan gelen listeden seçebilecek. Bu tamamlandığında
sohbet ve eylemler için sağlam bir temel hazır olur.

## Kapsam dışı

- **Sohbet arayüzü** → [002](../002-sohbet-paneli/)
- **Seçim eylemleri ve kodu değiştirme** → [003](../003-secim-aksiyonlari/)
- **Eklentinin görünen adı ve paketlenmesi** → [004](../004-paketleme-ve-adlandirma/)
- **Birden fazla sunucu arasında geçiş.** Tek sunucu yapılandırılır.

---

## Kullanıcı Hikâyeleri

### H1 — Sunucuyu tanımlama

**Ekip üyesi** olarak, **eklentiyi kurup tek seferde yapılandırmak** istiyorum,
çünkü **her açılışta ayar yapmak istemiyorum**.

Kabul kriterleri:

- [ ] Sunucu adresi, erişim anahtarı, model ve bağlam üst sınırı ayarlar
      ekranından girilir/seçilir; **hiçbiri koda gömülü değildir**
- [ ] Ayarlar IDE yeniden başlatıldığında korunur
- [ ] Erişim anahtarı işletim sisteminin güvenli saklama alanında tutulur; ayar
      dosyasında düz metin olarak **bulunmaz**
- [ ] Adres sonundaki eğik çizgi veya `/v1` eki fark etmeksizin aynı sonuca varılır
- [ ] Hiçbir ayar girilmemişken eklenti çökmez; kullanıcıyı ayarlara yönlendirir

### H2 — Bağlantıyı doğrulama

**Ekip üyesi** olarak, **girdiğim ayarların çalıştığını hemen görmek** istiyorum,
çünkü **hatayı ilk soruyu sorduğumda değil, ayarları girerken öğrenmek isterim**.

Kabul kriterleri:

- [ ] Ayarlar ekranında bir "bağlantıyı test et" düğmesi vardır
- [ ] Sonuç aynı ekranda, ayrı bir pencere açmadan gösterilir
- [ ] Test **kaydedilmiş değerleri değil, ekranda o an yazan değerleri** kullanır
- [ ] Test başarılıysa sunucudan dönen modeller listelenir ve kullanıcı
      **listeden seçer** — model adını elle yazmak zorunda kalmaz
- [ ] Test sırasında ayarlar ekranı donmaz
- [ ] Kurumsal ağdaki sertifika ve vekil sunucu yapılandırması **ek ayar
      gerektirmeden** çalışır

### H3 — Modelle konuşabilme (altyapı)

**Eklenti** olarak, **modele istek gönderip yanıtı parça parça alabilmek**
istiyorum, çünkü **üzerine kurulacak her özellik buna dayanıyor**.

Kabul kriterleri:

- [ ] Yanıt tamamını beklemeden, geldikçe parça parça iletilir
- [ ] İstek iptal edildiğinde bağlantı gerçekten kapanır ve **sunucu üretmeyi
      bırakır** — paylaşılan donanım boşa çalışmaz
- [ ] Model bir düşünme bölümü üretirse, bu bölüm cevap metninden ayrı olarak
      işaretlenir (sunucu ayrı alanda göndersin veya cevabın içinde göndersin)
- [ ] Bağlantı yanıt ortasında koparsa o ana kadar gelen metin kaybolmaz

---

## Davranış Kuralları

- **Ayarlanabilir her değer kullanıcı tarafından değiştirilebilir.** Makul
  varsayılanlar olabilir; ama bir değerin kullanıldığı yerde sabit durması ve
  ayarlardan değiştirilememesi hatadır. Sunucu adresi ile model adının varsayılanı
  **boştur** — kuruma özgü bilgi pakete gömülmez.
- Kullanıcıya gösterilen tüm metinler Türkçedir.
- Erişim anahtarı ve kullanıcı kodu hiçbir koşulda günlük kayıtlarına yazılmaz.
- İptal bir hata değildir; hata olarak gösterilmez.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Sunucu adresi veya model boş | Ayarlara yönlendiren açık uyarı; istek gönderilmez |
| Sunucuya ulaşılamıyor | "Sunucuya ulaşılamıyor" — adres/ağ/sertifika kontrolü önerilir |
| Erişim anahtarı yanlış | "Anahtar gerekli veya hatalı" — bağlantı hatasından ayrı mesaj |
| Model sunucuda yok | İsteğin reddedildiği ve model adının yanlış olabileceği söylenir |
| Yanıt ortasında bağlantı kopar | Gelen metin korunur, durum bildirilir |
| Sunucu beklenmeyen biçimde yanıt verir | Teknik ayrıntı günlüğe, kullanıcıya sade mesaj |

---

## Belirsizlikler

- [x] Ekip için önceden dolu gelecek sunucu adresi nedir?
      → **Cevap:** Önceden dolu gelmeyecek; kullanıcı girer. Koda gömülü kurum
      adresi **istenmiyor**.

- [x] Varsayılan model adı nedir?
      → **Cevap:** Varsayılan yok. Bağlantı testinden sonra sunucudan dönen
      listeden seçilir.

- [x] Bağlam için üst sınır ne olmalı?
      → **Cevap:** Bu da bir ayardır. İlk değeri `plan.md`'de gerekçesiyle
      önerildi (8000), sunucunun bildirdiği gerçek token kullanımı ölçüldükten
      sonra güncellenecek.

- [x] Model sunucusu düşünme bölümünü ayrı bir alanda mı gönderiyor?
      → **Cevap:** Evet, ayrı alanda gönderdiği **hatırlanıyor**.
      ⚠️ **Doğrulanmadı — varsayım.** Uygulama her iki durumu da karşılıyor:
      ayrı alan gelirse doğrudan kullanılır, cevabın içinde gelirse akış sırasında
      ayıklanır. Varsayım yanlış çıkarsa davranış bozulmaz.

## Bağımlılıklar

Yok — bu ilk spec. 002, 003 ve 004 buna dayanır.
