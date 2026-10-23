# ACP

Kendi sunucumuzda çalışan LLM'e (LiteLLM → SGLang → Qwen) bağlanan JetBrains eklentisi.

Tek kod tabanı; **IntelliJ IDEA, DataGrip, WebStorm ve PyCharm**'da çalışır.

## Kurulum ve dağıtım

**[docs/kurulum.md](docs/kurulum.md)** — adım adım:

- **A.** Projeyi indirip derlemek, kurumunuzun Nexus'una yüklemek ve ekibe yayınlamak
- **B.** Ekip üyesi olarak eklentiyi IDE'ye kurmak

Ayrıntılar (Nexus'u `curl` ile hazırlama, yayın hataları, Artifactory, CI):
[docs/yayinlama-rehberi.md](docs/yayinlama-rehberi.md).

## Geliştirme

```bash
./gradlew build      # derle + testler
./gradlew runIde     # eklentinin kurulu olduğu sanal bir IDE aç
./gradlew buildPlugin # dağıtılabilir zip üret (build/distributions/)
```

JDK kurmanıza gerek yok — Gradle toolchain JDK 21'i kendi indirir.

### GPU sunucusu olmadan geliştirme

`FakeSglangServer` OpenAI-uyumlu uçları taklit eder:

```bash
./gradlew -q test --tests '*' # veya IDE'den dev.acp.fake.FakeSglangServerKt.main() çalıştır
```

Yazdırdığı adresi ACP ayarlarına girin. Yarıda kesilen akış, 401, 500 ve
düşünme bloğu gibi senaryoları modlarıyla test edebilirsiniz.

## Değiştirmeden önce bilinmesi gerekenler

Bu dört kural ihlal edilirse hata **kullanıcının IDE'sinde, çalışma zamanında**
ortaya çıkar — derlemede değil. Hepsi otomatik olarak zorlanır.

### 1. Çalışma zamanı bağımlılığı eklenmez

Eklenti dağıtımında yalnızca kendi jar'ımız olmalı. IntelliJ platformu kendi
`kotlin-stdlib`, `kotlinx-coroutines` ve Jackson'ını sağlar; ikinci bir kopya
göndermek `LinkageError` / `NoSuchMethodError` üretir.

JSON için platformun Jackson'ı `JsonNode` seviyesinde kullanılır — veri sınıfı
yansıması veya anotasyon yok.

> Zorlayan: `verifyNoBundledJars` görevi (`./gradlew check`).

### 2. `dev.acp.llm` paketi saf kalır

O pakette `com.intellij` importu yasaktır. Böylece LLM katmanı IDE başlatmadan,
saniyeler içinde test edilir.

> Zorlayan: `ArchitectureTest`.

### 3. Dile özgü API kullanılmaz

Yalnızca `com.intellij.modules.platform`'a bağlıyız. `com.intellij.modules.java`,
Go/JS/Python PSI'ları ve dile özgü inspection'lar kullanılamaz — kullanılırsa
eklenti DataGrip ve WebStorm'da yüklenmez.

Dile duyarlılık `FileType` üzerinden yapılır (`.tsx` → React promptu vb.).
İleride gerçek PSI gerekirse `<depends optional config-file=...>` ile ayrı bir
modül olarak eklenir; çekirdeğe karıştırılmaz.

### 4. Kotlin sürümü hedef IDE ile eşleşir

`build.gradle.kts` içinde `apiVersion`/`languageVersion` 2.1'e sabitlenmiştir,
çünkü desteklenen en eski IDE (2025.2) Kotlin 2.1.20 bundle eder. Hedef IDE
yükseltilmeden bu yükseltilmez.

## Ağ

Eklenti kendi TLS/proxy çözümünü kurmaz; IDE'nin proxy ayarlarını ve
`CertificateManager`'ını kullanır. Kurumsal ağdaki TLS kesmesi ve iç CA
sertifikaları böyle çalışır.

API anahtarı `PasswordSafe`'te (işletim sistemi anahtar zinciri) saklanır,
ayar dosyasına **yazılmaz**.

## Kapsam

v1: sohbet paneli + seçime dayalı aksiyonlar (açıkla / test yaz / refactor / doküman).

Sonraki sürümler: satır içi FIM kod tamamlama, dosya düzenleyen agent.
Çekirdek arayüzler (`Flow<ChatDelta>`, `ContextProvider`) ikisini de
imza değiştirmeden eklenebilecek şekilde tasarlandı.
