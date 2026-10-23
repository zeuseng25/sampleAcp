# Spec: Derleme Hatasını Düzeltme

- **Spec no:** 009
- **Tarih:** 2026-10-22
- **Durum:** Uygulandı — *2026-10-23*

---

## Problem

Çok dosyalı düzenleme ([008](../008-cok-dosyali-duzenleme/)) dosyaları okuyup
değiştirebiliyor ama **projeyi derleyemiyor veya testlerini koşturamıyor**.
Geliştirici bir derleme hatası gördüğünde hatayı kendisi okuyor, ilgili yerleri
kendisi buluyor ve eklentiye elle tarif ediyor.

Ajan hatanın tam metnini göremediği için ya tahmin yürütüyor ya da geliştiricinin
uzun çıktıyı kopyalayıp yapıştırmasını gerektiriyor. Düzeltmeyi yaptıktan sonra
da işe yarayıp yaramadığını kendisi göremiyor.

## Amaç

Geliştirici çok dosyalı kipte bir hata olduğunu söylediğinde, ajan projenin
derleme veya test komutunu **geliştiricinin onayıyla** çalıştırabilecek, hata
satırlarını okuyacak ve 008'deki gibi önizlenip onaylanan bir düzeltme
hazırlayacak.

## Kapsam dışı

- **Her değişiklikten sonra kendiliğinden derleme.** Derleme yalnızca ajan
  istediğinde ve geliştirici onayladığında çalışır (kullanıcı kararı).
- **Modelin komut seçmesi.** Hangi komutun çalışacağını model belirlemez;
  proje türünden tahmin edilir veya kullanıcı belirler. Model yalnızca
  "doğrulamak istiyorum" diyebilir.
- **Uygulanmamış değişikliği derlemek.** Komut diskteki kodla çalışır; önizlemede
  bekleyen, henüz uygulanmamış değişiklik derlenmez. Geçici kopya yapılmaz.
- **Serbest terminal erişimi.** Bu bir terminal değil; yalnızca tek bir, önceden
  belirlenmiş doğrulama komutu çalışır.
- **Bağımlılık yükleme, yayınlama, dağıtım** gibi doğrulama dışı komutlar.

---

## Kullanıcı Hikâyeleri

### H1 — Hatayı bildiririm, ajan görür

**Geliştirici** olarak, **"derlenmiyor" veya "test kırılıyor" dediğimde ajanın
hatayı kendisinin görmesini** istiyorum, çünkü **uzun bir derleme çıktısını
kopyalayıp yapıştırmak ve hangi kısmın önemli olduğunu seçmek zaman alıyor**.

Kabul kriterleri:

- [x] Çok dosyalı kipte hata bildirildiğinde ajan doğrulama komutunu çalıştırmayı isteyebilir
- [x] Ajan, hatayı geliştirici zaten yapıştırdıysa komut çalıştırmadan ilerleyebilir
- [x] Ajan hata satırlarını okuyup ilgili dosyaları kendisi bulur
- [x] Hazırlanan düzeltme 008'in tüm kurallarına uyar: önizleme, tek geri alma, durdurma

### H2 — Hiçbir komut sormadan çalışmaz

**Geliştirici** olarak, **her komut çalışmadan önce ne çalışacağını görüp
onaylamak** istiyorum, çünkü **derleme dakikalar sürebilir ve makinemde
beklemediğim bir şeyin çalışmasını istemem**.

Kabul kriterleri:

- [x] Ajan komut çalıştırmak istediğinde çalışacak komut tam metniyle gösterilir
- [x] Geliştirici "Çalıştır" veya "Atla" seçer; seçim yapılmadan hiçbir şey çalışmaz
- [x] "Atla" seçilirse ajan bunu öğrenir ve elindeki bilgiyle devam eder
- [x] Çalışırken bu görünür ve her an durdurulabilir; durdurulunca komut ve başlattığı süreçler gerçekten sonlanır
- [x] Sonuç ajana ve geliştiriciye açıkça bildirilir: başarılı, başarısız, zaman aşımı veya durduruldu

### H3 — Komut proje sınırının dışına çıkmaz

**Geliştirici** olarak, **komutun yalnızca proje dizininde çalıştığından emin
olmak** istiyorum, çünkü **makinemde başka projeler ve kişisel dosyalar var**.

Kabul kriterleri:

- [x] Komut her zaman proje kök dizininde başlar
- [x] Proje dizini dışını gösteren bir komut reddedilir ve sebebi söylenir
- [x] Komut, birden fazla komutu zincirleyen veya çıktıyı yönlendiren bir kabuk üzerinden çalışmaz

### H4 — Hangi komutun çalışacağı belli ve değiştirilebilir

**Geliştirici** olarak, **hangi komutun çalışacağını görmek ve gerekirse
değiştirmek** istiyorum, çünkü **her projenin derleme ve test biçimi farklıdır**.

Kabul kriterleri:

- [x] Komut, projenin türünden tahmin edilir
- [x] Kullanıcı komutu proje bazında değiştirebilir; seçim IDE yeniden başlatıldığında korunur
- [x] Komut tahmin edilemiyor ve tanımlı da değilse ajan komut isteyemez; kullanıcıya nasıl tanımlayacağı söylenir

---

## Davranış Kuralları

- **Her çalıştırma ayrı onay ister.** Bir görevde ikinci kez istenirse yeniden sorulur.
- **Komutu model seçmez, değiştiremez, argüman ekleyemez.** Modelin yazdığı metin
  hiçbir zaman komut olarak çalıştırılmaz.
- **Komut diskteki kodla çalışır.** Aynı görevde önizlemede bekleyen düzenlemeler
  derlemeye dahil değildir; ajan bunu bilir.
- **Aynı anda tek komut.** Biri çalışırken yenisi istenemez.
- **Modele giden çıktı kırpılır, hata satırları korunur.** Derleme çıktısı on
  binlerce satır olabilir; tamamını göndermek bağlamı doldurur.
- **Komut, geliştiricinin kendi yetkileri ve ortamıyla çalışır.** Kurum ağına
  erişmesi gereken derleme araçları IDE'nin gördüğü ortamı görür.
- **Onay beklerken ajan durdurulabilir;** durdurulursa komut çalışmaz.
- **Adım sınırı geçerlidir.** Komut isteği de bir adım sayılır (008 H3).

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Komut tahmin edilemedi, tanımlı değil | Ajan komut isteyemez; nasıl tanımlanacağı söylenir |
| Komutun programı makinede yok | "Bulunamadı" denir; ajan bunu kod hatası sanmaz |
| Zaman aşımı | Komut sonlandırılır, zaman aşımı olduğu söylenir |
| Kullanıcı durdurdu | Komut ve alt süreçleri sonlandırılır; "Durduruldu" |
| Kullanıcı "Atla" dedi | Komut çalışmaz; ajan atlandığını bilerek devam eder |
| Komut proje dışına işaret ediyor | Çalışmadan reddedilir, sebebi söylenir |
| Başarısız ama hata satırı bulunamadı | Çıktının son kısmı ajana ve kullanıcıya verilir |

---

## Belirsizlikler

- [x] **Derleme ne zaman çalışır?**
      → **Cevap: yalnızca ajan istediğinde ve geliştirici onayladığında.** Her
      değişiklikten sonra kendiliğinden çalışmaz; tipik tetikleyici geliştiricinin
      bir hata bildirmesidir (kullanıcı kararı, 2026-10-22).

      Elenen alternatif: uygulamadan sonra otomatik derleme. Büyük projede her
      uygulamada dakikalarca beklemek ve çoğu zaman gereksiz derlemek.

- [x] **Uygulanmamış değişiklik nasıl doğrulanır?**
      → **Cevap: doğrulanmaz.** Komut diskteki kodla çalışır (kullanıcı kararı).

      Elenen alternatif: geçici kopyada derleme — projeyi kopyalamak bellek ve
      disk maliyeti getirir, büyük projede yavaştır.

- [x] **Komutu kim belirler?**
      → **Cevap: proje türünden tahmin, ayardan değiştirilebilir.** Model
      yalnızca çalıştırmayı isteyebilir (kullanıcı kararı).

- [x] **Model komutu kendi kendine çalıştırabilir mi?**
      → **Cevap: hayır, her çalıştırma onaylanır** (kullanıcı kararı). Yönerge
      tek başına yetmez: T10'da yönerge belirleyici çıktı ama uyulacağının
      garantisi yok.

- [x] **"Proje dışına çıkmama" neyi kapsıyor?**
      → **Cevap: komutun başladığı dizin ve verilen yollar.** Derleme aracının
      kendi yazdığı yerler (kullanıcı dizinindeki paket deposu, önbellek)
      kapsam dışı — bunlar aracın davranışı, engellenirse derleme çalışmaz
      (kullanıcı kararı, 2026-10-22).

- [x] **Varsayılan zaman aşımı?**
      → **Cevap: 5 dakika, ayardan değiştirilebilir** (kullanıcı kararı, 2026-10-22).

## Bağımlılıklar

- [008 — Çok dosyalı düzenleme](../008-cok-dosyali-duzenleme/) — ajan döngüsü,
  araç çağırma, önizleme, tek geri alma ve adım sınırı oradan gelir.
