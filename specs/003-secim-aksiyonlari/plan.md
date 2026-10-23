# Plan: Seçim Aksiyonları

- **Spec no:** 003 — [spec.md](spec.md)
- **Tarih:** 2026-09-04
- **Durum:** Taslak

---

## Yaklaşım

Dört eylem, **tek bir taban sınıfın** dört ince türevi. Her biri yalnızca hangi
prompt şablonunu kullanacağını ve sonucun editöre uygulanıp uygulanmayacağını
söyler; gerisi ortak.

Sonuç **sohbet panelinde akar** — ayrı bir pencere açılmaz. Böylece kullanıcı
ilerlemeyi görür, iptal edebilir, ve konuşma bağlamı korunur. Panel altyapısı
[002](../002-sohbet-paneli/)'de hazır; burada yalnızca yeni bir giriş noktası
ekleniyor.

Kodu değiştiren eylemlerde zincir: **yanıt → kod bloğunu ayıkla → karşılaştırmalı
önizleme → onay → tek geri alma adımı.** Hiçbir aşamada kullanıcı onayı atlanmaz.

## Değerlendirilen alternatifler

### Prompt metinleri nerede

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Pakette, düzenlenemez | İyileştirme sürümle gelir, herkes aynı kaliteyi alır | Kullanıcı kendi tarzına uyarlayamaz | **Seçildi** |
| Ayarlardan düzenlenebilir | Esnek | Bozuk prompt = destek yükü; yayınlanan iyileştirme kullanıcıda eski kalır | Elendi |
| Dosya + geçersiz kılma | İkisi birden | İki kaynak, "hangisi geçerli" karmaşası | Elendi |

Altın Kural 6 burada geçerli değil: prompt bir yapılandırma değeri değil, ürünün
davranışı.

### Sonuç nerede gösterilir

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Sohbet panelinde akar | İptal, ilerleme, geçmiş bedava gelir | Panel açık değilse açılmalı | **Seçildi** |
| Ayrı açılır pencere | Odaklı | İptal/akış/geçmiş baştan yazılır | Elendi |

### Değişikliğin uygulanması

| Alternatif | Artı | Eksi | Karar |
|---|---|---|---|
| Karşılaştırmalı önizleme + onay | Kullanıcı ne olacağını görür | Bir tık fazla | **Seçildi** |
| Doğrudan uygula, geri alınabilir | Hızlı | Kod sessizce değişir; spec bunu yasaklıyor | Elendi |
| Editör içi satır satır kabul/reddet | En rahat | Inlay/gutter işi; v1 için fazla | Ertelendi |

---

## Arayüzler

### Prompt şablonları (`dev.acp.llm.prompt` — saf)

```kotlin
internal enum class AksiyonTuru { ACIKLA, TEST_YAZ, YENIDEN_DUZENLE, DOKUMAN_EKLE }

internal object PromptSelector {
    /** Dosya türüne ve eyleme göre şablon. */
    fun sablon(tur: AksiyonTuru, dilEtiketi: String?): String
}
```

Şablonlar `resources/prompts/<tur>.md` altında; dile özgü ek not gerekiyorsa
`<tur>.<dil>.md` varsa o kullanılır. Dil çözümü `FileType` üzerinden — dile özgü
PSI yok (Altın Kural 3).

### Eylemler (`dev.acp.actions`)

```kotlin
internal abstract class BaseSelectionAction(
    private val tur: AksiyonTuru,
) : AnAction(), DumbAware {
    override fun update(e: AnActionEvent)      // seçim yoksa pasif
    override fun actionPerformed(e: AnActionEvent)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

Dört türev yalnızca [AksiyonTuru] veriyor. Sonucun editöre uygulanıp
uygulanmayacağı türden çıkarılıyor:

| Eylem | Editörü değiştirir mi |
|---|---|
| Açıkla | Hayır |
| Test yaz | Hayır — ayrı geçici dosyada |
| Yeniden düzenle | Evet — önizleme + onay |
| Doküman ekle | Evet — önizleme + onay |

### Hedefin sabitlenmesi (`dev.acp.editor`)

```kotlin
internal data class TargetAnchor(
    val file: VirtualFile,
    val document: Document,
    val marker: RangeMarker,
    val originalText: String,
)
```

Eylem başlarken alınır. Yanıt saniyeler sonra gelir ve kullanıcı bu arada yazmış
olabilir; yazmadan hemen önce `marker.isValid` **ve** mevcut metnin
`originalText` ile aynı olduğu kontrol edilir. Uymuyorsa uygulama **reddedilir**
(spec H2). Sessizce yanlış aralığı ezmek en kötü sonuç olurdu.

### Uygulama (`dev.acp.editor.ResultApplier`)

```kotlin
internal object ResultApplier {
    fun onizle(project: Project, anchor: TargetAnchor, yeniKod: String): Boolean
    fun uygula(project: Project, anchor: TargetAnchor, yeniKod: String)
}
```

- Önizleme: `DiffManager.showDiff` + `SimpleDiffRequest`
- Yazma: tek `WriteCommandAction` — **adlandırılmış**, böylece geri alma listesinde
  okunabilir görünür ve tek adımda geri alınır (spec H2)
- Biçimlendirme aynı komutun içinde yapılır ki geri alma ikisini birlikte alsın

### Test sonucu (`dev.acp.editor.ScratchFileWriter`)

"Test yaz" mevcut dosyayı **değiştirmez**: sonuç `ScratchRootType` ile geçici bir
dosyada açılır. Doğru kaynak klasörüne ve pakete yerleştirme spec'te kapsam dışı.

### plugin.xml

```xml
<actions>
    <group id="dev.acp.EditorPopup" text="ACP" popup="true">
        <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        <action id="dev.acp.Explain"  class="dev.acp.actions.ExplainSelectionAction"/>
        <action id="dev.acp.Test"     class="dev.acp.actions.GenerateTestAction"/>
        <action id="dev.acp.Refactor" class="dev.acp.actions.RefactorSelectionAction"/>
        <action id="dev.acp.Docs"     class="dev.acp.actions.AddDocsAction"/>
    </group>
</actions>
```

Kısayol atanmıyor; eylemler Keymap'te görünüyor ve kullanıcı isterse kendisi atar.

Alt+Enter için ayrıca `<intentionAction>` kaydı: `startInWriteAction() = false`
**zorunlu** — ağ çağrısı yazma kilidi altında yapılamaz.

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|---|---|
| `dev/acp/llm/prompt/AksiyonTuru.kt` | yeni — saf |
| `dev/acp/llm/prompt/PromptSelector.kt` | yeni — saf, şablon seçimi |
| `resources/prompts/*.md` | yeni — dört şablon |
| `dev/acp/actions/BaseSelectionAction.kt` | yeni — ortak davranış |
| `dev/acp/actions/{Explain,GenerateTest,Refactor,AddDocs}Action.kt` | yeni — ince türevler |
| `dev/acp/actions/AcpIntentionAction.kt` | yeni — Alt+Enter |
| `dev/acp/editor/TargetAnchor.kt` | yeni |
| `dev/acp/editor/ResultApplier.kt` | yeni — önizleme + yazma |
| `dev/acp/editor/ScratchFileWriter.kt` | yeni |
| `dev/acp/service/ChatSessionService.kt` | düzenleme — eylemle gelen istek girişi |
| `META-INF/plugin.xml` | eylem grubu + intention |

## Yeniden Kullanılacak Mevcut Kod

**Kendi kodumuz — 172 testi geçiyor:** `ChatSessionService` (akış, iptal, hata),
`SwingChatView`, `MarkdownFenceSplitter` (yanıttan kod bloğu ayıklamak için
**aynen**), `CompositeContextProvider` + `SelectionContextProvider`, `PromptBuilder`,
`OpenAiLlmClient`, `AcpBundle`, `FakeSglangServer`.

**Platform zaten sunuyor — yazma:**

| İhtiyaç | Platform API'si |
|---|---|
| Karşılaştırmalı önizleme | `DiffManager.showDiff` + `SimpleDiffRequest` |
| Tek adımlı geri alma | `WriteCommandAction` (`withName`) |
| Kod biçimlendirme | `CodeStyleManager.reformatText` |
| Geçici dosya | `ScratchRootType.createScratchFile` |
| Aralık takibi | `Document.createRangeMarker` |
| Eylem altyapısı | `AnAction` + `EditorPopupMenu` grubu |

---

## Altın Kural Kontrolü

- [x] Yeni çalışma zamanı bağımlılığı yok
- [x] `dev.acp.llm` saf kalıyor — `PromptSelector` orada, IDE importu yok
- [x] Dile özgü API yok — dil `FileType` üzerinden
- [x] Metinler `AcpBundle`'dan (prompt şablonları ayrı dosyalarda, kullanıcıya
      gösterilmiyor)
- [x] Koda gömülü ayarlanabilir değer yok

## Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Yanıt beklenirken kod değişir, yanlış aralık ezilir | **Veri kaybı** | `TargetAnchor` bayatlık kontrolü; uymuyorsa uygulama reddedilir |
| Ağ çağrısı yazma kilidi altında yapılır | IDE donar | `startInWriteAction() = false`; eylem coroutine'de |
| Model kod bloğu döndürmez | Kullanıcı ne uygulayacağını bilemez | Uygulama düğmesi hiç gösterilmez, sonuç yalnızca panelde |
| Biçimlendirme ayrı komutta olur | Geri alma iki adım olur | Aynı `WriteCommandAction` içinde |
| Dosya salt okunur | Sessiz başarısızlık | Yazmadan önce kontrol, sebep bildirilir |

## Test Stratejisi

- **Saf birim:** `PromptSelector` (dil eşleşmesi, şablon bulunamazsa varsayılan),
  yanıttan kod bloğu ayıklama (`MarkdownFenceSplitter` üzerinden)
- **`BasePlatformTestCase`:** eylem etkinliği (seçim var/yok), `TargetAnchor`
  bayatlık koruması, `ResultApplier` sonrası **tek adımda geri alma**, salt okunur
  dosyada reddetme, scratch dosyası oluşturma
- **Sahte sunucu:** eylemden gelen isteğin gövdesinde doğru şablonun bulunması
- **Elle (`runIde`):** sağ tık menüsü, Alt+Enter, önizleme penceresi, Ctrl+Z

## Uygulama Sırası

Riskli olan (veri kaybı) erken doğrulanıyor.

1. **Prompt şablonları + seçici** — saf, hızlı geri bildirim
2. **Açıkla eylemi** — editörü değiştirmeyen en basit yol; menü ve akış kurulur
3. **Hedef sabitleme + bayatlık koruması** — *veri kaybı riski burada kapanır*
4. **Uygulama** — önizleme, `WriteCommandAction`, tek adımlı geri alma
5. **Yeniden düzenle + doküman ekle** — 3 ve 4'ün üstüne oturur
6. **Test yaz** — scratch dosyası
7. **Alt+Enter** ve sağlamlaştırma
