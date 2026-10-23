# Görevler: Kurum İçi Dağıtım

- **Spec no:** 005 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Kısmen — depodan bağımsız işler sürüyor; T10 kurumun depo adresini bekliyor

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`

---

## Erişim doğrulaması — her şeyden önce

- [x] T10 Kimliksiz okuma **yerel Docker Nexus 3.95.2 CE'de doğrulandı** ✅ *2026-10-23*: yayıncı PUT 201, anonim GET 200 (içerik aynı), anonim HEAD var 200 / yok 404, anonim PUT 401
  - Kurulum: raw hosted `acp` (writePolicy=allow — kayıt her sürümde güncellenir; paket üzerine yazmayı `surumKontrol` engeller), anonim erişim açık, yalnızca `acp` deposuna yazabilen `acp-yayinci` rolü/kullanıcısı.
  - Bulgu: Nexus CE 3.77+ **EULA kabul edilmeden** her depo isteğine 403 veriyor (yönetici dahil). Kurumun Nexus'unda bu zaten yapılmıştır; yerel kurulumda kullanıcı onayıyla kabul edildi.
  - ⚠️ Kurumun gerçek deposunda tekrar doğrulanmalı (anonim erişim kurum politikasına bağlı).
  - Bu çalışmazsa planın tamamı değişir; bu yüzden ilk sırada.
  - Gereken bilgi: depo adresi ve yayınlama kimliği (yalnızca yazma için).

## Dağıtım kaydı

- [x] T20 `dagitimKaydiUret` → `DagitimKaydiTest` (7) ✅ *2026-10-23*; görev yokken kırmızı
  - Kaynak: pakete giren yamalı `plugin.xml` (`patchPluginXml` çıktısı) — kimlik, sürüm, ad, since-build oradan; until-build yalnızca pakette varsa (açık uçlu korunur).
  - Ruling: depo adresi her kurumda farklı (kullanıcı, 2026-10-23) → yalnızca `-Pacp.repo.publicUrl` ile verilir, `gradle.properties`'e de yazılmaz; yoksa görev anlaşılır hatayla durur. Test `.invalid` örnek adresle üretilen gerçek görev çıktısını okur. — Bedeli: yayınlayan her seferinde adresi verir (CI'da değişken).
  - Ruling: test `buildPlugin`'e değil `patchPluginXml`'e bağlı — `buildPlugin` başsız IDE açıyor (yavaş, açık sandbox'la çakışır, tuzak #3). Paket adı `buildPlugin`'in yapılandırmasından okunur, çalıştırılmaz.
- [x] T21 Sürüm `build.gradle.kts`'ten **`gradle.properties`'e taşındı** (`version=`) ✅; `-Pversion=0.9.9` ile paket adı, kayıt ve plugin.xml birlikte değişti (elle doğrulandı)

## Sürüm kontrolü

- [x] T30 Aynı sürüm ikinci kez → "depoda ZATEN VAR — üzerine yazılmaz" ile durdu; depodaki paketin özeti değişmedi ✅ *(yerel Nexus)*
- [x] T31 Mesaj sürüm artırmayı söylüyor ✅; yanlış anahtar sürüm kontrolünde "Depo erişimi reddetti (HTTP 401)… doğru mu?" ile yakalanıyor

## Yayınlama komutu

- [x] T40 `yayinla` görevi → `check` → `buildPlugin` → `surumKontrol` → yükleme sırasıyla çalışır
  - Kuru çalıştırma: `buildPlugin → test → verifyNoBundledJars → check → dagitimKaydiUret → surumKontrol → depoyaYukle` ✅
  - **Yerel Nexus'a gerçek yayın (0.1.0) ✅:** paket + `updatePlugins.xml` yüklendi; kayıt yüklenen pakete işaret ediyor, anonim indirilen paket derlenenle bayt bayt aynı; anahtar günlükte yok.
  - Bulunan hatalar: (1) görev sınıfındaki `http` özelliği Gradle doğrulamasına takıldı → fonksiyon; (2) **önceden var olan**: `verifyNoBundledJars` `build/distributions`'ta tek zip bekliyordu — sürüm artınca eski zip de orada, `clean`'siz her yayın düşerdi → artık `buildPlugin`'in ürettiği paketi denetliyor.
  - Ruling: yükleme düz HTTP `PUT` + Basic (Nexus raw ve Artifactory ortak paydası); okuma `HEAD`. Kurumun deposu farklı bir yöntem isterse yalnızca `DepoGorevi` değişir.
- [x] T41 ✅ İstemeden kanıtlandı: `-Pversion` ile gradle.properties ayrışınca `DagitimKaydiTest` kırmızı oldu, yayın `:test`'te durdu, depoya gidilmedi
- [x] T42 Yalnız okuyabilen kullanıcıyla: sürüm kontrolü geçti, paket PUT 403 → "yüklenemedi; kayıt güncellenmedi"; kayıt 0.1.0'da kaldı, 0.1.1 depoda yok ✅
  - Ruling: "birlikte" = sıra (önce paket, sonra kayıt); kaydın olmayan paketi göstermesi imkânsız. Kayıt yüklemesi başarısız olursa yeni paket depoda yetim kalır ama kullanıcılar eski (çalışan) sürümü görür. — Bedeli: yetim paket bir sonraki yayında aynı sürüm numarasını kilitler; sürüm artırılır.
- [x] T43 Kimlik yalnızca ortamdan → `DagitimGuvenlikTest` (3) ✅; ilk test kırmızıydı; kimlik `@Internal` (önbelleğe/günlüğe girmez)

## Kurulum belgesi

- [x] T50 `docs/kurulum.md` ✅
  - Ruling: plan "adres gerçek değeriyle yazılır" diyordu; adres kurumdan kuruma değiştiği için belgede `DEPO_ADRESI` yer tutucusu var, dağıtımı yapan bir kez doldurur. — Bedeli: doldurulmazsa kullanıcı adresi bulamaz; belgenin başında açık uyarı var.
- [x] T51 AGENTS.md "Ekibe dağıtım" bölümü ✅ (elle dağıtım notunun yerine)

## Uçtan uca doğrulama

- [x] T60 Yerel Docker Nexus'a yayın → kullanıcının normal IntelliJ'inde depo tanımlandı, ACP Marketplace'ten kuruldu, çalıştı ✅ *2026-10-23*
  - Kullanıcı tarafı iki bulgu (belgelere eklendi): (1) adresin sonunda boşluk → IDE `updatePlugins.xml%20` ister, 404, "Connection failed"; (2) macOS'ta anahtar zinciri kaydı başka IDE'ye (sandbox) aitti → okuma reddedildi, eklenti anahtarsız istek attı ("anahtar=false"), mesaj "anahtar hatalı" dedi — yanıltıcı, ayrı iş.
  - ⚠️ Kurumun gerçek Nexus'unda tekrarlanmalı.
- [ ] T61 İkinci bir sürüm yayınla → IDE güncellemeyi kendi akışında gösterir, tek tıkla kurulur *(H2)*
- [ ] T62 Güncellemeden sonra ayarlar ve API anahtarı korunuyor; yan yana ikinci kopya yok *(H2)*

## Doğrulama ve kapanış

- [ ] T90 `spec.md` kabul kriterlerinin tamamı doğrulanır
- [ ] T91 `spec.md` durumu "Uygulandı" yapılır
- [ ] T92 `AGENTS.md` güncellenir

---

## Notlar

### Neden beklemede (2026-09-04)

Spec ve plan hazır, ama uygulama başlatılmadı. Sebep: **T10** (depodan anonim
okuma doğrulaması) olmadan devam etmek anlamsız — sonuç olumsuz çıkarsa dağıtım
yeri komple değişir ve plan yeniden yazılır.

Devam edebilmek için gereken iki bilgi:

1. **Depo adresi** — paketlerin duracağı yol
   (örn. `https://nexus.kurum.local/repository/acp/`)
2. **Yayınlama kimliği** — yalnızca *yazma* için; okuma anonim olacak.
   Ortam değişkeni olarak verilir, repoya yazılmaz:
   `ACP_REPO_USER`, `ACP_REPO_TOKEN`

Bunlar geldiğinde T10'dan devam edilir. T20 (dağıtım kaydı üretimi) adresten
bağımsız olduğu için istenirse önden yazılabilir, ama planın risk sırası
T10'u öne alıyor.
