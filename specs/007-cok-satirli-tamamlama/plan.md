# Plan: Çok Satırlı Kod Tamamlama

- **Spec no:** 007 — [spec.md](spec.md)
- **Tarih:** 2026-09-04
- **Durum:** Taslak

---

## Yaklaşım

006'nın altyapısı olduğu gibi kullanılıyor: aynı sağlayıcı, aynı istemci, aynı
ayarlar. Değişen tek şey **istek kurulmadan önce bir kip seçilmesi** ve
**yanıtın o kipe göre temizlenmesi**.

Kip kararı ve kesme, iki saf sınıfa düşüyor — ikisi de IDE'ye bağlı değil,
saniyeler içinde test edilebilir. Sağlayıcıya eklenen kod birkaç satırdan
ibaret kalıyor.

Çizim için hiçbir şey yapılmıyor: platform çok satırlı öneriyi blok inlay
olarak kendisi çiziyor (`DefaultInlineCompletionInlaysRenderer.addBlockElement`
ile doğrulandı). Aynı `InlineCompletionGrayTextElement` içinde `\n` yeterli.

## Değerlendirilen alternatifler

### Öneri nerede biter

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Girinti seviyesi | Dilden bağımsız, PSI gerekmez, Python'da doğal | Girintisiz/karışık kodda zayıf | **Seçildi** |
| `stop` dizisi göndermek | Sunucu keser, token harcanmaz | Biçime göre değişir, girintiyi ifade edemez | Elendi |
| Modelin durduğu yer | En basit | Modele göre değişir; GLM/Kimi'de davranış başkalaşır | Yalnızca yedek |
| Sabit satır sayısı | Tahmin edilebilir | Yapıyı bilmez, cümle ortasında keser | Emniyet supabı olarak kaldı |

Girinti kesici **ve** satır üst sınırı birlikte kullanılıyor: ilki yapıyı,
ikincisi kötü günü karşılıyor.

### Kip kararı nerede verilir

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Saf fonksiyon, imleç konumundan | Testi ucuz, IDE gerekmez | — | **Seçildi** |
| PSI'dan blok bilgisi | Daha doğru | Altın Kural 3'ü çiğner, dört IDE'de çalışmaz | Elendi |
| Ayardan | Kullanıcı kontrolü | Her bağlam değişiminde ayara gitmek gerekir | Elendi (spec kararı) |

---

## Arayüzler

### Kotlin tipleri (`dev.acp.llm.completion` — saf)

```kotlin
/** İmlecin konumuna göre ne tür öneri isteneceği. */
internal enum class TamamlamaKipi {
    /** Öneri istenmez — üye erişiminden hemen sonra. */
    YOK,
    /** Satır ortası: bugünkü davranış. */
    TEK_SATIR,
    /** İmlecin solunda yalnızca boşluk: blok önerisi. */
    COK_SATIR,
}

internal object TamamlamaKipiSecici {
    fun sec(metin: String, imlec: Int): TamamlamaKipi
}

internal object GirintiKesici {
    /**
     * @param oneri modelin ürettiği, satır satır bakılacak metin
     * @param tabanGirinti imlecin bulunduğu satırın girinti genişliği
     * @param maxSatir emniyet supabı
     */
    fun kes(oneri: String, tabanGirinti: Int, maxSatir: Int): String
}
```

`TamamlamaBaglami.uyeErisimiSonrasiMi` bu enum'a taşınır; sağlayıcıda iki ayrı
kontrol kalmaz, tek karar noktası olur.

`TamamlamaBaglami.Baglam`'a `tabanGirinti: Int` eklenir — imlecin satırının
girinti genişliği.

### Girinti kesicinin kuralları

Bu kısım kolay yanlış yapılır; kurallar açıkça:

1. **İlk satır girinti karşılaştırmasına girmez.** FIM prompt'u imlecin satırının
   yazılmış kısmıyla bitiyor, dolayısıyla modelin ilk satırı o satırın
   **devamıdır** ve kendi girintisi yoktur.
2. **Boş satır bloğu bitirmez.** Girinti hesabına katılmaz, öneride korunur —
   ama öneri boş satırla bitiyorsa sondaki boşluklar atılır.
3. **Girintisi `tabanGirinti`'den küçük ilk satırda durulur**, o satır dahil
   edilmez. Java/Kotlin'de kapanış `}` böyle elenir.
4. **Sekme genişliği**: sekme, editörün ayarına göre değil sabit bir genişlik
   sayılır. Karışım varsa karşılaştırma yaklaşık olur — kabul edilebilir,
   çünkü sonuç yalnızca öneri uzunluğunu etkiler, kodu bozmaz.
5. **İmlecin satırında yalnızca boşluk varsa, önerinin başındaki yeni satır
   atılır.** Model, imleç zaten girintideyken kendi satırını açıyor; olduğu gibi
   kabul edilirse imlecin satırı boş kalır. T10 ölçümünde gözlendi.
6. `maxSatir` her koşulda uygulanır.

### Ayarlar

| Alan | Tip | Varsayılan | Not |
|---|---|---|---|
| `completionMaxLines` | Int | 6 | Emniyet supabı; girinti kesici birinci savunma |
| `completionBlockMaxTokens` | Int | 256 | **Yalnızca çok satır kipinde.** Tek satır 64'te kalır |

Tek satırın token sınırı **değişmiyor** — spec H4: çok satır eklemek tek
satırın gecikmesini artırmamalı.

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `dev/acp/llm/completion/TamamlamaKipi.kt` | yeni — saf |
| `dev/acp/llm/completion/GirintiKesici.kt` | yeni — saf |
| `dev/acp/llm/completion/TamamlamaBaglami.kt` | `tabanGirinti` eklenir; `uyeErisimiSonrasiMi` taşınır |
| `dev/acp/llm/completion/OneriTemizleyici.kt` | `kip` parametresi; tek satır kısaltması kipe bağlanır |
| `dev/acp/completion/AcpInlineCompletionProvider.kt` | kip seçimi, kipe göre token sınırı |
| `dev/acp/settings/AcpSettings.kt` | iki yeni alan |
| `dev/acp/settings/AcpSettingsConfigurable.kt` | iki yeni alan |

## Yeniden Kullanılacak Mevcut Kod

**Kendi kodumuz:** `FimPromptKurucu` (değişmiyor), `OpenAiLlmClient.complete()`,
`AcpSettings.tamamlamaAcikMi()`, `FakeSglangServer`, `OneriTemizleyici`'nin
belirteç temizleme ve sonek kırpma adımları.

**Platform zaten sunuyor:**

| İhtiyaç | Nasıl |
|---|---|
| Çok satırlı soluk metin çizimi | Blok inlay — `InlineCompletionGrayTextElement` içinde `\n` |
| Tek tuşla kabul, tek adımlı geri alma | Platformun ekleme işleyicisi |
| Yazınca kaybolma, debounce | 006'dan aynen |

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok
- [x] `dev.acp.llm` saf kalıyor — kip seçici ve girinti kesici orada
- [x] Dile özgü API yok — girinti, dilden bağımsız tek yapısal sinyal
- [x] Kullanıcıya görünen metinler `AcpBundle`'dan
- [x] Ayarlanabilir değerler `AcpSettings`'te
- [x] 006'nın güvenlik kararları gevşetilmiyor (varsayılan kapalı, sessiz hata,
      kod günlüğe yazılmaz)

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| **256 token üretmek çok yavaş** | H4 karşılanmaz, özellik kullanılmaz | **T10'da, kod yazmadan önce ölçülür** — kabul edilemezse tasarım değişir |
| Girinti kesici erken keser | Öneri yarım kalır, işe yaramaz | Boş satır kuralı; her kural için ayrı test |
| Girinti kesici geç keser | Kabul edilen öneri kodu bozar | Satır üst sınırı; sonek kırpma korunur |
| Karışık sekme/boşluk | Kesme noktası kayar | Sabit sekme genişliği; sonuç yalnızca uzunluğu etkiler |
| Tek satır davranışı bozulur | 006'daki çalışan özellik geriler | 006'nın tüm testleri değişmeden kalır — geçmezlerse gerileme var |
| Uzun öneri altındaki kodu gizler | Okunabilirlik düşer | Platformun blok inlay'i kodu kaydırır, gizlemez — `runIde`'de doğrulanır |
| **Model FIM yapmıyorsa hasar tek satırdakinden büyük** | Kabul edilen öneri kodu ikiye katlar | Girinti kesici + sonek kırpma; ayrıca biçim ayarı yanlışsa kullanıcı görebilsin diye öneri kalitesi `runIde`'de kontrol edilir |

### FIM yapmayan sunum yığınında ne olur — 2026-09-04 gözlemi

FIM desteğini belirleyen model değil, **sunum yığını**. Aynı model
(`qwen/qwen3.8-27b`) OpenRouter'da Parasail'de düzgün FIM yapıyor, Reka'da
yapmıyor. Reka prompt'u "şu kodu tamamla" diye okuyup **fonksiyonun tamamını
baştan yazıyor**:

```cpp
void hesapla(int a, int b) {      ← imza zaten prefix'teydi
    int sonuc = a + b;
}                                  ← } zaten suffix'teydi
```

Tek satır kipinde bunun bedeli sınırlı: ilk satır alınır, yanlıştır, kullanıcı
yok sayar. **Çok satır kipinde ise kabul edilen öneri fonksiyonu ikiye katlar.**

Yani çok satır, yanlış biçim ayarının veya FIM desteklemeyen bir modelin
hasarını büyütüyor. Girinti kesici bunu tamamen önleyemez — ilk satırın girintisi
zaten karşılaştırmaya girmiyor (kural 1), dolayısıyla tekrarlanan imza satırı
kesilmez. Sonek kırpma yalnızca sondaki `}`'ı yakalar.

Bu, kodla çözülecek bir sorun değil: doğru model ve doğru biçim ayarı
gerektiriyor. `runIde` doğrulamasında öneri kalitesine bakılmasının sebebi bu.

## Test Stratejisi

- **Saf birim (çoğunluk):** `TamamlamaKipiSecici` her konum için; `GirintiKesici`
  beş kuralın her biri için, artı kötü girdi (girintisiz kod, tek satır, boş)
- **Gerileme:** 006'nın 314 testi **değiştirilmeden** geçmeli. Tek satır kipinde
  davranış birebir aynı kalmalı.
- **Sahte sunucu:** kipe göre doğru token sınırının gönderildiği; çok satırlı
  yanıtın uçtan uca kesilip gösterildiği
- **Elle (`runIde`):** gerçek modelde öneri kalitesi, **yazma akıcılığı**,
  kabul + tek adımlı geri alma, önerinin altındaki kodu gizlemediği

## Uygulama Sırası

Riskli olan ilk sırada — 006'daki FIM doğrulamasının karşılığı.

1. **Gecikme ölçümü** — `curl` ile, kod yazmadan. Kabul edilemezse tasarım değişir.
2. **Kip seçici** — saf; `uyeErisimiSonrasiMi` buraya taşınır
3. **Girinti kesici** — saf; beş kuralın her biri testli
4. **Temizleyiciye kip bağlanması** — 006 testleri hâlâ yeşil olmalı
5. **Ayarlar** — satır sınırı, blok token sınırı
6. **Sağlayıcı** — *ilk kez çok satırlı öneri görünür*
7. **Akıcılık ve kalite** — `runIde` ile elle
