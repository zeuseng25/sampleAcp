# Plan: Çok Dosyalı Düzenleme

- **Spec no:** 008 — [spec.md](spec.md)
- **Tarih:** 2026-09-04
- **Durum:** Taslak

---

## Yaklaşım

Üç katman, aralarındaki sınır bilinçli:

1. **Araç çağırma** — `LlmClient` genişletilir. 001'de bu düşünülerek
   bırakılmıştı (`ChatDelta`'da `// v2: ToolCallDelta` yorumu); yeni bir varyant
   ekleniyor, imzalar değişmiyor.
2. **Ajan döngüsü — SAF.** Model çağırır, araç isteğini alır, sonucu geri
   besler, tekrarlar. Araçları kendisi çalıştırmaz: bir arayüz üzerinden
   çağırır. Böylece döngü IDE olmadan, sahte araçlarla test edilebilir.
3. **Araçların IDE uygulaması** — arama, dosya okuma, düzenleme toplama.

Bu ayrımın sebebi: agent'ın **karar mantığı** en çok hata yapılacak yer, ve IDE
başlatmadan test edilemezse her deneme dakikalar sürer. Saf tutunca saniyeler
sürer.

**Düzenlemeler döngü sırasında diske yazılmaz.** Araç yalnızca "şu dosyada şunu
şununla değiştir" kaydını biriktirir. Yazma işi, döngü bittikten sonra tek bir
işlemde yapılır — spec'in "ya tamamen ya hiç" kuralı ancak böyle tutulabilir.

## Değerlendirilen alternatifler

### Agent motoru

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Kendi döngümüz | Harici süreç yok, zip dağıtımıyla uyumlu, tam kontrol | Döngüyü, sınırları, iptali biz yazacağız | **Seçildi** |
| Harici motor (opencode vb.) | Yetenek hazır gelir | Her geliştiricinin makinesinde ayrı çalışan süreç; depo erişimi olmayan elden dağıtımda kabul edilemez | Elendi |

Spike (2026-09-04): araç çağırma **düz OpenAI-uyumlu API ile** alındı. Harici
motor, zaten sahip olduğumuz yeteneği getirip karşılığında dağıtım yükü
bırakacaktı.

### Düzenleme nasıl ifade edilir

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| `eski metin` → `yeni metin` | Bayatlık kendiliğinden yakalanır: eski metin bulunamazsa dosya değişmiş demektir | Model eski metni birebir vermeli | **Seçildi** |
| Satır numarası + yeni içerik | Model için kolay | Satır numaraları bir önceki düzenlemeden sonra kayar; sessizce yanlış yere yazar | Elendi |
| Tam dosya içeriği | En basit uygulama | Büyük dosyada token israfı; modelin dokunmadığı yerleri de yeniden üretmesi hata kaynağı | Elendi |

Seçilen biçim, 003'teki bayatlık korumasının doğal karşılığı.

### Uygulama biçimi

Ayardan seçilir (spec H4). İkisi de **aynı** toplu uygulama yolunu kullanır;
fark yalnızca araya önizleme girip girmediği. Böylece "doğrudan uygula" seçilse
bile tek geri alma adımı korunur.

---

## Arayüzler

### Araç çağırma (`dev.acp.llm` — saf)

```kotlin
data class AracTanimi(
    val ad: String,
    val aciklama: String,
    /** JSON Schema — JsonNode düzeyinde, yansıma yok. */
    val parametreler: JsonNode,
)

data class AracCagrisi(val id: String, val ad: String, val argumanlarJson: String)

sealed interface ChatDelta {
    // ... mevcut varyantlar değişmiyor
    data class ToolCall(val cagri: AracCagrisi) : ChatDelta
}

data class ChatMessage(
    val role: Role,
    val content: String,
    /** Asistan araç çağırdıysa. */
    val toolCalls: List<AracCagrisi> = emptyList(),
    /** Rol TOOL ise hangi çağrıya yanıt olduğu. */
    val toolCallId: String? = null,
) {
    enum class Role(val wire: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant"), TOOL("tool") }
}
```

`ChatRequest`'e `tools: List<AracTanimi>` eklenir. Boşsa gövdeye hiç yazılmaz —
mevcut çağrılar birebir aynı kalır.

### Ajan döngüsü (`dev.acp.llm.agent` — SAF)

```kotlin
internal interface AracYurutucu {
    /** Aracı çalıştırır, modele geri verilecek sonucu döndürür. */
    suspend fun calistir(cagri: AracCagrisi): String
}

internal sealed interface AjanOlayi {
    data class Dusunuyor(val metin: String) : AjanOlayi
    data class AracCagirdi(val ad: String, val ozet: String) : AjanOlayi
    data class Bitti(val neden: BitisNedeni, val mesaj: String?) : AjanOlayi
}

internal enum class BitisNedeni { TAMAMLANDI, ADIM_SINIRI, IPTAL, HATA }

internal class AjanDongusu(
    private val client: LlmClient,
    private val yurutucu: AracYurutucu,
    private val araclar: List<AracTanimi>,
    private val maxAdim: Int,
) {
    fun calistir(gorev: String): Flow<AjanOlayi>
}
```

Döngü saf: `LlmClient` ve `AracYurutucu` enjekte edilir, ikisi de sahtelenebilir.
`Flow` döndürmesi H3 içindir — kullanıcı ne olduğunu adım adım görür ve akışı
iptal ederek durdurur.

### Araçlar

| Ad | Ne yapar | Neden |
|---|---|---|
| `dosya_ara` | Projede metin arar, dosya/satır döndürür | Keşfin başlangıcı |
| `dosya_oku` | Bir dosyanın içeriğini döndürür | Bağlam gerektiğinde |
| `dosya_duzenle` | `yol`, `eski`, `yeni` alır; **diske yazmaz**, plana ekler | Yazma döngüden sonra |

Üçü de. Dosya oluşturma, silme, komut çalıştırma **yok** (spec kapsam dışı).

### Düzenleme planı (saf)

```kotlin
internal data class Duzenleme(val yol: String, val eski: String, val yeni: String)

internal class DuzenlemePlani {
    fun ekle(d: Duzenleme)
    /** Aynı dosyadaki düzenlemeler sırayla uygulanır. */
    fun dosyaBazinda(): Map<String, List<Duzenleme>>
}
```

### Uygulama (`dev.acp.editor`)

```kotlin
internal object TopluUygulayici {
    /** Her dosya için yeni içeriği hesaplar; eski metin bulunamazsa o düzenleme atlanır. */
    fun hesapla(project: Project, plani: DuzenlemePlani): TopluSonuc

    /** Çok dosyalı diff önizlemesi. */
    fun onizle(project: Project, sonuc: TopluSonuc): Boolean

    /** Hepsi tek WriteCommandAction içinde — tek geri alma adımı. */
    fun uygula(project: Project, sonuc: TopluSonuc)
}
```

Doğrulanan platform API'leri (2026-09-04, derlenerek):

- `SimpleDiffRequestChain(istekler)` + `DiffManager.showDiff(project, chain, hints)`
  → çok dosyalı önizleme
- `WriteCommandAction.writeCommandAction(project, *dosyalar).withName(...).withGroupId(...)`
  → çok dosyada **tek geri alma adımı**

### Ayarlar

| Alan | Tip | Varsayılan | Not |
|---|---|---|---|
| `agentUygulamaKipi` | enum | `ONIZLE` | `ONIZLE` \| `DOGRUDAN` (spec H4) |
| `agentMaxAdim` | Int | 20 | Sonsuz döngü koruması |
| `agentMaxDosya` | Int | 20 | Tek işte dokunulacak dosya üst sınırı |
| `agentAracZamanAsimiMs` | Int | 30.000 | Tek araç çağrısı için |

Kip **metin olarak** saklanır (006'daki gerekçe: eski ayar dosyası çökmesin,
tanınmayan değer güvenli tarafa düşsün — burada `ONIZLE`).

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `dev/acp/llm/ChatModels.kt` | `AracTanimi`, `AracCagrisi`, `ChatDelta.ToolCall`, `Role.TOOL` |
| `dev/acp/llm/openai/OpenAiCodec.kt` | `tools` kodlaması, `tool_calls` çözümü |
| `dev/acp/llm/agent/AjanDongusu.kt` | yeni — saf |
| `dev/acp/llm/agent/DuzenlemePlani.kt` | yeni — saf |
| `dev/acp/agent/IdeAracYurutucu.kt` | yeni — arama, okuma, düzenleme toplama |
| `dev/acp/editor/TopluUygulayici.kt` | yeni |
| `dev/acp/settings/AcpSettings.kt` | dört yeni alan |
| `dev/acp/ui/chat/*` | ajan olaylarının gösterimi, durdurma |

## Yeniden Kullanılacak Mevcut Kod

**Kendi kodumuz:** `OpenAiLlmClient` (akış, hata eşlemesi, iptal),
`HttpClientFactory`, `AcpSettings`/`AcpSecrets`, `ChatSessionService`'in iptal
mekanizması, `FakeSglangServer`, `Truncator` (bağlam bütçesi).

**Platform:** çok dosyalı diff, tek adımlı geri alma, proje geneli metin arama
(`FindManager` — dile özgü değil, dört IDE'de de çalışır).

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok — araç çağırma düz JSON
- [x] `dev.acp.llm` saf kalıyor — ajan döngüsü ve düzenleme planı orada
- [x] Dile özgü API yok — arama metin tabanlı, PSI kullanılmıyor
- [x] Metinler `AcpBundle`'dan
- [x] Ayarlanabilir değerler `AcpSettings`'te
- [ ] ⚠️ **006/007'nin "onay olmadan yazma" kuralı, ayarla açılan bir kipte
      bilinçli olarak esnetiliyor** (spec H4). Varsayılan güvenli taraf.

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| **Model gerçek projede döngüyü sürdüremiyor** | Özellik işe yaramaz | **T10'da, kod yazmadan önce gerçek depoda ölçülür** |
| Bağlam bütçesi doluyor | Döngü ortasında hata | Araç sonuçları kırpılır; adım sınırı |
| Model eski metni birebir veremiyor | Düzenlemeler atlanır | Atlananlar kullanıcıya **bildirilir**, sessizce yutulmaz |
| Yarısı uygulanmış değişiklik | Kod derlenmez, nerede kaldığı belirsiz | Tek `WriteCommandAction`; hesaplama uygulamadan **önce** biter |
| Geri alma dosya dosya oluyor | Kullanıcı 10 kez Ctrl+Z'ye basar | `withGroupId` — derlenerek doğrulandı, ayrıca elle test |
| Döngü durmuyor | Kullanıcı bekler, maliyet | Adım ve dosya sınırı; her an iptal |
| "Doğrudan uygula" kipinde beklenmedik değişiklik | Güven kaybı | Varsayılan değil; tek adımda geri alınabilir |

## Test Stratejisi

- **Saf birim (çoğunluk):** `AjanDongusu` sahte `LlmClient` + sahte
  `AracYurutucu` ile — adım sınırı, iptal, araç hatası, boş sonuç, döngü
  tamamlanması. IDE gerekmez.
- **Kodek:** `tools` gövdeye doğru yazılıyor mu, `tool_calls` doğru çözülüyor mu;
  **araç yokken gövde birebir eskisi gibi** (gerileme).
- **Sahte sunucu:** araç çağıran uçtan uca senaryo.
- **`BasePlatformTestCase`:** `TopluUygulayici` — bayat dosya atlanıyor,
  hesaplama uygulamadan önce bitiyor, tek geri alma adımı.
- **Gerileme kapısı:** 001–007'nin **363 testi değiştirilmeden** geçmeli.
- **Elle (`runIde`):** gerçek bir değişiklik, çok dosyalı önizleme, tek Ctrl+Z,
  durdurma.

## Uygulama Sırası

Riskli olan ilk sırada.

1. **Gerçek depoda döngü ölçümü** — kod yazmadan. Sürdüremiyorsa tasarım değişir.
2. **Araç çağırma istemcide** — kodek + `ChatDelta.ToolCall`; gerileme kapısı burada
3. **Ajan döngüsü** — saf, sahte araçlarla
4. **Araçların IDE uygulaması** — arama, okuma, düzenleme toplama
5. **Toplu uygulama** — önizleme + tek geri alma adımı
6. **Ayarlar ve arayüz** — kip seçimi, adım gösterimi, durdurma
7. **Sağlamlaştırma** — sınırlar, iptal, bildirilen atlamalar
