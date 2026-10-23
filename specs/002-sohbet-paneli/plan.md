# Plan: Sohbet Paneli

- **Spec no:** 002 — [spec.md](spec.md)
- **Tarih:** 2026-09-03
- **Durum:** Taslak

---

## Yaklaşım

Panel üç katmana ayrılır ve aralarındaki tek bağ değişmez bir durum nesnesidir:

```
ChatSessionService  (durum + LLM çağrısı)  →  ChatUiState  →  ChatView  (çizim)
        ↑                                                          │
        └──────────────────  ChatViewAction  ←─────────────────────┘
```

**Görünüm; `Project`, `Editor`, `LlmClient` veya ayarlara dokunmaz.** Değişmez bir
`ChatUiState` çizer, kullanıcı niyetlerini yayınlar. Bu disiplin tutarsa görünüm
ileride tamamen değiştirilebilir (örn. JCEF) ve `BasePlatformTestCase` ile test
edilebilir kalır.

Konuşma durumu **proje seviyesindeki serviste** tutulur, panelde değil (H1: panel
kapatılıp açılınca konuşma yerinde durur). Panel yalnızca bir görüntüleyicidir.

Akış sıcak yolu: `Flow<ChatDelta>` → `coalesceText` (~50ms) → EDT → görünüm.
Token başına yeniden çizim panelin donmasına yol açar (H1: "arayüz donmaz").

## Değerlendirilen alternatifler

### Konuşma durumu nerede tutulur

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Proje servisi (`ChatSessionService`) | Panel kapansa da yaşar; test edilebilir | — | **Seçildi** |
| Panel bileşeninin içinde | Daha az kod | Panel kapanınca konuşma ölür — H1 kriteri karşılanmaz | Elendi |
| Diske kalıcı | Oturumlar arası korunur | Spec kapsam dışı; kullanıcı kodu diske yazılır | Elendi |

### Akış sırasında markdown

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Akarken düz metin, bitince yeniden çiz | Ekleme O(delta); jank yok | Kod bloğu ancak yanıt bitince renklenir | **Seçildi** |
| Her token'da markdown ayrıştır | Anında biçimli | Her token'da tüm mesaj yeniden ayrıştırılır → panel kilitlenir | Elendi |

### Kod bloğu çizimi

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Salt-okunur `EditorTextField` | IDE'nin **gerçek** renklendirmesi, her dil için doğru | Ağır; `releaseEditor` zorunlu | **Seçildi** |
| Kendi renklendiricimiz | Hafif | Her dil için ayrı iş, IDE'den farklı görünür | Elendi |

### Markdown ayrıştırma

Bundled Markdown eklentisine bağlanılmıyor: JetBrains kendi destek yanıtında
panel API'sinin "kararsız olduğu ve gözden geçirileceğini" söylüyor. Ayrıca dile
özgü olmayan tek ihtiyacımız kod bloklarını ayırmak — kendi `MarkdownFenceSplitter`
bunu ~80 satırda yapıyor ve testlenebiliyor.

---

## Arayüzler

### Durum (`dev.acp.service`)

```kotlin
data class ChatUiState(
    val messages: List<ChatMessageUi>,
    val streaming: Boolean,          // iptal düğmesi bunun tersiyle görünür
    val error: ChatError? = null,
)

data class ChatMessageUi(
    val id: MessageId,
    val role: Role,                  // USER, ASSISTANT
    val text: String,
    val reasoning: String = "",      // düşünme bölümü; boşsa gösterilmez
    val context: List<ContextRozeti> = emptyList(),  // H3: ne paylaşıldığı
    val streaming: Boolean = false,
)

data class ChatError(val message: String, val yenidenDenenebilir: Boolean)
```

```kotlin
@Service(Service.Level.PROJECT)
internal class ChatSessionService(private val project: Project, private val scope: CoroutineScope) {
    val state: StateFlow<ChatUiState>
    fun send(soru: String)
    fun cancel()
    fun retry()        // son soruyu BAŞTAN sorar (spec Belirsizlik 2)
    fun clear()
}
```

`retry()` yarım yanıtı siler ve son kullanıcı sorusunu yeniden gönderir.
"Kaldığı yerden devam" bilinçli olarak yok — spec'te gerekçesiyle elendi.

### Görünüm (`dev.acp.ui.chat`)

```kotlin
internal interface ChatView : Disposable {
    val component: JComponent
    fun render(state: ChatUiState)                           // idempotent, tam durum
    fun appendDelta(messageId: MessageId, delta: ChatDelta)  // sıcak yol
    val actions: Flow<ChatViewAction>
}

sealed interface ChatViewAction {
    data class Gonder(val metin: String) : ChatViewAction
    data object Iptal : ChatViewAction
    data object YenidenDene : ChatViewAction
    data class Kopyala(val metin: String) : ChatViewAction
}
```

`render` tam durumu çizer (nadir), `appendDelta` yalnızca akan mesaja ekler (sık).
İkisini ayırmak, akış sırasında tüm listeyi yeniden çizmemeyi sağlar.

### Bağlam (`dev.acp.context`)

```kotlin
internal interface ContextProvider {
    val priority: Int
    suspend fun collect(request: ContextRequest): List<ContextItem>
    companion object { val EP = ExtensionPointName<ContextProvider>("dev.acp.contextProvider") }
}

internal data class ContextItem(
    val kind: Kind,                 // SELECTION, SURROUNDING, CURRENT_FILE, PROJECT_META
    val displayName: String,        // panelde rozet olarak görünür (H3)
    val path: String?, val language: String?,
    val lineRange: IntRange?, val text: String, val truncated: Boolean,
)
```

Sağlayıcılar **biçimlenmiş prompt değil, tiplenmiş öğe listesi** döner; prompt'a
çevirme tek yerde (`PromptBuilder`). Böylece ileride PSI tabanlı bir sağlayıcı
yalnızca yeni öğeler katkı verir, tüketici değişmez.

### plugin.xml

```xml
<extensionPoints>
    <extensionPoint name="contextProvider" interface="dev.acp.context.ContextProvider" dynamic="true"/>
</extensionPoints>
```

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `dev/acp/service/ChatSessionService.kt` | yeni — durum, gönderme, iptal, yeniden dene |
| `dev/acp/service/ChatUiState.kt` | yeni — değişmez durum tipleri |
| `dev/acp/ui/chat/ChatView.kt` | yeni — arayüz + eylemler |
| `dev/acp/ui/chat/SwingChatView.kt` | yeni — transcript, giriş alanı, düğmeler |
| `dev/acp/ui/chat/MessageBlockRenderer.kt` | yeni — düz metin / kod bloğu ayrımı |
| `dev/acp/ui/chat/CodeBlockComponent.kt` | yeni — `EditorTextField` + kopyala |
| `dev/acp/ui/chat/ReasoningSection.kt` | yeni — katlanmış düşünme bölümü |
| `dev/acp/ui/chat/AutoscrollController.kt` | yeni — kaydırma kuralı |
| `dev/acp/ui/chat/ChatToolWindowFactory.kt` | düzenleme — yer tutucu yerine gerçek görünüm |
| `dev/acp/llm/text/MarkdownFenceSplitter.kt` | yeni — saf |
| `dev/acp/llm/text/TokenEstimator.kt`, `Truncator.kt` | yeni — saf |
| `dev/acp/llm/prompt/PromptBuilder.kt` | yeni — saf |
| `dev/acp/context/*.kt` | yeni — sağlayıcı ve uygulaması |
| `META-INF/plugin.xml` | uzantı noktası |

## Yeniden Kullanılacak Mevcut Kod

**Kendi kodumuz — 120 testi geçiyor:** `LlmClient`/`OpenAiLlmClient` (akış, iptal,
hata eşlemesi), `coalesceText`, `ThinkTagFilter`, `ChatModels`, `LlmError`,
`AcpSettings`, `AcpSecrets`, `HttpClientFactory`, `LlmClientService`, `AcpBundle`,
`FakeSglangServer`.

**Platform zaten sunuyor — yazma:**

| İhtiyaç | Platform API'si |
|---|---|
| Sözdizimi renklendirmesi | `EditorFactory.createViewer` + `EditorHighlighterFactory` |
| Tema/renk uyumu | `JBColor`, `EditorColorsManager`, `LafManagerListener` |
| Kaydırma paneli | `JBScrollPane` |
| Servis + coroutine kapsamı | `@Service` + enjekte edilen `CoroutineScope` |
| Bildirim | `NotificationGroup` "ACP" (001'de tanımlandı) |

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok — markdown kendi ayrıştırıcımız
- [x] `dev.acp.llm` saf kalıyor — `MarkdownFenceSplitter`, `Truncator`,
      `PromptBuilder` orada ve `com.intellij` importu yok
- [x] Dile özgü API yok — kod bloğu dili `FileType` üzerinden çözülüyor
- [x] Metinler `AcpBundle`'dan
- [x] Ayarlanabilir değerler `AcpSettings`'ten (bağlam bütçesi, sıcaklık, maxTokens)

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Token başına yeniden çizim paneli kilitler | H1 karşılanmaz | `coalesceText` + akışta düz metin; markdown yalnızca bitince |
| `EditorTextField` sızıntısı | Uzun oturumda bellek şişer | `EditorFactory.releaseEditor` zorunlu, `Disposable`'a bağlanır; testle doğrulanır (H2 son kriter) |
| Uzun sohbette çok sayıda canlı editör | Bellek | Son N mesajda gerçek editör; eskiler statik metin |
| Bağlam sessizce çok büyük gider | Maliyet ve yavaşlık | Rozet kullanıcıya ne gittiğini gösterir; bütçe ayarlardan |
| Otomatik kaydırma okumayı bozar | Kullanılamaz panel | Yalnızca kullanıcı zaten dipteyse kaydır |

## Test Stratejisi

- **Saf birim (IDE gerekmez):** `MarkdownFenceSplitter` (kapanmamış blok, `~~~`,
  dil etiketi), `TokenEstimator`, `Truncator` (seçim asla kırpılmaz, satır
  sonunda kırpılır), `PromptBuilder` (altın dosyalar)
- **`BasePlatformTestCase`:** `ChatSessionService` (gönder → durum akışı, iptal,
  yeniden dene son soruyu tekrarlar), bağlam toplama, editör sızıntısı
- **Sahte sunucuya karşı:** uçtan uca akış, yarıda kesilme, iptal
- **Elle (`runIde`):** akış janksız mı, tema değişince panel uyuyor mu, yukarı
  kaydırınca zorla aşağı inmiyor mu

## Uygulama Sırası

Görünür sonuç önce; süsleme sonra.

1. **Durum + servis** — `ChatUiState`, `ChatSessionService` (H1 çekirdeği)
2. **Düz metin panel** — gönder, akış, iptal. *İlk kez gerçekten sohbet edilebilir.*
3. **Geçmiş** — panel kapanıp açılınca konuşma durur; "yeniden dene"
4. **Markdown ve kod blokları** — fence ayrıştırma, `EditorTextField`, kopyala
5. **Düşünme bölümü** — katlanmış, kapalı
6. **Kaydırma ve tema** — otomatik kaydırma kuralı, tema dinleyicisi
7. **Bağlam** — sağlayıcı, bütçe/kırpma, rozetler (H3)
