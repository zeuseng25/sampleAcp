# Plan: Satır İçi Kod Tamamlama

- **Spec no:** 006 — [spec.md](spec.md)
- **Tarih:** 2026-09-04
- **Durum:** Taslak

---

## Yaklaşım

IDE'nin **kendi satır içi tamamlama altyapısı** kullanılıyor: `InlineCompletionProvider`.
Soluk metnin çizimi, kabul tuşu, yazınca kaybolma, IDE'nin kendi tamamlama
listesiyle uyum — hepsi platformdan geliyor. Kendi inlay çizimimizi yazmıyoruz.

`DebouncedInlineCompletionProvider` tabanı debounce'u da veriyor; "her tuşta
istek gönderme" kuralı ondan bedava geliyor (spec H1).

Model isteği **FIM biçimi ayarına** göre kuruluyor. Biçim bir açılır listeden
seçiliyor; `KAPALI` seçiliyse sağlayıcı hiç çalışmıyor.

Kritik davranış: **yazma deneyimi hiçbir koşulda yavaşlamaz.** İstek arka planda,
imleç oynayınca iptal, hata sessizce yutulup günlüğe yazılıyor.

## Değerlendirilen alternatifler

### Öneriyi kim çiziyor

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| `InlineCompletionProvider` | Çizim, kabul tuşu, iptal, IDE uyumu bedava | `lang.impl` içinde — sürüm kararlılığı platform API'si kadar yüksek değil | **Seçildi** |
| Kendi inlay çizimimiz | Tam kontrol | Kabul tuşu, IDE tamamlamasıyla çakışma, tema — hepsi elde | Elendi |

### FIM biçimi

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Ayardan biçim seçici | Model değişince biçim de değişir; yanlış biçim görünür | Bir ayar daha | **Seçildi** |
| Model adından tahmin | Ayar yok | Tahmin yanılırsa **sessizce** kötü öneri; teşhisi zor | Elendi |
| Tek biçim (Qwen) | En basit | GLM/Kimi'ye geçince çalışmaz, spec yeniden açılır | Elendi |

### İstek ucu

FIM için `/v1/completions` kullanılıyor, `/v1/chat/completions` değil: FIM
belirteçleri **ham prompt** içinde gitmeli; sohbet biçimi araya kendi şablonunu
sokar ve belirteçleri bozar. "Sohbet tarzı" biçimi seçilirse istisna olarak
sohbet ucu kullanılır.

---

## Arayüzler

### FIM biçimi (`dev.acp.llm.completion` — saf)

```kotlin
internal enum class FimBicimi(val ad: String) {
    KAPALI("Kapalı"),
    QWEN("Qwen"),
    DEEPSEEK("DeepSeek"),
    STARCODER("StarCoder"),
    CODELLAMA("CodeLlama"),
    SOHBET("Sohbet tarzı"),
}

internal object FimPromptKurucu {
    /** Öncesi + sonrası → modele gidecek ham prompt. */
    fun kur(bicim: FimBicimi, oncesi: String, sonrasi: String): String
}
```

Belirteçler biçime göre:

| Biçim | Şablon |
|---|---|
| Qwen | `<\|fim_prefix\|>ÖNCE<\|fim_suffix\|>SONRA<\|fim_middle\|>` |
| DeepSeek | `<｜fim▁begin｜>ÖNCE<｜fim▁hole｜>SONRA<｜fim▁end｜>` |
| StarCoder | `<fim_prefix>ÖNCE<fim_suffix>SONRA<fim_middle>` |
| CodeLlama | `<PRE> ÖNCE <SUF>SONRA <MID>` |
| Sohbet tarzı | Sohbet ucuna talimatlı istek |

### Yanıt temizleme (saf)

```kotlin
internal object OneriTemizleyici {
    /** Modelin çıktısını tek satırlık, gösterilebilir bir öneriye indirger. */
    fun temizle(hamYanit: String, imlecSonrasiSatir: String): String?
}
```

Yapması gerekenler ve **neden**:

- Kod çiti gelirse ayıkla — bazı modeller FIM'de bile ``` ekliyor
- İlk satırdan sonrasını at — tek satır kararı (spec Belirsizlik 4)
- Sonda kaçan FIM/EOT belirteçlerini kes
- İmlecin sonrasındaki metni tekrarlıyorsa **kırp** — yoksa kod iki kez yazılır
- Sonuç boş veya yalnızca boşluksa `null` (öneri gösterilmez)

### İstemci (`dev.acp.llm`)

```kotlin
interface LlmClient {
    fun streamChat(request: ChatRequest): Flow<ChatDelta>
    suspend fun listModels(): List<ModelInfo>
    /** FIM tamamlama — akışsız, tek seferlik. */
    suspend fun complete(request: CompletionRequest): String
}

internal data class CompletionRequest(
    val model: String,
    val prompt: String,
    val maxTokens: Int,
    val stop: List<String>,
    val temperature: Double,
)
```

`streamChat` gibi akışlı değil: tek satırlık öneri için akış gereksiz karmaşıklık.

### Sağlayıcı (`dev.acp.completion`)

```kotlin
internal class AcpInlineCompletionProvider : DebouncedInlineCompletionProvider() {
    override val id: InlineCompletionProviderID
    override fun isEnabled(event: InlineCompletionEvent): Boolean  // ayar + biçim
    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion
}
```

`isEnabled` ayar kapalıysa **hiç istek göndermeden** false döner (spec H3).

### Ayarlar

| Alan | Tip | Varsayılan | Not |
|---|---|---|---|
| `tamamlamaBicimi` | enum | `KAPALI` | Kapalı = özellik yok (spec kararı) |
| `tamamlamaModeli` | String | boş | Sohbetten **ayrı**; farklı model olabilir |
| `tamamlamaMaxTokens` | Int | 64 | Tek satır için yeterli |
| `tamamlamaGecikmeMs` | Int | 300 | Debounce; yazarken istek yağmuru olmasın |
| `tamamlamaZamanAsimiMs` | Int | 3.000 | Geç gelen öneri zaten atılacak |

Tamamlama modeli boşsa sohbet modeline düşülür.

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `dev/acp/llm/completion/FimBicimi.kt` | yeni — saf |
| `dev/acp/llm/completion/FimPromptKurucu.kt` | yeni — saf |
| `dev/acp/llm/completion/OneriTemizleyici.kt` | yeni — saf |
| `dev/acp/llm/LlmClient.kt` | `complete()` eklenir |
| `dev/acp/llm/openai/OpenAiLlmClient.kt` | `/v1/completions` uygulaması |
| `dev/acp/completion/AcpInlineCompletionProvider.kt` | yeni |
| `dev/acp/settings/AcpSettings.kt` | beş yeni alan |
| `dev/acp/settings/AcpSettingsConfigurable.kt` | "Satır içi tamamlama" bölümü |
| `META-INF/plugin.xml` | `inlineCompletionProvider` kaydı |

## Yeniden Kullanılacak Mevcut Kod

**Kendi kodumuz:** `OpenAiLlmClient` (istek kurma, hata eşlemesi),
`HttpClientFactory`, `AcpSettings`/`AcpSecrets`, `LlmClientService`,
`FakeSglangServer`, `MarkdownFenceSplitter` (kod çiti ayıklama için).

**Platform zaten sunuyor:**

| İhtiyaç | API |
|---|---|
| Soluk metin çizimi, kabul tuşu | `InlineCompletionProvider` |
| Debounce | `DebouncedInlineCompletionProvider` |
| İmleç oynayınca iptal | Platform isteği iptal ediyor |
| Tek adımlı geri alma | Platformun kendi ekleme işleyicisi |

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok
- [x] `dev.acp.llm` saf kalıyor — biçim, prompt kurucu ve temizleyici orada
- [x] Dile özgü API yok — sağlayıcı dilden bağımsız
- [x] Metinler `AcpBundle`'dan
- [x] Ayarlanabilir değerler `AcpSettings`'te

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| **Model FIM'i gerçekten desteklemiyor** | Özellik anlamsız çıktı üretir | **T10'da, her şeyden önce** gerçek modele karşı denenir |
| `InlineCompletionProvider` impl sınıfı, sürümle değişebilir | Derleme kırılır | `verifyPlugin` çalıştırılır; API imzası `AGENTS.md`'ye not edilir |
| Yazarken gecikme hissi | Özellik kullanılmaz olur | Debounce + kısa zaman aşımı + istek arka planda; hiçbir yerde EDT beklemesi yok |
| Öneri imleç sonrasını tekrarlar | Kod iki kez yazılır | `OneriTemizleyici` kırpar; testle korunur |
| Hata penceresi yazarken açılır | Kabul edilemez | Hatalar yutulur, yalnızca günlüğe |
| Kod sürekli dışarı gider | Denetim endişesi | Varsayılan kapalı; ayarda görünür |

## Test Stratejisi

- **Saf birim (çoğunluk):** `FimPromptKurucu` her biçim için doğru belirteçler;
  `OneriTemizleyici` kod çiti, çok satır, tekrar eden sonek, boş çıktı
- **Sahte sunucu:** `complete()` uçtan uca; zaman aşımı; hata durumunda `null`
- **`BasePlatformTestCase`:** `isEnabled` ayar kapalıyken false; kapalıyken
  **hiç istek gitmediği**
- **Elle (`runIde`):** gerçek modelle öneri kalitesi, yazma akıcılığı, kabul ve
  geri alma

## Uygulama Sırası

Riskli olan **ilk sırada**: model FIM'i desteklemiyorsa tasarım değişir.

1. **Gerçek modele karşı FIM denemesi** — `curl` ile, kod yazmadan önce
2. **Biçim + prompt kurucu + temizleyici** — saf, hızlı geri bildirim
3. **`complete()` istemcide** — sahte sunucuya karşı
4. **Ayarlar** — biçim seçici, model, gecikme
5. **Sağlayıcı** — IDE'ye bağlanır; *ilk kez öneri görünür*
6. **Kalite ve sağlamlaştırma** — iptal, zaman aşımı, sessiz hata
