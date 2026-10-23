# ACP — Derleme, Nexus'a Yükleme ve Ekibe Dağıtım

Bu rehber, ACP'yi kaynak koddan derleyip **kendi kurumunuzun Nexus'una**
yükleyecek ve ekibinize dağıtacak kişi içindir. Sonunda ekip üyeleri eklentiyi
IDE'lerinin kendi eklenti ekranından kurar; yeni sürümler IDE'nin güncelleme
akışında görünür.

> Ekipteki son kullanıcının adımları ayrı: [kurulum.md](kurulum.md).

**Akış bir bakışta:**

```text
kaynak kod ──./gradlew yayinla──▶ Nexus (raw depo) ──updatePlugins.xml──▶ ekibin IDE'leri
               test → paket →        acp-X.Y.Z.zip                       kur / güncelle
               sürüm kontrolü →      updatePlugins.xml
               yükle
```

---

## 1. Gereksinimler

| Ne | Neden | Not |
|---|---|---|
| **IntelliJ IDEA 2026.1 veya üstü, bilgisayarda kurulu** | Derleme, platformu kurulu IDE'den alır; indirmez | Ultimate ya da Community |
| JDK 17+ (yalnızca Gradle'ı çalıştırmak için) | Gradle 9 bunu ister | Derleme için gereken JDK 21'i Gradle kendisi indirir |
| İnternet erişimi (ilk derlemede) | Gradle ve bağımlılıklar indirilir | Kurum içi Maven yansısı varsa `~/.gradle/init.gradle.kts` ile yönlendirin |
| Nexus Repository 3 | Paketlerin duracağı yer | Artifactory de olur — bkz. [Bölüm 8](#8-nexus-değil-de-artifactory) |

IDE `/Applications/IntelliJ IDEA.app` dışında kuruluysa (Linux, Windows, farklı
klasör) yolunu **kendi** Gradle ayarınıza yazın — depoya değil:

```properties
# ~/.gradle/gradle.properties
acp.ide.path=/opt/idea                                    # Linux
# acp.ide.path=C:/Program Files/JetBrains/IntelliJ IDEA 2026.1   # Windows
```

## 2. Kodu alın ve kurumunuza göre ayarlayın

```bash
git clone <depo-adresi> acp
cd acp
```

`gradle.properties` içinde **iki** değer sizi ilgilendirir:

```properties
# Eklentinin IDE'de görünen adı — kurumunuzun adını verebilirsiniz.
acp.displayName=ACP

# Sürüm — her yayında ELLE artırılır. Aynı sürüm ikinci kez yayınlanamaz.
version=0.1.0
```

> ⚠️ **Eklenti kimliği (`dev.acp.assistant`) asla değiştirilmez.** Görünen adı
> istediğiniz kadar değiştirebilirsiniz; kimlik değişirse IDE yeni sürümü ayrı
> bir eklenti sayar ve kullanıcılarda iki kopya oluşur.

Sunucu adresi, model ve API anahtarı pakete **girmez** — her kullanıcı kendi
IDE'sinde girer. Kurum adresi koda gömülmez.

## 3. Derleyin ve test edin

```bash
./gradlew check         # tüm testler + paket denetimi (yabancı jar sızmış mı)
./gradlew buildPlugin   # build/distributions/acp-<sürüm>.zip
```

İlk derleme birkaç dakika sürer. Bu adımlardan önce, `./gradlew runIde` ile
açılmış bir **deneme IDE'si açıksa kapatın**; aksi hâlde paketleme "Only one
instance of IDEA can be run at a time" ile durur.

Zip'i elle denemek isterseniz: **Settings → Plugins → ⚙ → Install Plugin from
Disk…**

## 4. Nexus'u hazırlayın (bir kez)

Bir **raw (hosted)** depo, okumaya açık; yazma yalnızca bir yayın kullanıcısına.
Aşağıdakiler arayüzden de yapılır; burada REST karşılıkları var. `NEXUS` ve
yönetici bilgilerini kendinize göre değiştirin.

```bash
NEXUS=https://nexus.kurum.local
ADMIN=admin:YONETICI_SIFRESI
```

### 4.1 Lisans (yalnızca yeni kurulan Community Edition)

Nexus CE 3.77 ve sonrası, kullanım lisansı (EULA) kabul edilene kadar **her depo
isteğine 403** verir — yönetici dahil. Kurumunuzun Nexus'u zaten kullanımdaysa bu
adım yapılmıştır. Yeni kurduysanız arayüzde açılan lisans ekranını onaylayın.

### 4.2 Raw depo

```bash
curl -u "$ADMIN" -X POST -H 'Content-Type: application/json' \
  "$NEXUS/service/rest/v1/repositories/raw/hosted" -d '{
    "name": "acp", "online": true,
    "storage": {"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"},
    "raw": {"contentDisposition": "INLINE"}
  }'
```

**`writePolicy: allow` gerekli:** `updatePlugins.xml` her yayında güncellenir.
Paketin üzerine yazılmasını yayın komutunun kendisi engeller (Bölüm 5).

### 4.3 Okumaya açık (kimliksiz)

IDE'ler eklenti deposunu kimliksiz okur. Bu, dağıtımın çalışması için **zorunlu**.

```bash
curl -u "$ADMIN" -X PUT -H 'Content-Type: application/json' \
  "$NEXUS/service/rest/v1/security/anonymous" \
  -d '{"enabled": true, "userId": "anonymous", "realmName": "NexusAuthorizingRealm"}'
```

Kurum politikası anonim erişime kapalıysa yalnızca bu depo için okuma izni
tanımlayın; IDE'nin özel eklenti deposu kimlik bilgisi **göndermez**.

### 4.4 Yayın kullanıcısı (yalnızca bu depoya yazar)

```bash
curl -u "$ADMIN" -X POST -H 'Content-Type: application/json' \
  "$NEXUS/service/rest/v1/security/roles" -d '{
    "id": "acp-yayin", "name": "acp-yayin", "description": "ACP yayını",
    "privileges": ["nx-repository-view-raw-acp-read", "nx-repository-view-raw-acp-browse",
                   "nx-repository-view-raw-acp-add", "nx-repository-view-raw-acp-edit"],
    "roles": []
  }'

curl -u "$ADMIN" -X POST -H 'Content-Type: application/json' \
  "$NEXUS/service/rest/v1/security/users" -d '{
    "userId": "acp-yayinci", "firstName": "ACP", "lastName": "Yayinci",
    "emailAddress": "acp-yayin@kurum.local", "password": "GUCLU_BIR_SIFRE",
    "status": "active", "roles": ["acp-yayin"]
  }'
```

Yayını yönetici hesabıyla yapmayın; bu kullanıcı yalnızca `acp` deposuna yazabilir.

### 4.5 Doğrulayın

```bash
echo deneme > deneme.txt
curl -u acp-yayinci:GUCLU_BIR_SIFRE --upload-file deneme.txt "$NEXUS/repository/acp/deneme.txt"   # 201
curl -o /dev/null -w '%{http_code}\n' "$NEXUS/repository/acp/deneme.txt"                          # 200 (kimliksiz)
curl -o /dev/null -w '%{http_code}\n' --upload-file deneme.txt "$NEXUS/repository/acp/izinsiz.txt" # 401
```

Üçü de beklenen kodu veriyorsa depo hazır.

## 5. Yayınlayın

Kimlik bilgisi **yalnızca ortam değişkeni** olarak verilir; hiçbir dosyaya
yazılmaz, günlükte görünmez:

```bash
export ACP_REPO_USER=acp-yayinci
export ACP_REPO_TOKEN='GUCLU_BIR_SIFRE'

./gradlew yayinla -Pacp.repo.url=https://nexus.kurum.local/repository/acp/
```

`yayinla` sırasıyla:

1. **Testler ve paket denetimi** (`check`) — biri kırmızıysa hiçbir şey yüklenmez
2. **Paketleme** — `acp-<sürüm>.zip`
3. **Dağıtım kaydı** — `updatePlugins.xml`, paketin kendi `plugin.xml`'inden
   üretilir (kimlik, sürüm, ad, uyumlu IDE aralığı); elle yazılmaz
4. **Sürüm kontrolü** — depoda bu sürüm varsa **durur**, üzerine yazmaz
5. **Yükleme** — önce paket, sonra kayıt. Paket yüklenemezse kayıt güncellenmez;
   kullanıcılar eski, çalışan sürümü görmeye devam eder

Kullanıcıların gördüğü adres yükleme adresinden farklıysa (ör. ters vekil
arkası) ikincisini ayrıca verin:

```bash
./gradlew yayinla -Pacp.repo.url=https://nexus-ic.kurum.local/repository/acp/ \
                  -Pacp.repo.publicUrl=https://nexus.kurum.local/repository/acp/
```

Yayından sonra kontrol:

```bash
curl https://nexus.kurum.local/repository/acp/updatePlugins.xml
```

### Yayın hataları

| Mesaj | Anlamı | Ne yapmalı |
|---|---|---|
| `acp-X.zip depoda ZATEN VAR — üzerine yazılmaz` | Bu sürüm yayınlanmış | `gradle.properties` → `version`'ı artırın |
| `Yükleme kimliği yok` | Ortam değişkenleri tanımsız | `ACP_REPO_USER`, `ACP_REPO_TOKEN` |
| `Depo erişimi reddetti (HTTP 401)` | Kullanıcı/şifre hatalı | Bölüm 4.4 |
| `… yüklenemedi (HTTP 403); kayıt güncellenmedi` | Kullanıcının yazma izni yok — ya da EULA kabul edilmemiş | Bölüm 4.4 / 4.1 |
| `Depo adresi tanımlı değil` | `-Pacp.repo.url` verilmedi | Komuta ekleyin |
| `There were failing tests` | Testler kırmızı; yayın yapılmadı | Önce testleri düzeltin |
| `Only one instance of IDEA can be run at a time` | Deneme IDE'si açık | `runIde` ile açılanı kapatın |

## 6. Ekibe duyurun

1. [kurulum.md](kurulum.md)'yi açın; `DEPO_ADRESI`'ni kendi adresinizle
   değiştirin (ör. `https://nexus.kurum.local/repository/acp`).
2. Ekibe gönderin. Her üye bir kez:
   **Settings → Plugins → ⚙ → Manage Plugin Repositories → +**
   `https://nexus.kurum.local/repository/acp/updatePlugins.xml`
   → **Marketplace**'te "ACP" → **Install**.

## 7. Yeni sürüm

```bash
# gradle.properties: version=0.1.0 → version=0.1.1
./gradlew yayinla -Pacp.repo.url=https://nexus.kurum.local/repository/acp/
```

Ekibin IDE'leri yeni sürümü kendi güncelleme akışında gösterir; tek tıkla kurulur.
Kullanıcı ayarları ve API anahtarı korunur (kimlik değişmediği sürece).

Sürüm geri alınmaz: hatalı bir sürüm çıktıysa düzeltip **daha büyük** bir
sürümle yeniden yayınlayın.

## 8. Nexus değil de Artifactory

Yayın, düz HTTP `PUT` (yükleme) ve `HEAD` (var mı) kullanır; Artifactory'nin
**generic** deposu ikisini de destekler. Generic bir depo açın, okumayı kimliksiz
yapın, bir yayın kullanıcısına yazma verin; komut aynıdır:

```bash
./gradlew yayinla -Pacp.repo.url=https://artifactory.kurum.local/artifactory/acp/
```

## 9. CI'dan yayın

Komut ortam değişkenlerinden okuduğu için CI'a taşımak yalnızca değişken
tanımlamaktır. CI'da deneme IDE'si açık olmadığından Bölüm 3'teki uyarı geçmez;
ama CI makinesinde de IntelliJ IDEA kurulu olmalı (`acp.ide.path`).

```yaml
# örnek (GitLab CI)
yayinla:
  script:
    - ./gradlew yayinla -Pacp.repo.url=$ACP_REPO_URL
  variables: {}   # ACP_REPO_USER / ACP_REPO_TOKEN / ACP_REPO_URL proje değişkenlerinde, maskeli
  when: manual
```

## 10. Sorun giderme (kullanıcı tarafı)

| Belirti | Sebep / çözüm |
|---|---|
| Depo eklenince "Connection failed" | Adresin **sonunda boşluk** — kopyala-yapıştırda sık. Düzenleyip silin |
| Marketplace'te "ACP" yok | Adres `…/updatePlugins.xml` ile bitmeli; tarayıcıda açılmalı |
| "API anahtarı gerekli veya hatalı" (macOS), anahtar doğru | Anahtar başka bir IDE'nin kaydında; macOS izin sorduğunda **Her Zaman İzin Ver** — ya da Anahtar Zinciri Erişimi'nde `IntelliJ Platform ACP — …` kaydını silip yeniden girin |
| Güncelleme görünmüyor | **Settings → Plugins → Installed** → ⚙ → **Check for Updates** |
