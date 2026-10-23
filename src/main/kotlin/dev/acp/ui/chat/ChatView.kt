package dev.acp.ui.chat

import com.intellij.openapi.Disposable
import dev.acp.service.ChatUiState
import kotlinx.coroutines.flow.Flow
import javax.swing.JComponent

/**
 * Sohbet görünümü.
 *
 * **Kural: görünüm `Project`, `Editor`, `LlmClient` veya ayarlara dokunmaz.**
 * Değişmez bir [ChatUiState] çizer ve kullanıcı niyetlerini [actions] üzerinden
 * yayınlar. Bu disiplin tutarsa görünüm ileride tamamen değiştirilebilir
 * (örn. JCEF) ve testlerde durum elle kurulabilir.
 */
internal interface ChatView : Disposable {

    val component: JComponent

    /** Tam durumu çizer. Idempotent olmalı: aynı durumla iki kez çağrılabilir. */
    fun render(state: ChatUiState)

    /** Açılışta hatırlanan kipi seçer (spec 010 T53). Eylem yaymaz. */
    fun kipiAyarla(kip: PanelKipi)

    val actions: Flow<ChatViewAction>
}

/** Paneldeki üç kip (spec 008 T93, 010 T50). Ad olarak saklanır. */
enum class PanelKipi {
    SOHBET, UZMAN, COK_DOSYALI;

    companion object {
        /** Tanınmayan değer Sohbet'e düşer (elle düzenlenmiş/eski workspace.xml). */
        fun coz(ad: String?): PanelKipi = entries.firstOrNull { it.name == ad } ?: SOHBET
    }
}

sealed interface ChatViewAction {
    /** Kullanıcı kip değiştirdi; bağlantı katmanı proje ayarına yazar (T53). */
    data class KipDegisti(val kip: PanelKipi) : ChatViewAction

    data class Gonder(val metin: String, val kip: dev.acp.service.SohbetKipi = dev.acp.service.SohbetKipi.SOHBET) : ChatViewAction
    data object Iptal : ChatViewAction
    data object YenidenDene : ChatViewAction
    data object Onizle : ChatViewAction
    data object Uygula : ChatViewAction

    // ---- çok dosyalı düzenleme (spec 008) ----

    /** Yazılan metin ajana gider, sohbete değil. */
    data class AjanBaslat(val gorev: String) : ChatViewAction
    data object AjanDurdur : ChatViewAction
    data object AjanOnizle : ChatViewAction
    data object AjanUygula : ChatViewAction
    data object AjanVazgec : ChatViewAction
    /** Spec 009: onay bekleyen doğrulama komutu. */
    data object AjanKomutCalistir : ChatViewAction
    data object AjanKomutAtla : ChatViewAction
}
