package dev.acp.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import dev.acp.AcpBranding
import dev.acp.AcpBundle
import dev.acp.llm.LlmError
import dev.acp.llm.completion.FimBicimi
import dev.acp.service.LlmClientService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ayarlar → Tools → (eklenti adı).
 *
 * İki tuzağa dikkat edildi:
 *
 * 1. **`PasswordSafe` EDT'de bloklayabilir** (işletim sistemi kullanıcıya parola
 *    sorabilir). Bu yüzden anahtar ekrana hiç yüklenmiyor: alan boş başlar,
 *    boş bırakılırsa mevcut anahtar korunur. Kayıtlı anahtar olup olmadığı
 *    arka planda okunup etikete yazılır.
 * 2. **Bağlantı testi kaydedilmiş değerleri değil, ekranda yazan değerleri**
 *    kullanır — kullanıcı kaydetmeden deneyebilmeli (H2).
 */
internal class AcpSettingsConfigurable : BoundSearchableConfigurable(
    AcpBranding.displayName,
    "dev.acp.settings",
) {

    private val settings get() = AcpSettings.getInstance().state

    private val baseUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val apiKeyStatus = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
    /**
     * Seçili model. Elle de yazılabilir — sunucu ulaşılamazken kullanıcı
     * tıkanmasın diye.
     */
    private val modelField = JBTextField()

    /** Satır içi tamamlama modeli. Boş bırakılırsa sohbet modeli kullanılır. */
    private val tamamlamaModelAlani = JBTextField()

    /**
     * FIM biçimi seçici.
     *
     * Açılır liste, model adından tahmine yeğlendi: tahmin yanılırsa model
     * belirteçleri düz metin sanar ve **hata vermeden** kötü öneri üretir.
     * Görünür bir ayar, sessiz bir tahminden iyidir.
     */
    private val bicimSecici = ComboBox(FimBicimi.entries.toTypedArray()).apply {
        // SimpleListCellRenderer.create(String, Function) DEĞİL: o aşırı yükleme
        // kaldırılmak üzere işaretli (verifyPlugin bunu bulmuştu).
        renderer = textListCellRenderer { it?.gorunenAd ?: "" }
    }

    /**
     * Ajanın uygulama kipi (spec 008 H4).
     *
     * Varsayılan güvenli taraf; "doğrudan uygula" kullanıcının bilinçli tercihi.
     */
    private val kipSecici = ComboBox(AjanKipi.entries.toTypedArray()).apply {
        renderer = textListCellRenderer { it?.gorunenAd ?: "" }
    }

    /** Bağlantı testinden dönen modeller; seçici bunları gösterir. */
    private var bulunanModeller: List<String> = emptyList()

    private val testStatus = JBLabel()

    /** `reset()` sırasında okunan anahtar; `isModified` bununla karşılaştırılır. */
    private var kayitliAnahtarVar = false

    override fun createPanel(): DialogPanel = panel {
        group(AcpBundle.message("settings.group.connection")) {
            row(AcpBundle.message("settings.baseUrl")) {
                cell(baseUrlField)
                    .align(Align.FILL)
                    .bindText({ settings.baseUrl.orEmpty() }, { settings.baseUrl = normalize(it) })
                    .validationOnApply { alan ->
                        when {
                            alan.text.isBlank() ->
                                ValidationInfo(AcpBundle.message("validation.baseUrl.empty"), alan)
                            UrlNormalizer.normalize(alan.text) == null ->
                                ValidationInfo(AcpBundle.message("validation.baseUrl.invalid"), alan)
                            else -> null
                        }
                    }
                    .comment(AcpBundle.message("settings.baseUrl.comment"))
            }

            row(AcpBundle.message("settings.apiKey")) {
                cell(apiKeyField)
                    .align(Align.FILL)
                    .comment(AcpBundle.message("settings.apiKey.hint"))
            }
            row("") {
                cell(apiKeyStatus)
                link(AcpBundle.message("settings.apiKey.clear")) {
                    apiKeyField.text = ""
                    anahtariSil()
                }
            }

            row(AcpBundle.message("settings.model")) {
                cell(modelField).align(Align.FILL)
                button(AcpBundle.message("settings.model.choose")) { modelSeciciyiAc(modelField) }
            }
            row("") {
                comment(AcpBundle.message("settings.model.comment"))
            }

            row("") {
                button(AcpBundle.message("settings.test")) { baglantiyiTestEt() }
                cell(testStatus)
            }
        }

        group(AcpBundle.message("settings.group.model")) {
            row(AcpBundle.message("settings.contextBudget")) {
                intTextField()
                    .bindIntText({ settings.contextTokenBudget }, { settings.contextTokenBudget = it })
                    .comment(AcpBundle.message("settings.contextBudget.comment"))
            }
            row(AcpBundle.message("settings.maxTokens")) {
                intTextField().bindIntText({ settings.maxTokens }, { settings.maxTokens = it })
            }
            row(AcpBundle.message("settings.temperature")) {
                intTextField()
                    .bindIntText({ settings.temperatureMilli }, { settings.temperatureMilli = it })
                    .comment(AcpBundle.message("settings.temperature.comment"))
            }
            row {
                checkBox(AcpBundle.message("settings.enableThinking"))
                    .bindSelected({ settings.enableThinking }, { settings.enableThinking = it })
            }
        }

        group(AcpBundle.message("settings.group.completion")) {
            row("") {
                comment(AcpBundle.message("settings.completion.comment"))
            }
            row(AcpBundle.message("settings.completion.format")) {
                cell(bicimSecici)
                    .comment(AcpBundle.message("settings.completion.format.comment"))
            }
            row(AcpBundle.message("settings.completion.model")) {
                cell(tamamlamaModelAlani).align(Align.FILL)
                button(AcpBundle.message("settings.model.choose")) { modelSeciciyiAc(tamamlamaModelAlani) }
            }
            row("") {
                comment(AcpBundle.message("settings.completion.model.comment"))
            }
            row(AcpBundle.message("settings.completion.maxTokens")) {
                intTextField()
                    .bindIntText({ settings.completionMaxTokens }, { settings.completionMaxTokens = it })
                    .comment(AcpBundle.message("settings.completion.maxTokens.comment"))
            }
            row(AcpBundle.message("settings.completion.debounce")) {
                intTextField()
                    .bindIntText({ settings.completionDebounceMs }, { settings.completionDebounceMs = it })
                    .comment(AcpBundle.message("settings.completion.debounce.comment"))
            }
            row(AcpBundle.message("settings.completion.blockMaxTokens")) {
                intTextField()
                    .bindIntText({ settings.completionBlockMaxTokens }, { settings.completionBlockMaxTokens = it })
                    .comment(AcpBundle.message("settings.completion.blockMaxTokens.comment"))
            }
            row(AcpBundle.message("settings.completion.maxLines")) {
                intTextField()
                    .bindIntText({ settings.completionMaxLines }, { settings.completionMaxLines = it })
                    .comment(AcpBundle.message("settings.completion.maxLines.comment"))
            }
            row(AcpBundle.message("settings.completion.timeout")) {
                intTextField()
                    .bindIntText({ settings.completionTimeoutMs }, { settings.completionTimeoutMs = it })
                    .comment(AcpBundle.message("settings.completion.timeout.comment"))
            }
        }

        group(AcpBundle.message("settings.group.agent")) {
            row("") { comment(AcpBundle.message("settings.agent.comment")) }
            row(AcpBundle.message("settings.agent.mode")) {
                cell(kipSecici).comment(AcpBundle.message("settings.agent.mode.comment"))
            }
            row(AcpBundle.message("settings.agent.maxSteps")) {
                intTextField()
                    .bindIntText({ settings.agentMaxAdim }, { settings.agentMaxAdim = it })
                    .comment(AcpBundle.message("settings.agent.maxSteps.comment"))
            }
            row(AcpBundle.message("settings.agent.maxFiles")) {
                intTextField()
                    .bindIntText({ settings.agentMaxDosya }, { settings.agentMaxDosya = it })
            }
            row(AcpBundle.message("settings.agent.commandTimeout")) {
                intTextField(1..3_600)
                    .bindIntText({ settings.agentKomutZamanAsimiMs / 1000 }, { settings.agentKomutZamanAsimiMs = it * 1000 })
                    .comment(AcpBundle.message("settings.agent.commandTimeout.comment"))
            }
        }

        group(AcpBundle.message("settings.group.research")) {
            row {
                checkBox(AcpBundle.message("settings.research.chat"))
                    .bindSelected({ settings.sohbetArastirma }, { settings.sohbetArastirma = it })
                    .comment(AcpBundle.message("settings.research.chat.comment"))
            }
            row(AcpBundle.message("settings.research.chatSteps")) {
                intTextField(1..50)
                    .bindIntText({ settings.sohbetMaxAdim }, { settings.sohbetMaxAdim = it })
            }
            row(AcpBundle.message("settings.research.expertSteps")) {
                intTextField(1..50)
                    .bindIntText({ settings.uzmanMaxAdim }, { settings.uzmanMaxAdim = it })
                    .comment(AcpBundle.message("settings.research.expertSteps.comment"))
            }
        }

        group(AcpBundle.message("settings.group.network")) {
            row {
                comment(AcpBundle.message("settings.network.comment"))
            }
            row(AcpBundle.message("settings.connectTimeout")) {
                intTextField().bindIntText({ settings.connectTimeoutMs }, { settings.connectTimeoutMs = it })
            }
            row(AcpBundle.message("settings.readTimeout")) {
                intTextField()
                    .bindIntText({ settings.readTimeoutMs }, { settings.readTimeoutMs = it })
                    .comment(AcpBundle.message("settings.readTimeout.comment"))
            }
            row(AcpBundle.message("settings.idleTimeout")) {
                intTextField()
                    .bindIntText({ settings.idleTimeoutMs }, { settings.idleTimeoutMs = it })
                    .comment(AcpBundle.message("settings.idleTimeout.comment"))
            }
        }
    }

    override fun reset() {
        super.reset()
        modelField.text = settings.model.orEmpty()
        tamamlamaModelAlani.text = settings.completionModel.orEmpty()
        bicimSecici.selectedItem = settings.fimBicimi
        kipSecici.selectedItem = settings.ajanKipi
        bulunanModeller = emptyList()
        apiKeyField.text = ""
        testStatus.text = ""
        anahtarDurumunuYukle()
    }

    override fun isModified(): Boolean =
        super.isModified() ||
            apiKeyField.password.isNotEmpty() ||
            (secilenModel() != settings.model.orEmpty()) ||
            (tamamlamaModelAlani.text.trim() != settings.completionModel.orEmpty()) ||
            (bicimSecici.selectedItem != settings.fimBicimi) ||
            (kipSecici.selectedItem != settings.ajanKipi)

    override fun apply() {
        super.apply()
        settings.model = secilenModel()
        settings.completionModel = tamamlamaModelAlani.text.trim()
        settings.completionFormat = (bicimSecici.selectedItem as FimBicimi).name
        settings.agentUygulamaKipi = (kipSecici.selectedItem as AjanKipi).name

        val yeniAnahtar = String(apiKeyField.password)
        if (yeniAnahtar.isNotBlank()) {
            val adres = settings.baseUrl.orEmpty()
            LlmClientService.getInstance().scope.launch(Dispatchers.IO) {
                AcpSecrets.setApiKey(adres, yeniAnahtar)
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    apiKeyField.text = ""
                    kayitliAnahtarVar = true
                    anahtarEtiketiniGuncelle()
                }
            }
        }
    }

    // ---- yardımcılar ------------------------------------------------------

    /**
     * Model seçici açılır penceresi.
     *
     * Combo + `ComboboxSpeedSearch` denendi ve bırakıldı: arama kutusu combo'nun
     * üstünde, ilgisiz satırların yanında beliriyordu. Bu açılır pencere ise
     * arama kutusunu kendi içinde, doğru yerde gösterir — IDE'nin her yerinde
     * kullanılan desen.
     */
    private fun modelSeciciyiAc(alan: JBTextField = modelField) {
        if (bulunanModeller.isEmpty()) {
            testStatus.foreground = UIUtil.getContextHelpForeground()
            testStatus.text = AcpBundle.message("settings.model.needTest")
            return
        }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(bulunanModeller)
            .setTitle(AcpBundle.message("settings.model.chooseTitle"))
            // Arama kutusunu açan şey bu: her öğenin süzmede kullanılacak metni.
            .setNamerForFiltering { model -> model }
            .setFilterAlwaysVisible(true)
            .setItemChosenCallback { secim -> alan.text = secim }
            .setSelectedValue(alan.text.trim().ifBlank { null }, true)
            .createPopup()
            .showUnderneathOf(alan)
    }

    private fun secilenModel(): String = modelField.text.trim()

    private fun normalize(girdi: String) = UrlNormalizer.normalize(girdi) ?: girdi.trim()

    private fun anahtarDurumunuYukle() {
        val adres = settings.baseUrl.orEmpty()
        if (adres.isBlank()) {
            kayitliAnahtarVar = false
            anahtarEtiketiniGuncelle()
            return
        }
        // PasswordSafe EDT'de bloklayabilir — arka planda oku.
        LlmClientService.getInstance().scope.launch(Dispatchers.IO) {
            val varMi = AcpSecrets.getApiKey(adres) != null
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                kayitliAnahtarVar = varMi
                anahtarEtiketiniGuncelle()
            }
        }
    }

    private fun anahtarEtiketiniGuncelle() {
        apiKeyStatus.text = AcpBundle.message(
            if (kayitliAnahtarVar) "settings.apiKey.present" else "settings.apiKey.absent"
        )
    }

    private fun anahtariSil() {
        val adres = settings.baseUrl.orEmpty()
        LlmClientService.getInstance().scope.launch(Dispatchers.IO) {
            AcpSecrets.setApiKey(adres, null)
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                kayitliAnahtarVar = false
                anahtarEtiketiniGuncelle()
            }
        }
    }

    private fun baglantiyiTestEt() {
        // Ekranda yazan değerlerle test edilir, kaydedilmiş olanlarla değil (H2).
        // Alanlar doğrudan okunuyor; panel.apply() çağrılsaydı kullanıcı "İptal"e
        // bassa bile bütün değerler kaydedilmiş olurdu.
        val adres = UrlNormalizer.normalize(baseUrlField.text) ?: run {
            testStatus.foreground = UIUtil.getErrorForeground()
            testStatus.text = AcpBundle.message("validation.baseUrl.invalid")
            return
        }


        val yazilanAnahtar = String(apiKeyField.password).takeIf { it.isNotBlank() }

        testStatus.foreground = UIUtil.getContextHelpForeground()
        testStatus.text = AcpBundle.message("settings.test.running")

        LlmClientService.getInstance().scope.launch(Dispatchers.IO) {
            val sonuc = runCatching {
                val anahtar = yazilanAnahtar ?: AcpSecrets.getApiKey(adres)
                LlmClientService.getInstance().listModels(adres, anahtar)
            }
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                sonuc
                    .onSuccess { modeller -> basariGoster(modeller.map { it.id }) }
                    .onFailure { hataGoster(it) }
            }
        }
    }

    private fun basariGoster(modelAdlari: List<String>) {
        testStatus.foreground = UIUtil.getLabelSuccessForeground()
        if (modelAdlari.isEmpty()) {
            testStatus.text = AcpBundle.message("settings.test.okEmpty")
            return
        }
        testStatus.text = AcpBundle.message("settings.test.ok", modelAdlari.size)

        bulunanModeller = modelAdlari
        // Kullanıcının seçimi listede yoksa (veya hiç seçim yoksa) ilkine düş.
        if (secilenModel() !in modelAdlari) modelField.text = modelAdlari.first()
    }

    private fun hataGoster(hata: Throwable) {
        testStatus.foreground = UIUtil.getErrorForeground()
        // Kullanıcıya sade mesaj; teknik ayrıntı buraya konmaz.
        testStatus.text = (hata as? LlmError)?.userMessage ?: hata.message.orEmpty()
    }
}
