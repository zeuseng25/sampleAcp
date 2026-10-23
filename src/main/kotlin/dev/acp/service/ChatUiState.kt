package dev.acp.service

/**
 * Sohbet panelinin **değişmez** durumu.
 *
 * Görünüm yalnızca bunu çizer; `Project`, `Editor`, `LlmClient` veya ayarlara
 * dokunmaz. Bu disiplin tutarsa görünüm ileride tamamen değiştirilebilir ve
 * testlerde durum elle kurulabilir.
 */

@JvmInline
value class MessageId(val value: Long)

/**
 * Panelde gösterilen ajan durumu (spec 008).
 *
 * Sohbet durumundan **ayrı** tutuluyor: ajan çalışırken sohbet mesajları
 * değişmiyor, ve ikisi birbirini bozmuyor.
 */
data class AjanUi(
    val acik: Boolean = false,
    /**
     * Kullanıcının yazdığı görev.
     *
     * Panelde gösterilmesi gerekiyor: yalnızca adımlar görünürse kullanıcı ne
     * istediğini kaybediyor (kullanıcı bulgusu 2026-09-05).
     */
    val gorev: String? = null,
    val calisiyor: Boolean = false,
    /** Atılan adımlar; en yenisi sonda. */
    val adimlar: List<String> = emptyList(),
    /** İş bitip değişiklikler hesaplandığında dolar. */
    val ozet: AjanOzet? = null,
    val mesaj: String? = null,
    val hata: String? = null,
    /** Kullanıcıya görünür olmalı: onayı atlayan kip gizli kalmamalı. */
    val dogrudanUygula: Boolean = false,
    /** Onay bekleyen doğrulama komutu (spec 009); null = beklenen yok. */
    val bekleyenKomut: String? = null,
    /** Komut çıktısı veya ayar bilgisi; kullanıcı da görmeli (spec 009). */
    val komutBilgisi: String? = null,
    /**
     * Tur başladığında sohbette kaç mesaj vardı — görünüm ajan turunu bu konuma
     * çizer; sonra sorulan sohbet soruları altına gelir.
     */
    val sohbetSirasi: Int = Int.MAX_VALUE,
)

/** Hesaplanmış değişikliklerin özeti. */
data class AjanOzet(
    val dosyalar: List<String>,
    val toplamDegisiklik: Int,
    /** Uygulanamayan düzenlemeler — **gösterilmeli**, sessizce yutulmamalı. */
    val atlananlar: List<String>,
)

enum class ChatRole { USER, ASSISTANT }

/** Sohbet panelinin kipi (spec 010). Çok dosyalı kip ayrı serviste (008). */
enum class SohbetKipi { SOHBET, UZMAN }

/** Panelde "bu soruya şunlar eklendi" diye gösterilen bağlam rozeti. */
data class ContextRozeti(val ad: String, val detay: String? = null)

data class ChatMessageUi(
    val id: MessageId,
    val role: ChatRole,
    val text: String = "",
    /**
     * Modele gidecek **ham talimat**. Hazır eylemlerde prompt şablonudur;
     * kullanıcı panelde bunun yerine kısa eylem adını görür ([text]).
     * `null` ise [text] kullanılır.
     */
    val talimat: String? = null,
    /**
     * Modele **gerçekten gönderilen** gövde. Bağlam eklendiğinde [text]'ten
     * farklıdır: kullanıcı panelde yalnızca kendi sorusunu görür, model ise
     * bağlamla birlikte alır.
     */
    val gonderilenMetin: String? = null,
    /** Modelin düşünme bölümü. Boşsa gösterilmez. */
    val reasoning: String = "",
    val context: List<ContextRozeti> = emptyList(),
    val streaming: Boolean = false,
    /** Asistanın araştırma adımları (spec 010 H3); cevabın üstünde gösterilir. */
    val adimlar: List<String> = emptyList(),
    /** Kullanıcı mesajında: hangi kipte soruldu — "yeniden dene" aynı kipte sorsun. */
    val kip: SohbetKipi = SohbetKipi.SOHBET,
)

/**
 * @param yenidenDenenebilir "Yeniden dene" düğmesi gösterilsin mi. Yapılandırma
 *   hatalarında `false` — tekrar denemek aynı sonucu verir, kullanıcının önce
 *   ayarları düzeltmesi gerekir.
 */
data class ChatError(val mesaj: String, val yenidenDenenebilir: Boolean)

/**
 * Editöre uygulanmayı bekleyen sonuç.
 *
 * Yalnızca kodu değiştiren eylemlerde ve model gerçekten bir kod bloğu
 * döndürdüğünde dolu olur; aksi hâlde uygulama hiç önerilmez (spec 003 H2).
 */
data class UygulanabilirSonuc(val mesajId: MessageId, val kod: String)

data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val uygulanabilir: UygulanabilirSonuc? = null,
    /** Bir yanıt akıyor mu — iptal düğmesi buna bakar. */
    val streaming: Boolean = false,
    val error: ChatError? = null,
    /** Çok dosyalı düzenleme (spec 008). Varsayılan kapalı. */
    val ajan: AjanUi = AjanUi(),
)
