# Spec: Paketleme ve Adlandırma

- **Spec no:** 004
- **Tarih:** 2026-09-03
- **Durum:** Uygulandı

---

## Problem

Eklentinin görünen adı koda gömülüydü. Farklı bir ekibe veya kuruma kendi adıyla
dağıtmak için kaynak kodu değiştirmek gerekiyordu — hem hataya açık hem de her
dağıtım için ayrı bir kod dalı anlamına geliyordu.

Ayrıca adın değiştirilmesi eklentinin **kimliğini** de değiştirirse IDE bunu başka
bir eklenti sayar; kullanıcıda yan yana iki kopya oluşur ve güncellemeler bozulur.

## Amaç

Dağıtımı yapan kişi, eklentinin görünen adını tek bir yapılandırma dosyasından
belirleyip paketleyebilecek. Kimlik değişmediği için yeniden adlandırılmış paket,
IDE tarafından aynı eklentinin yeni sürümü olarak görülecek.

## Kapsam dışı

- **Kurum içi otomatik güncelleme.** Ayrı spec olacak. Bu spec yalnızca paketin
  adını belirler; nasıl dağıtıldığını değil.
- **Simge (ikon) değiştirme.** Şimdilik yalnızca ad.
- **Çalışma zamanında ad değiştirme.** Ad build sırasında sabitlenir.

---

## Kullanıcı Hikâyeleri

### H1 — Paket kendi adıyla üretilir

**Dağıtımı yapan kişi** olarak, **eklentinin görünen adını paketleme sırasında
belirlemek** istiyorum, çünkü **farklı ekip veya kurumlara kendi adıyla dağıtmak
gerekebilir**.

Kabul kriterleri:

- [x] Görünen ad tek bir yapılandırma dosyasından verilir; kaynak kodda aranmaz
- [x] Ad verilmezse paketleme anlaşılır bir hatayla durur — sessizce yanlış adla
      paketlenmez
- [x] Türkçe karakter içeren ad bozulmadan görünür
- [x] Verilen ad eklenti listesinde görünür
- [x] Verilen ad araç penceresi başlığında görünür
- [x] Verilen ad ayarlar sayfası başlığında görünür
- [x] **Eklentinin kimliği ada göre değişmez.** Ad değiştirilip yeniden paketlenen
      sürüm, IDE tarafından *aynı* eklentinin yeni sürümü olarak görülür —
      yan yana ikinci bir kopya kurulmaz

---

## Davranış Kuralları

- Eklenti kimliği (`dev.acp.assistant`) **hiçbir koşulda değiştirilmez.**
- Sürüm numarası tutarlı ilerler; otomatik dağıtıma geçildiğinde sürüm geçmişinin
  bozulmaması gerekiyor.

## Hata Durumları

| Durum | Beklenen davranış |
|-------|-------------------|
| Ad tanımlı değil | Paketleme durur, eksik ayarın adı söylenir |
| Ad özel karakter içeriyor | Kaçırılarak yazılır, bozulmadan okunur |
| Üretilen kaynak pakete girmemiş | Eklenti çökmez; acil düşüş adı kullanılır |

---

## Belirsizlikler

Yok.

## Bağımlılıklar

Yok — bağımsız uygulanabilir.
