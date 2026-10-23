# ACP Kurulumu

Eklenti kurum içi depodan kurulur ve güncellenir. Adımlar **IntelliJ IDEA,
PyCharm, WebStorm ve DataGrip**'te aynıdır.

> **Dağıtımı yapan kişi için:** bu belgeyi ekibe göndermeden önce aşağıdaki
> `DEPO_ADRESI` yer tutucusunu kurumunuzun gerçek adresiyle **bir kez**
> değiştirin (örnek: `https://nexus.kurum.local/repository/acp`). Adres
> kurumdan kuruma değiştiği için koda ve depoya yazılmaz.

---

## 1. Depoyu tanımlayın (bir kez)

1. **Settings** (macOS: **IntelliJ IDEA → Settings**) → **Plugins**
2. Sağ üstteki **⚙** → **Manage Plugin Repositories…**
3. **+** ile şu adresi ekleyin ve **OK**:

   ```text
   DEPO_ADRESI/updatePlugins.xml
   ```

## 2. Eklentiyi kurun

1. **Plugins → Marketplace** sekmesinde **ACP** diye arayın
2. **Install** → IDE'yi yeniden başlatın

Dosya indirmeniz veya bir yere kopyalamanız gerekmez.

## 3. Sunucuyu tanımlayın

**Settings → Tools → ACP**:

- **Sunucu adresi** ve **API anahtarı**'nı girin
- **Bağlantıyı test et** → listeden modeli seçin

API anahtarı işletim sisteminin anahtar zincirinde saklanır; ayar dosyalarına
yazılmaz.

## Güncellemeler

Yeni sürüm yayınlandığında IDE bunu kendi güncelleme akışında gösterir
(**Plugins → Installed**'da "Update" ve bildirim). Tek tıkla kurulur; ayarlarınız
ve API anahtarınız korunur.

## Sorun giderme

| Belirti | Ne yapmalı |
|---|---|
| Depo eklenince "Connection failed" | Adresin **sonunda boşluk** kalmış olabilir (kopyala-yapıştırda sık olur); IDE boşlukla birlikte ister ve dosya bulunamaz. Adresi düzenleyip boşluğu silin |
| Marketplace'te "ACP" çıkmıyor | Depo adresinin sonunda `/updatePlugins.xml` var mı? Tarayıcıda açılıyor mu? |
| "Cannot connect" / sertifika hatası | Kurum ağında mısınız (VPN)? IDE'nin vekil ayarları **Settings → HTTP Proxy** |
| Anahtarı girdim ama "API anahtarı gerekli veya hatalı" (macOS) | Anahtar, onu kaydeden IDE'ye ait. Başka bir IDE okumak istediğinde macOS izin sorar; **Her Zaman İzin Ver** deyin. Ya da **Anahtar Zinciri Erişimi**'nde `IntelliJ Platform ACP — …` kaydını silip anahtarı yeniden girin |
| Kurulum sonrası panel yok | IDE'yi yeniden başlattınız mı? **View → Tool Windows → ACP** |

---

## Yayınlayan kişi için

Yeni sürüm:

```bash
# 1) gradle.properties → version=X.Y.Z  (bir öncekinden BÜYÜK olmalı)
# 2) Kimlik — yalnızca ortam değişkeni, hiçbir dosyaya yazılmaz:
export ACP_REPO_USER=...
export ACP_REPO_TOKEN=...
# 3) Yayınla (açık bir runIde sandbox'ı varsa önce kapatın):
./gradlew yayinla -Pacp.repo.url=DEPO_ADRESI
```

`yayinla` sırasıyla: testler ve paket denetimi → paketleme → depoda aynı sürüm
var mı (varsa **durur**, üzerine yazmaz) → önce paket, sonra `updatePlugins.xml`.
Testlerden biri kırmızıysa hiçbir şey yüklenmez.
