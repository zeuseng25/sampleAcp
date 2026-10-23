# Spec: Kurum İçi Dağıtım

- **Spec no:** 005
- **Tarih:** 2026-09-04
- **Durum:** Onaylandı — **beklemede** (altyapı bilgisi bekliyor)

---

## Problem

Eklenti çalışıyor ama yalnızca onu derleyen kişinin makinesinde. Ekibe ulaşmasının
tek yolu paketi elle göndermek.

Bunun iki sonucu var:

**Kurulum zahmetli.** Her ekip üyesine dosya iletmek, nereye kurulacağını anlatmak
ve IDE'yi yeniden başlatmasını beklemek gerekiyor. Yeni katılan biri için bu her
seferinde tekrarlanıyor.

**Güncelleme hiç olmuyor.** Elle kurulan bir eklenti kendini güncellemez. Prompt
iyileştirmesi, hata düzeltmesi, yeni özellik — hepsi yayınlandığı yerde kalır.
Ekip aylarca eski sürümle çalışır ve kimse fark etmez. Şimdiye kadar düzeltilen
kırpılma, sessiz bağlam hatası, sonsuza kadar bekleme gibi sorunlar da eski
sürümdeki kullanıcılarda durmaya devam eder.

Bu spec olmazsa: eklentiye harcanan emek tek kişiye hizmet eder ve zamanla
kullanılamaz hâle gelir.

## Amaç

Ekip üyesi eklentiyi bir kez tanımlar; sonrasında yeni sürümler IDE'nin kendi
güncelleme akışında görünür ve tek tıkla kurulur. Dağıtımı yapan kişi yeni sürümü
tek bir komutla yayınlayabilir.

## Kapsam dışı

- **Herkese açık mağazada yayınlama.** Kurum içi kalıyor; imzalama, uyum kuralları
  ve geriye dönük sürüm desteği bu spec'te yok.
- **Otomatik sürüm numarası artırma.** Sürüm elle belirlenir.
- **Sürüm geri alma (rollback).** Yanlış sürüm yayınlanırsa düzeltilmiş yeni bir
  sürüm yayınlanır.
- **Kullanım ölçümü / telemetri.** Kimin hangi sürümü kullandığı toplanmaz.
- **Eklentinin kendini güncellemesi.** Güncelleme IDE'nin kendi mekanizmasıyla
  olur; eklenti kendi indirme mantığını yazmaz.

---

## Kullanıcı Hikâyeleri

### H1 — Ekip üyesi eklentiyi kurar

**Ekip üyesi** olarak, **eklentiyi tek seferlik bir tanımla kurmak** istiyorum,
çünkü **dosya alıp elle kurmak zahmetli ve her yeni kişide tekrarlanıyor**.

Kabul kriterleri:

- [ ] Kurulum adımları tek sayfalık bir belgede yazılı ve kopyalanabilir
- [ ] Kullanıcı kurum içi adresi bir kez tanımlayınca eklentiyi IDE'nin kendi
      eklenti ekranından bulup kurabilir
- [ ] Kurulum dört IDE'de de aynı adımlarla yapılır
- [ ] Kurulum sırasında dosya indirip elle yerleştirmek gerekmez

### H2 — Yeni sürüm kullanıcıya ulaşır

**Ekip üyesi** olarak, **yeni sürüm çıktığında haberdar olmak** istiyorum, çünkü
**düzeltmelerden ve iyileştirmelerden habersiz eski sürümle çalışmak istemiyorum**.

Kabul kriterleri:

- [ ] Yeni sürüm yayınlandığında IDE bunu kendi güncelleme akışında gösterir
- [ ] Güncelleme tek tıkla kurulur
- [ ] Kullanıcının ayarları ve API anahtarı güncellemeden sonra korunur
- [ ] Eski sürümden yeni sürüme geçiş, yan yana ikinci bir kopya oluşturmaz

### H3 — Yayınlayan kişi yeni sürüm çıkarır

**Dağıtımı yapan kişi** olarak, **yeni sürümü tek komutla yayınlamak** istiyorum,
çünkü **elle dosya kopyalamak hataya açık ve zamanla yapılmaz olur**.

Kabul kriterleri:

- [ ] Yeni sürüm tek bir komutla paketlenip yayınlanır
- [ ] Yayınlanan sürümün numarası pakette ve dağıtım kaydında **aynıdır**
- [ ] Aynı sürüm numarası ikinci kez yayınlanmaya çalışılırsa uyarı verir —
      sessizce üzerine yazmaz
- [ ] Yayınlama öncesi testler çalışır; testler kırmızıysa yayınlanmaz

---

## Davranış Kuralları

- Eklentinin **kimliği asla değişmez**. Kimlik değişirse IDE yeni sürümü ayrı bir
  eklenti sayar ve kullanıcıda iki kopya oluşur.
- Sürüm numaraları **artan** sırada ilerler; bir sürüm yayınlandıktan sonra
  içeriği değiştirilmez.
- Dağıtım kaydı ve paket **birlikte** güncellenir; ikisinin ayrışması kullanıcıya
  bozuk güncelleme olarak yansır.
- Kurulum belgesi Türkçedir.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Dağıtım adresine ulaşılamıyor | IDE kendi hatasını gösterir; eklenti çalışmaya devam eder |
| Dağıtım kaydı bozuk | Yayınlama adımı bunu **yayınlamadan önce** yakalar |
| Aynı sürüm ikinci kez yayınlanıyor | Uyarı verilir, üzerine yazılmaz |
| Testler kırmızı | Yayınlama yapılmaz |

---

## Belirsizlikler

Cevaplanmadan `plan.md`'ye geçilemez.

- [x] Paket nerede duracak?
      → **Cevap:** Mevcut kurumsal **artifact deposu**. Kimlik doğrulama ve
      yedekleme hazır geliyor.

- [x] Yayınlama nereden yapılacak?
      → **Cevap:** Geliştiricinin makinesinden **elle**, tek komutla. CI'a
      taşımak sonraki bir adım; komut CI'dan da çalışabilecek şekilde yazılacak.

- [x] Depodan okuma kimlik doğrulama istiyor mu?
      → **Cevap:** **Hayır**, kurum ağından anonim okuma açık. Yazma kimlik
      istiyor ama o yalnızca yayınlayanı ilgilendiriyor.
      ⚠️ Bu, IDE'nin özel eklenti deposu tanımının çalışması için gereken en
      önemli koşul. Yanlış çıkarsa dağıtım yeri değişmek zorunda kalır —
      bu yüzden **ilk görevlerden biri** bunu gerçek depoya karşı doğrulamak.

- [x] Sürüm numarası nasıl belirlenecek?
      → **Cevap:** **Elle**, `gradle.properties` içinde. Yayınlama komutu aynı
      sürümün ikinci kez çıkmasını engelleyecek.

## Bağımlılıklar

- [004 — Paketleme ve adlandırma](../004-paketleme-ve-adlandirma/) — sürüm ve
  kimlik ayrımı orada kuruldu.
