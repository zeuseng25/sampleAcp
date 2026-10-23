# ACP Kurulumu

ACP, kurumunuzun kendi model sunucusuna bağlanan bir JetBrains eklentisidir.
**IntelliJ IDEA, PyCharm, WebStorm ve DataGrip**'te çalışır.

Bu belgenin iki bölümü var — hangisi size uyuyorsa oradan başlayın:

- **[A. Projeyi indirip kurumuna yayınlayacak kişi](#a-projeyi-indirip-kurumuna-yayınlayacak-kişi)**
  — kodu GitHub'dan alır, derler, kurumun Nexus'una yükler, ekibe duyurur.
  Bunu kurumda **bir kişi** yapar.
- **[B. Ekip üyesi](#b-ekip-üyesi)** — eklentiyi IDE'sine kurar ve kullanır.

---

## A. Projeyi indirip kurumuna yayınlayacak kişi

Adımlar sırayla yapılır. Her adımın ayrıntısı ve olası hatalar
**[yayinlama-rehberi.md](yayinlama-rehberi.md)**'de.

### A1. Gereksinimleri kurun

- **IntelliJ IDEA 2026.1 veya üstü** bilgisayarınızda kurulu olmalı — derleme
  platformu buradan alır, internetten indirmez.
- **JDK 17 veya üstü** (yalnızca Gradle'ı çalıştırmak için; derlemenin istediği
  JDK 21'i Gradle kendisi indirir).
- **git**

### A2. Kodu indirin

```bash
git clone https://github.com/<hesap>/<depo>.git acp
cd acp
```

### A3. IDE'nin yerini gösterin (macOS dışında veya farklı klasörde)

IntelliJ IDEA `/Applications/IntelliJ IDEA.app` dışındaysa, yolunu **kendi**
Gradle ayarınıza yazın (projeye değil):

```properties
# ~/.gradle/gradle.properties
acp.ide.path=/opt/idea
# Windows: acp.ide.path=C:/Program Files/JetBrains/IntelliJ IDEA 2026.1
```

### A4. Kurumunuza göre ayarlayın

`gradle.properties`:

```properties
acp.displayName=ACP     # IDE'de görünen ad; kurumunuzun adını verebilirsiniz
version=0.1.0           # sürüm; her yayında ELLE artırılır
```

Eklenti kimliği (`dev.acp.assistant`) **değiştirilmez**. Sunucu adresi, model ve
API anahtarı pakete girmez; her kullanıcı kendisi girer.

### A5. Derleyin ve test edin

```bash
./gradlew check         # testler + paket denetimi
./gradlew buildPlugin   # → build/distributions/acp-0.1.0.zip
```

İlk derleme birkaç dakika sürer. Bitince zip'i kendi IDE'nizde denemek
isterseniz: **Settings → Plugins → ⚙ → Install Plugin from Disk…**

### A6. Nexus'ta depo hazırlayın (bir kez)

Nexus'ta yönetici olarak ([ayrıntı ve hazır `curl` komutları](yayinlama-rehberi.md#4-nexusu-hazırlayın-bir-kez)):

1. **raw (hosted)** tipinde `acp` adlı bir depo — *Deployment policy*:
   **Allow redeploy**
2. **Kimliksiz okuma açık** (Security → Anonymous Access) — IDE'ler depoyu
   kimliksiz okur; bu zorunlu
3. Yalnızca bu depoya yazabilen bir **yayın kullanıcısı** (ör. `acp-yayinci`)

Doğrulayın — ilki `201`, ikincisi `200`, üçüncüsü `401` dönmeli:

```bash
NEXUS=https://nexus.kurum.local
echo deneme > deneme.txt
curl -o /dev/null -w '%{http_code}\n' -u acp-yayinci:SIFRE --upload-file deneme.txt $NEXUS/repository/acp/deneme.txt
curl -o /dev/null -w '%{http_code}\n' $NEXUS/repository/acp/deneme.txt
curl -o /dev/null -w '%{http_code}\n' --upload-file deneme.txt $NEXUS/repository/acp/izinsiz.txt
```

Hepsi `403` ise ve Nexus yeni kurulduysa: kullanım lisansını (EULA) arayüzde
onaylayın.

### A7. Yayınlayın

Kimlik yalnızca ortam değişkeni olarak verilir — hiçbir dosyaya yazılmaz:

```bash
export ACP_REPO_USER=acp-yayinci
export ACP_REPO_TOKEN='SIFRE'
./gradlew yayinla -Pacp.repo.url=https://nexus.kurum.local/repository/acp/
```

Komut sırasıyla testleri çalıştırır, paketler, depoda aynı sürüm var mı bakar
(varsa durur) ve önce paketi, sonra `updatePlugins.xml`'i yükler. Testlerden biri
kırmızıysa hiçbir şey yüklenmez. Hata mesajları ve anlamları:
[rehber, Bölüm 5](yayinlama-rehberi.md#yayın-hataları).

Kontrol:

```bash
curl https://nexus.kurum.local/repository/acp/updatePlugins.xml
```

### A8. Ekibe duyurun

Aşağıdaki **B bölümünde** geçen `DEPO_ADRESI`'ni kendi adresinizle değiştirin
(ör. `https://nexus.kurum.local/repository/acp`) ve bu belgeyi ekibe gönderin.

### A9. Yeni sürüm çıkarmak

```bash
# gradle.properties: version=0.1.0 → version=0.1.1   (her zaman büyütün)
./gradlew yayinla -Pacp.repo.url=https://nexus.kurum.local/repository/acp/
```

Ekibin IDE'leri yeni sürümü kendi güncelleme akışında gösterir. Sürüm geri
alınmaz; hatalı sürümü daha büyük bir sürümle düzeltin.

---

## B. Ekip üyesi

Adımlar dört IDE'de de aynıdır.

### B1. Depoyu tanımlayın (bir kez)

1. **Settings** (macOS: **IntelliJ IDEA → Settings**) → **Plugins**
2. Sağ üstteki **⚙** → **Manage Plugin Repositories…**
3. **+** ile şu adresi ekleyin ve **OK** — sonunda boşluk kalmamasına dikkat edin:

   ```text
   DEPO_ADRESI/updatePlugins.xml
   ```

### B2. Eklentiyi kurun

1. **Plugins → Marketplace** sekmesinde **ACP** diye arayın
2. **Install** → IDE'yi yeniden başlatın

Dosya indirmeniz veya bir yere kopyalamanız gerekmez.

### B3. Sunucuyu tanımlayın

**Settings → Tools → ACP**:

- **Sunucu adresi** ve **API anahtarı**'nı girin → **Apply**
- **Bağlantıyı test et** → listeden modeli seçin

API anahtarı işletim sisteminin anahtar zincirinde saklanır; ayar dosyalarına
yazılmaz.

### Güncellemeler

Yeni sürüm yayınlandığında IDE bunu kendi güncelleme akışında gösterir
(**Plugins → Installed**'da "Update" ve bildirim). Tek tıkla kurulur; ayarlarınız
ve API anahtarınız korunur.

### Sorun giderme

| Belirti | Ne yapmalı |
|---|---|
| Depo eklenince "Connection failed" | Adresin **sonunda boşluk** kalmış olabilir (kopyala-yapıştırda sık olur); IDE boşlukla birlikte ister ve dosya bulunamaz. Adresi düzenleyip boşluğu silin |
| Marketplace'te "ACP" çıkmıyor | Depo adresinin sonunda `/updatePlugins.xml` var mı? Tarayıcıda açılıyor mu? |
| "Cannot connect" / sertifika hatası | Kurum ağında mısınız (VPN)? IDE'nin vekil ayarları **Settings → HTTP Proxy** |
| Anahtarı girdim ama "API anahtarı gerekli veya hatalı" (macOS) | Anahtar, onu kaydeden IDE'ye ait. Başka bir IDE okumak istediğinde macOS izin sorar; **Her Zaman İzin Ver** deyin. Ya da **Anahtar Zinciri Erişimi**'nde `IntelliJ Platform ACP — …` kaydını silip anahtarı yeniden girin |
| Kurulum sonrası panel yok | IDE'yi yeniden başlattınız mı? **View → Tool Windows → ACP** |
