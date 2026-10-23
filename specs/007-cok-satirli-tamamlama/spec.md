# Spec: Çok Satırlı Kod Tamamlama

- **Spec no:** 007
- **Tarih:** 2026-09-04
- **Durum:** Uygulandı — *2026-09-04*

---

## Problem

[006](../006-satir-ici-tamamlama/) ile satır içi tamamlama çalışıyor, ama
bilinçli olarak **tek satırla** sınırlı. Bu sınır, önerinin en işe yarayacağı
anda devreye giriyor.

Geliştirici bir fonksiyon gövdesine yeni girdiğinde niyeti bellidir: imza,
parametre adları ve çevredeki kod ne yapılacağını büyük ölçüde söyler. Ama şu
an yalnızca ilk satır öneriliyor. Kullanıcı onu kabul edip bir sonraki satır
için yeniden bekliyor, sonra bir daha. Beş satırlık bilinen bir kalıp beş ayrı
tur sürüyor ve her turda odak bölünüyor.

Tek satır sınırı, özelliğin en çok değer üreteceği durumu — boş bir gövdeyi
doldurmayı — en zahmetli hale getiriyor.

## Amaç

Geliştirici bir bloğun içine girip yazmaya ara verdiğinde, o bloğa uygun
**birden fazla satırlık** bir devam önerisi görecek; tek tuşla tamamını kabul
edip yazmaya devam edebilecek. Satır ortasında yazarken davranış değişmeyecek.

## Kapsam dışı

- **Önerinin bir kısmını kabul etmek.** Öneri bütün olarak kabul edilir veya
  yok sayılır. *(Bilinçli ödünleşme — bkz. Belirsizlik 3.)*
- **Birden fazla bloğu veya fonksiyonu üretmek.** Öneri, imlecin bulunduğu
  bloğun içinde kalır.
- **Öneri kalitesini modelden bağımsız iyileştirmek.** Model yanlış kod
  üretiyorsa bu spec onu düzeltmez; yalnızca önerinin *nerede biteceğine*
  karar verir.
- **Birden fazla alternatif öneri arasında gezinme.** Tek öneri gösterilir.
- **Satır içi tamamlamanın kendisi** → [006](../006-satir-ici-tamamlama/)

---

## Kullanıcı Hikâyeleri

### H1 — Blok içinde çok satırlı öneri

**Geliştirici** olarak, **bir fonksiyon gövdesine girdiğimde birden fazla
satırlık öneri görmek** istiyorum, çünkü **bilinen bir kalıbı satır satır kabul
etmek her turda odağımı bölüyor**.

Kabul kriterleri:

- [x] İmlecin solunda yalnızca boşluk varken (boş satır veya yeni satır başı)
      yazmaya ara verilince birden fazla satırlık öneri belirir
- [x] Öneri, imlecin bulunduğu bloğun girinti seviyesinde kalır
- [x] Öneri tek tuşla bütünüyle kabul edilir ve **tek geri alma adımıyla** geri alınır
- [x] Yazmaya devam edilince öneri anında kaybolur
- [x] Öneri, altındaki mevcut kodu gizlemez veya kaydırmaz — okunabilir kalır

### H2 — Satır ortasında davranış değişmez

**Geliştirici** olarak, **satır ortasında yazarken bugünkü kısa öneriyi
görmeye devam etmek** istiyorum, çünkü **`int x = ` yazarken beş satırlık bir
öneri görmek rahatsız edici ve neredeyse hep yanlış olur**.

Kabul kriterleri:

- [x] İmlecin solunda boşluktan başka bir şey varsa öneri tek satır kalır
- [x] Üye erişiminden hemen sonra (`nesne.` gibi) hiç öneri üretilmez —
      006'da alınan karar aynen geçerlidir
- [x] Bu iki durumda 006'daki davranışta hiçbir değişiklik olmaz

### H3 — Önerinin nerede bittiği tahmin edilebilir

**Geliştirici** olarak, **önerinin bulunduğum bloğun dışına taşmamasını**
istiyorum, çünkü **kapanış satırını tekrar yazan veya yeni bir fonksiyona
başlayan bir öneri, kabul edilirse kodu bozar**.

Kabul kriterleri:

- [x] Öneri, imlecin bulunduğu bloktan çıkan ilk satırda biter
- [x] Blok içindeki boş satırlar öneriyi erken bitirmez
- [x] Öneri hiçbir koşulda belirlenen satır üst sınırını aşmaz
- [x] Model durmadan üretmeye devam etse bile öneri sınırlı kalır

### H4 — Yazma akıcılığı korunur

**Geliştirici** olarak, **çok satırlı öneri açıkken de yazmanın akıcı
kalmasını** istiyorum, çünkü **006'da bu kriter zaten sınırdaydı ve daha uzun
öneri üretmek daha çok zaman alır**.

Kabul kriterleri:

- [x] Uzun bir dosyada hızlı yazarken gecikme hissi oluşmaz
- [x] Öneri geç gelirse veya hiç gelmezse yazma etkilenmez
- [x] Çok satırlı öneri, tek satırlı öneriden gözle görülür ölçüde daha geç gelmez

---

## Davranış Kuralları

- **Kip kararı kullanıcıya sorulmaz.** Tek satır mı çok satır mı olacağına
  imlecin konumu karar verir. Ayardan seçtirmek, kullanıcıyı her bağlam
  değişiminde ayara gitmeye zorlardı.
- **006'nın hiçbir güvenlik kararı gevşetilmez.** Özellik varsayılan kapalıdır,
  kapalıyken hiç istek gönderilmez, hatalar sessizce yutulur, kullanıcının kodu
  günlüğe yazılmaz.
- **Öneri asla kullanıcı onayı olmadan koda yazılmaz.**
- Daha uzun öneri üretmek, **tek satırlı önerinin** gecikmesini artırmamalıdır.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Model bloğun dışına taşan kod üretti | Taşan kısım atılır, kalanı gösterilir |
| Model hiç durmadı, üst sınıra dayandı | Öneri üst sınırda kesilir, yine de gösterilir |
| Kesme sonrası anlamlı bir şey kalmadı | Öneri gösterilmez |
| Öneri geç geldi, imleç oynadı | Öneri atılır |
| Sunucu hata verdi | Öneri gösterilmez, kullanıcı rahatsız edilmez |

---

## Belirsizlikler

- [x] **Daha uzun öneri üretmek ne kadar sürüyor?**
      → **Cevap: fark yok.** `qwen/qwen3.8-27b` ile ölçüldü (2026-09-04):
      `max_tokens` 64 de olsa 256 da olsa model **aynı 24 token'ı** üretip
      duruyor. Üst sınır bir tavan, maliyet değil.

      Bu, H4 riskini tasarım seviyesinde ortadan kaldırıyor: daha uzun öneriye
      izin vermek, kısa öneriyi yavaşlatmıyor. Yine de yazma akıcılığı
      uygulamadan sonra elle doğrulanacak — ölçüm tek bir prompt üzerinden.

- [x] **Öneri nerede bitmeli?**
      → **Cevap:** İmlecin bulunduğu bloğun **girinti seviyesine** göre.
      Gerekçe: dilden bağımsız çalışır ve dile özgü kod çözümleme gerektirmez
      (Altın Kural 3). Python'da girinti zaten yapının kendisidir; süslü
      parantez kullanan dillerde kapanış satırı doğal sınırı verir.

      Elenen alternatifler: modelin durduğu yere güvenmek (modele göre değişir,
      model değişince davranış da değişir), sabit satır sayısı (yapıyı hiç
      dikkate almaz, öneriyi cümlenin ortasında keser).

- [x] **Önerinin bir kısmı kabul edilebilmeli mi?**
      → **Cevap:** **Hayır, hepsi ya da hiçbiri.**
      Gerekçe: öğrenilecek yeni tuş olmaması tercih edildi.

      ⚠️ **Bilinen ödünleşme:** Bu karar, kötü önerinin maliyetini artırır. Altı
      satırlık bir önerinin dördü doğruysa kullanıcı ya hepsini alıp iki satır
      silecek ya da hiçbirini alamayacak. Kullanımda rahatsız edici çıkarsa
      kısmi kabul ayrı bir spec olarak açılır — bu spec onu engellemez.

- [x] **Kip nasıl seçilsin?**
      → **Cevap:** İmlecin konumundan **otomatik**. Ayardan seçtirilmez.

---

## Uygulama sonrası notlar

Kabul kriterlerinin tamamı 2026-09-04'te `runIde` ile gerçek modele karşı elle
doğrulandı.

Spec yazılırken öngörülmeyen üç şey:

1. **Model çıktının ilk satırını bazen girintisiz yazıyor** — aynı sunucu, aynı
   ayar, farklı fonksiyon. Kesme eşiğinin modelin kendi hizasından türetilmesi
   gerekti; plandaki "bizim girintimiz eşiktir" varsayımı yanlıştı.
2. **`maxSatir` varsayılanı öneriyi şekillendiriyordu.** 6 iken sıradan bir
   gövdenin `return`'ünü kesiyordu; 12 yapıldı.
3. **Sessiz hata, teşhissiz hataya dönüşmüştü.** Öneri gelmediğinde sebebini
   görmenin yolu yoktu; debug seviyesinde ölçü kaydı eklendi.

Üçü de ancak **gerçek model çıktısıyla** çalışılınca görüldü.

## Bağımlılıklar

- [006 — Satır içi kod tamamlama](../006-satir-ici-tamamlama/) — tamamlama
  altyapısı, ayarlar ve güvenlik kararları oradan gelir. Bu spec onun
  "tek satır" kararını yalnızca **belirli bağlamlarda** genişletir.
