# Plan: Çekirdek ve Model Bağlantısı

- **Spec no:** 001 — [spec.md](spec.md)
- **Tarih:** 2026-09-03
- **Durum:** Onaylandı

---

## Yaklaşım

Tek Kotlin eklentisi, yalnızca `com.intellij.modules.platform`'a bağlı — dört IDE'de
de aynı paket çalışır.

Model çağrısı JDK'nın `java.net.http.HttpClient`'ı ile yapılır, SSE elle ayrıştırılır,
JSON için platformun bundle ettiği Jackson `JsonNode` seviyesinde kullanılır.
**Eklenti hiçbir jar göndermez.**

LLM katmanı (`dev.acp.llm`) saf tutulur: `com.intellij` importu yasaktır. Böylece
IDE başlatmadan saniyeler içinde test edilir ve ileride başka bir yerde yeniden
kullanılabilir.

Akış `Flow<ChatDelta>` taşır — ham metin değil. v2'de araç çağırma eklenirken
imza değiştirmemeyi sağlar (H3).

## Değerlendirilen alternatifler

### JSON/HTTP yığını

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| JDK `HttpClient` + platform Jackson (`JsonNode`) | Sıfır jar gönderilir, sürüm sürüklenmesine bağışık | DTO eşlemesi elle (~150 satır) | **Seçildi** |
| JDK `HttpClient` + kotlinx.serialization | `@Serializable` ile az kod | Üretilen serializer'lar kontrol etmediğimiz runtime sürümüne bağlanır | Elendi |
| OkHttp + hazır OpenAI istemcisi | En hızlı başlangıç | OkHttp+Okio+serialization sürükler, hepsi platformda da var | Elendi |
| Ktor client | Hoş SSE eklentisi | Gönderilen yüzey en büyüğü | Elendi |

Karar kriteri: `PluginClassLoader` platform sınıflarında parent-first çalışır.
Gönderdiğimiz bir jar platformda da varsa, kod bizimkine karşı derlenip
platformunkiyle çalışır → `LinkageError`, **kullanıcının IDE'sinde, çalışma
zamanında**. Riski "muhtemelen sorun olmaz"dan **yapısal olarak sıfır**a indiriyoruz.

### Ayarların saklanması

| Alternatif | Karar |
|---|---|
| Gizli olmayanlar `PersistentStateComponent`, anahtar `PasswordSafe` | **Seçildi** — anahtar ayar dosyasına hiç yazılmaz (H1) |
| Hepsi `PersistentStateComponent` | Elendi — anahtar düz metin diske yazılırdı |

Anahtar, servis adı base URL'den türetilerek saklanır; iki sunucu arasında geçişte
her ikisinin anahtarı da korunur.

---

## Arayüzler

### LLM katmanı (`dev.acp.llm` — saf)

```kotlin
interface LlmClient {
    fun streamChat(request: ChatRequest): Flow<ChatDelta>   // iptal edilebilir
    suspend fun listModels(): List<ModelInfo>               // "bağlantıyı test et"
}
```

Yazılmış ve test edilmiş parçalar: `SseLineSplitter`, `SseParser`, `ThinkTagFilter`,
`OpenAiCodec`, `awaitCancellable`, `coalesceText`, `ChatModels`, `LlmError`.

### Ağ katmanı (`dev.acp.net`)

```kotlin
internal object HttpClientFactory {
    fun create(state: AcpSettings.State): HttpClient
}
```

`java.net.http.HttpClient` IDE'nin vekil ve sertifika ayarlarını **kendiliğinden
kullanmaz**. Fabrika ikisini açıkça bağlar:

- `ProxySelector` → IDE'nin vekil yapılandırması
- `SSLContext` → `CertificateManager.getInstance().sslContext` (kurumsal iç CA)

Bağlanmazsa kurumsal ağda hata anlaşılmaz bir `SSLHandshakeException` olur (H2 son
kriter). **Bu yüzden erken doğrulanıyor.**

### Ayarlar

`AcpSettings` — uygulama seviyesinde `PersistentStateComponent`, `acp.xml`:

| Alan | Tip | Varsayılan (değiştirilebilir) | Not |
|---|---|---|---|
| `baseUrl` | String | **boş** | Koda gömülü adres yok |
| `model` | String | **boş** | Bağlantı testinden gelen listeden seçilir |
| `contextTokenBudget` | Int | 8000 | Yanıta yer bırakır, gereksiz ön-işlemeyi önler; ölçümle güncellenecek |
| `maxTokens` | Int | 2048 | |
| `temperatureMilli` | Int | 200 | Binde tamsayı: `BaseState` float delegesi belirsiz, ayrıca yerel ondalık ayracından bağımsız |
| `connectTimeoutMs` | Int | 10 000 | |
| `readTimeoutMs` | Int | 180 000 | Uzun üretimler için cömert |
| `enableThinking` | Boolean | false | Sunucuya `extraBody` ile iletilir |

**API anahtarı `State`'e girmez** — `PasswordSafe` + `CredentialAttributes`.

### plugin.xml uzantıları

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable parentId="tools" id="dev.acp.settings"
                             instance="dev.acp.settings.AcpSettingsConfigurable"
                             key="settings.title" bundle="messages.AcpBundle"/>
    <notificationGroup id="ACP" displayType="BALLOON"/>
</extensions>
```

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `dev/acp/llm/openai/OpenAiLlmClient.kt` | yeni — `LlmClient` uygulaması |
| `dev/acp/net/HttpClientFactory.kt` | yeni — vekil + sertifika |
| `dev/acp/settings/AcpSettings.kt` | yeni — `PersistentStateComponent` |
| `dev/acp/settings/AcpSecrets.kt` | yeni — `PasswordSafe` |
| `dev/acp/settings/UrlNormalizer.kt` | yeni — saf, test edilebilir |
| `dev/acp/settings/AcpSettingsConfigurable.kt` | yeni — ayar ekranı + bağlantı testi |
| `dev/acp/settings/AcpSettingsListener.kt` | yeni — ayar değişince istemci yeniden kurulur |
| `dev/acp/service/LlmClientService.kt` | yeni — uygulama servisi, `HttpClient` sahibi |
| `META-INF/plugin.xml` | düzenleme — configurable + notificationGroup |

## Yeniden Kullanılacak Mevcut Kod

**Kendi kodumuz — 66 testi geçiyor, yeniden yazılmayacak:** `SseLineSplitter`,
`SseParser`, `ThinkTagFilter`, `OpenAiCodec`, `awaitCancellable`, `coalesceText`,
`ChatModels`, `LlmError`, `AcpBundle`, `AcpBranding`, `FakeSglangServer`,
`ArchitectureTest`.

**Platform zaten sunuyor — yazma:**

| İhtiyaç | Platform API'si |
|---|---|
| Kurumsal CA güveni | `CertificateManager.getInstance().sslContext` |
| Vekil sunucu | IDE'nin proxy seçicisi |
| Güvenli anahtar saklama | `PasswordSafe` + `CredentialAttributes` |
| Ayar ekranı | `BoundSearchableConfigurable` + Kotlin UI DSL |
| Servis + coroutine kapsamı | `@Service` + kurucuya enjekte edilen `CoroutineScope` |

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok
- [x] `dev.acp.llm` saf kalıyor — `HttpClientFactory` ayrı pakette, istemciye hazır
      `HttpClient` enjekte edilir
- [x] Dile özgü API yok
- [x] Metinler `AcpBundle`'dan
- [x] Koda gömülü adres/model yok

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Kurumsal TLS/vekil çalışmaz | Eklenti kurumda hiç kullanılamaz | T25 gerçek sunucuya karşı, arayüzden **önce** |
| İptal sunucuya ulaşmaz, GPU boşa üretir | Paylaşılan donanımda gerçek maliyet | T33 sahte sunucuda `clientDisconnected` ile kanıtlanır |
| `PasswordSafe` EDT'de bloklar | Ayar ekranı donar | Anahtar okuması arka planda; servis bellekte önbelleğe alır |
| Gradle plugin 2.18.1'e yükseltme | Testler sessizce koşmaz olur | `AGENTS.md`'de uyarı |

## Test Stratejisi

- **Saf birim:** SSE ✅, `ThinkTagFilter` ✅, `OpenAiCodec` ✅, `coalesceText` ✅,
  `awaitCancellable` ✅, `UrlNormalizer`
- **Sahte sunucu:** mutlu akış, 401, 400, 500, `[DONE]` gelmeden EOF, ve **iptalin
  soketi gerçekten kapattığı**
- **`BasePlatformTestCase`:** ayarların XML'inde anahtarın **bulunmaması**
- **Elle:** T25 gerçek sunucuya karşı bağlantı testi

## Uygulama Sırası

Riskli olan önde: **ağ katmanı, istemciden ve arayüzden önce.**

1. Ayarlar ve gizli saklama (H1)
2. Ağ katmanı — vekil + sertifika
3. Bağlantı testi, gerçek sunucuya karşı (H2) — *kurumsal ağ burada kanıtlanır*
4. İstemci — akış, hata eşlemesi, iptal (H3)
