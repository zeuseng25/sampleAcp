# Görevler: Paketleme ve Adlandırma

- **Spec no:** 004 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı

---

- [x] T01 `AcpBranding` + testleri → 7 test yeşil (Türkçe karakter, eksik anahtar, boş değer, boşluk kırpma, dosya yok) ✅
- [x] T02 `generateBranding` Gradle görevi → `build/generated/branding/acp-branding.properties` üretilir, `processResources` ona bağlanır ✅
- [x] T03 `pluginConfiguration.name` `gradle.properties`'ten okunur → `-Pacp.displayName="Şirket Asistanı"` ile paketlendi, `plugin.xml`'de `<name>Şirket Asistanı</name>` çıktı ✅
- [x] T04 Kimlik sabitliği → aynı pakette `<id>dev.acp.assistant</id>` değişmeden kaldı ✅
- [x] T05 Türkçe karakter → `displayName=Şirket Asistanı` üretilen kaynakta ve pakette bozulmadan göründü ✅
- [x] T06 Araç penceresi başlığı → `toolWindow.stripeTitle` markalı addan geliyor ✅ *elle doğrulandı*
- [x] T07 Ayarlar sayfası başlığı → üretilen `AcpBrandingBundle`'dan geliyor ✅ *elle doğrulandı*

## Doğrulama ve kapanış

- [x] T90 Kabul kriterleri doğrulandı ✅
- [x] T91 `spec.md` durumu "Uygulandı" ✅

---

## Notlar

### Sıra dışı numara (2026-09-03)

Bu spec, 001 uygulanırken ortaya çıkan bir istek üzerine yazıldı ve aynı gün
uygulandı — yani 002 ve 003'ten önce hayata geçti. Numaralar oluşturulma sırasını
gösterir, uygulanma sırasını değil.

İlk başta bu istek 001'in içine (H6 olarak) yazılmıştı; spec bölünürken buraya
taşındı.

### Kimlik/ad ayrımı neden önemli

`plugin.xml`'deki `<id>` IDE'nin güncelleme eşleştirmesinde kullandığı anahtardır.
Ad ile birlikte değişseydi, yeniden adlandırılmış her paket **yeni bir eklenti**
sayılırdı: kullanıcıda yan yana iki kopya, ayrı ayarlar, bozuk güncelleme.
Kurum içi otomatik dağıtıma geçileceği için (bkz. 001 Belirsizlik 5 → ayrı spec)
bu ayrım şimdiden doğru kurulmalıydı.
