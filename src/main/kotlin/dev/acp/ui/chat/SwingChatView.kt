package dev.acp.ui.chat

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.acp.AcpBundle
import dev.acp.llm.text.MarkdownBlok
import com.intellij.util.ui.HTMLEditorKitBuilder
import dev.acp.llm.text.MarkdownFenceSplitter
import dev.acp.llm.text.MarkdownToHtml
import javax.swing.JEditorPane
import dev.acp.service.ChatMessageUi
import dev.acp.service.ChatRole
import dev.acp.service.ChatUiState
import dev.acp.service.SohbetKipi
import dev.acp.service.MessageId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Sohbet panelinin Swing gerçeklemesi.
 *
 * Gövde iki kipte çizilir: akış sırasında düz metin, akış bitince markdown
 * bloklarına ayrılmış (kod blokları IDE'nin gerçek renklendirmesiyle).
 * Gerekçe [MesajBileseni] içinde.
 *
 * **Plandan sapma:** plan ayrı bir `appendDelta` sıcak yolu öngörüyordu. Yerine
 * [render] mesaj kimliğine göre fark alıyor. Tek yol daha az kod ve daha az
 * senkronizasyon hatası demek; `coalesceText` güncellemeleri zaten saniyede
 * ~20'ye indirdiği için performans yeterli.
 *
 * Renkler [JBColor] ve [UIUtil] üzerinden alınıyor — sabit renk yok, tema
 * değişince panel de değişir.
 */
internal class SwingChatView(private val project: Project) : ChatView {

    private val _actions = MutableSharedFlow<ChatViewAction>(extraBufferCapacity = 32)
    override val actions: Flow<ChatViewAction> = _actions.asSharedFlow()

    /**
     * Sohbet görüntüsü **genişliği izler**: içerik paneli asla görünen alandan
     * geniş olmaz, uzun satırlar kaydırılır.
     *
     * Düz bir JPanel, içindeki en geniş bileşenin istediği genişliği alıyordu;
     * uzun bir dosya yolu veya araştırma satırı bütün sohbeti yatayda kaydırıyordu
     * (kullanıcı bulgusu, spec 010 T90). Kod blokları kendi yatay kaydırmalarını taşır.
     */
    private val transcript = object : JPanel(VerticalLayout(JBUI.scale(6))), javax.swing.Scrollable {
        override fun getPreferredScrollableViewportSize(): java.awt.Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: java.awt.Rectangle, o: Int, d: Int) = JBUI.scale(16)
        override fun getScrollableBlockIncrement(r: java.awt.Rectangle, o: Int, d: Int) =
            if (o == javax.swing.SwingConstants.VERTICAL) r.height else r.width
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }.apply {
        border = JBUI.Borders.empty(10, 8)
        background = UIUtil.getTextFieldBackground()
        isOpaque = true
    }

    private val bosDurumEtiketi = JBLabel(AcpBundle.message("chat.empty")).apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(8, 4)
    }

    private val kaydirma = JBScrollPane(transcript).apply {
        border = JBUI.Borders.empty()
        viewport.background = UIUtil.getTextFieldBackground()
        verticalScrollBar.unitIncrement = JBUI.scale(16)
    }

    private val girdi = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        border = JBUI.Borders.empty(6, 8)
        emptyText.text = AcpBundle.message("chat.input.hint")
    }

    /**
     * Çok dosyalı düzenleme anahtarı (spec 008).
     *
     * **Kip burada da görünüyor**, yalnızca ayarlarda değil. Onayı atlayan bir
     * ayarın gizli kalmaması gerekiyor; benzer araçlar (Cline) da otomatik onay
     * ayarını işin yapıldığı yerde gösteriyor.
     */
    private val ajanAnahtari = JBRadioButton(AcpBundle.message("chat.agent.toggle")).apply {
        toolTipText = AcpBundle.message("chat.agent.toggle.hint")
        addActionListener { kipSecildi() }
    }

    /** Kodu derinlemesine araştırıp kıdemli bir uzman gibi anlatır; dosya değiştirmez (spec 010). */
    private val uzmanSecici = JBRadioButton(AcpBundle.message("chat.mode.expert")).apply {
        toolTipText = AcpBundle.message("chat.mode.expert.hint")
        addActionListener { kipSecildi() }
    }

    /** Sohbet varsayılan; seçenekler yan yana durunca hangisinde olunduğu gözden kaçmıyor (T93). */
    private val sohbetSecici = JBRadioButton(AcpBundle.message("chat.mode.chat"), true).apply {
        addActionListener { kipSecildi() }
    }.also { ButtonGroup().apply { add(it); add(uzmanSecici); add(ajanAnahtari) } }

    /** Ajanın durumu tek bakışta: çalışıyor / bitti / durduruldu / hata (T90 bulgusu). */
    private val ajanDurumEtiketi = JBLabel().apply {
        font = JBFont.label().asBold()
        isVisible = false
    }

    private val ajanKipEtiketi = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBFont.medium()
    }

    private val ajanDurdurDugmesi = JButton(AcpBundle.message("chat.agent.stop")).apply {
        isVisible = false
        addActionListener { _actions.tryEmit(ChatViewAction.AjanDurdur) }
    }

    /**
     * Ajanın son mesajı.
     *
     * `JBLabel` DEĞİL: modelin özeti bir cümleden uzun olabiliyor ve tek satırlık
     * etikette **kırpılıyordu** (bulgu 2026-09-05). Sarmalanan alan kullanılıyor.
     */
    private val ajanOzetEtiketi = sarmalananAlan().apply { isVisible = false }
    // Sohbetteki "Önizle"/"Uygula" ile AYNI metni kullanmıyorlar: aynı panelde
    // iki "Uygula" bağlantısı olması hangisinin ne yaptığını belirsizleştirirdi.
    // (Bunu spec 003'ten kalan yerleşim testi yakaladı.)
    private val ajanOnizleBaglantisi = ActionLink(AcpBundle.message("chat.agent.preview")) {
        _actions.tryEmit(ChatViewAction.AjanOnizle)
    }.apply { isVisible = false }
    private val ajanUygulaBaglantisi = ActionLink(AcpBundle.message("chat.agent.apply")) {
        _actions.tryEmit(ChatViewAction.AjanUygula)
    }.apply { isVisible = false }
    /** Onay bekleyen doğrulama komutu (spec 009 H2): tam metniyle, kendi satırında. */
    private val komutOnayEtiketi = sarmalananAlan().apply { isVisible = false }
    private val komutCalistirBaglantisi = ActionLink(AcpBundle.message("chat.agent.command.run")) {
        _actions.tryEmit(ChatViewAction.AjanKomutCalistir)
    }
    private val komutAtlaBaglantisi = ActionLink(AcpBundle.message("chat.agent.command.skip")) {
        _actions.tryEmit(ChatViewAction.AjanKomutAtla)
    }
    private val komutOnaySatiri = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
        isOpaque = false
        isVisible = false
        add(komutCalistirBaglantisi)
        add(komutAtlaBaglantisi)
    }

    private val ajanVazgecBaglantisi = ActionLink(AcpBundle.message("chat.agent.discard")) {
        _actions.tryEmit(ChatViewAction.AjanVazgec)
    }.apply { isVisible = false }

    /**
     * Satırlara sarılan metin alanı.
     *
     * `JBTextArea(lineWrap = true)` DEĞİL: dikey yerleşimde genişliği bilmeden
     * tercih ettiği yüksekliği **tek satır** hesaplıyor ve uzun metin kırpılıyor
     * (kullanıcı bulgusu 2026-09-05, testle doğrulandı).
     *
     * IDE'nin HTML motoru genişliğe göre doğru sarıyor; sohbet gövdesinde de
     * aynısını kullanıyoruz.
     */
    private fun sarmalananAlan() = JEditorPane().apply {
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty(2, 4)
        font = UIUtil.getLabelFont()
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    /** Düz metni HTML'e kaçırarak yazar; model çıktısı görünümü bozmasın. */
    private fun JEditorPane.duzMetin(metin: String) {
        val kacisli = metin
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>")
        val yeni = "<html><body>$kacisli</body></html>"
        if (text != yeni) { text = yeni; caretPosition = 0 }
    }

    private val gonderDugmesi = JButton(AcpBundle.message("chat.send")).apply {
        // IDE'nin birincil düğme görünümü.
        putClientProperty("JButton.buttonType", "default")
        addActionListener { gonder() }
    }

    private val iptalDugmesi = JButton(AcpBundle.message("chat.cancel")).apply {
        isVisible = false
        addActionListener { _actions.tryEmit(ChatViewAction.Iptal) }
    }

    private val hataEtiketi = JBLabel().apply { foreground = UIUtil.getErrorForeground() }

    private val yenidenDeneBaglantisi = ActionLink(AcpBundle.message("chat.retry")) {
        _actions.tryEmit(ChatViewAction.YenidenDene)
    }

    private val onizleBaglantisi = ActionLink(AcpBundle.message("chat.preview")) {
        _actions.tryEmit(ChatViewAction.Onizle)
    }

    private val uygulaBaglantisi = ActionLink(AcpBundle.message("chat.apply")) {
        _actions.tryEmit(ChatViewAction.Uygula)
    }

    /**
     * Uygulanabilir sonuç şeridi.
     *
     * Yalnızca kodu değiştiren eylemlerde ve model gerçekten kod bloğu
     * döndürdüğünde görünür. Kod yoksa hiç gösterilmez — açıklama metnini
     * koda yazma ihtimali olmamalı (spec 003 H2).
     */
    private val uygulaSeridi = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(4, 8),
        )
        // BorderLayout, FlowLayout değil: panel dar olduğunda FlowLayout
        // bağlantıları alt satıra sarıyor ve şeridin yüksekliği bunu
        // karşılamadığı için "Uygula" kırpılıyordu.
        //
        // Öncelik: dar alanda kaybolması gereken şey açıklama etiketi, düğmeler
        // değil. Bu yüzden bağlantılar DOĞU'da (önce yer alır), etiket MERKEZ'de.
        add(
            JBLabel(AcpBundle.message("chat.apply.ready")).apply {
                foreground = UIUtil.getContextHelpForeground()
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
                isOpaque = false
                add(onizleBaglantisi)
                add(uygulaBaglantisi)
            },
            BorderLayout.EAST,
        )
        isVisible = false
    }

    private val hataSeridi = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(6, 10),
        )
        add(hataEtiketi, BorderLayout.CENTER)
        add(yenidenDeneBaglantisi, BorderLayout.EAST)
        isVisible = false
    }

    private val girdiAlani = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8),
        )
        add(
            JBScrollPane(girdi).apply { border = JBUI.Borders.customLine(JBColor.border(), 1) },
            BorderLayout.CENTER,
        )
        add(
            JPanel(VerticalLayout(JBUI.scale(4))).apply {
                isOpaque = false
                add(gonderDugmesi)
                add(iptalDugmesi)
                add(ajanDurdurDugmesi)
            },
            BorderLayout.EAST,
        )
        // Seçiciler BATI'da, etiket ORTA'da: dar panelde kısalan etiket olur (AGENTS.md, yerleşim).
        add(
            JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(4)
                add(
                    JPanel().apply {
                        isOpaque = false
                        layout = BoxLayout(this, BoxLayout.X_AXIS)
                        add(sohbetSecici)
                        add(uzmanSecici)
                        add(ajanAnahtari)
                    },
                    BorderLayout.WEST,
                )
                add(ajanKipEtiketi, BorderLayout.CENTER)
            },
            BorderLayout.NORTH,
        )
    }

    /**
     * Ajan bölümü (spec 008/009): **kendi kaydırma alanında, yüksekliği sınırlı.**
     *
     * Önceden doğrudan alt paneldeydi; uzun komut çıktısı veya modelin uzun son
     * mesajı sohbet alanını ekrandan itiyordu ve kaydırılamıyordu (T90 bulgusu,
     * 2026-10-23). Genişliği izler (yatay kaydırma yok, uzun satır kayar).
     */
    private val ajanBolumu = object : JPanel(VerticalLayout(JBUI.scale(2))), javax.swing.Scrollable {
        override fun getPreferredScrollableViewportSize(): java.awt.Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: java.awt.Rectangle, o: Int, d: Int) = JBUI.scale(16)
        override fun getScrollableBlockIncrement(r: java.awt.Rectangle, o: Int, d: Int) =
            if (o == javax.swing.SwingConstants.VERTICAL) r.height else r.width
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }.apply {
                        isOpaque = false
                        add(ajanDurumEtiketi)

                        // Onay: metin ve bağlantılar AYRI satırlarda (dar panel dersi).
                        add(komutOnayEtiketi)
                        add(komutOnaySatiri)

                        /*
                         * Özet ve bağlantılar AYRI satırlarda.
                         *
                         * Aynı satıra konduklarında dar panelde FlowLayout satır
                         * kaydırıyor ve kaydırılan bağlantılar kabın dışında
                         * kalıp kırpılıyordu (kullanıcı bulgusu 2026-09-05).
                         * Spec 003'te aynı hata sohbetin kendi şeridinde
                         * yaşanmıştı; ders o zaman kodda yazılmıştı ama buraya
                         * uygulanmamıştı.
                         *
                         * Etiketler de kısa tutuluyor: üç bağlantı dar panele
                         * ancak böyle sığıyor.
                         */
                        add(ajanOzetEtiketi)
                        add(
                            JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
                                isOpaque = false
                                add(ajanOnizleBaglantisi)
                                add(ajanUygulaBaglantisi)
                                add(ajanVazgecBaglantisi)
                            },
                        )
    }

    /** İçerik kadar uzar ama panelin yaklaşık üçte birini geçmez; fazlası kaydırılır. */
    private val ajanKaydirma = object : JBScrollPane(ajanBolumu) {
        override fun getPreferredSize(): java.awt.Dimension {
            val p = super.getPreferredSize()
            val sinir = maxOf(JBUI.scale(120), (kokYuksekligi() * 0.35).toInt())
            return java.awt.Dimension(p.width, minOf(p.height, sinir))
        }
    }.apply {
        border = JBUI.Borders.empty()
        isOpaque = false
        viewport.isOpaque = false
        horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    /** En üstteki kabın yüksekliği; sınır panel büyüdükçe büyür. */
    private fun JComponent.kokYuksekligi(): Int {
        var p: java.awt.Container = this
        while (p.parent != null) p = p.parent
        return p.height
    }

    private val kok: JPanel = JPanel(BorderLayout()).apply {
        add(kaydirma, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(
                    JPanel(VerticalLayout(0)).apply {
                        isOpaque = false
                        add(uygulaSeridi)
                        add(hataSeridi)
                    },
                    BorderLayout.NORTH,
                )
                add(ajanKaydirma, BorderLayout.NORTH)
                add(girdiAlani, BorderLayout.CENTER)
            },
            BorderLayout.SOUTH,
        )
    }


    private val bilesenler = LinkedHashMap<MessageId, MesajBileseni>()

    /** Tema değişiminde yeniden çizebilmek için son durum saklanıyor. */
    private var sonDurum = ChatUiState()

    init {
        klavyeyiKur()
        transcript.add(bosDurumEtiketi)
        temayiDinle()
    }

    override val component: JComponent get() = kok

    override fun render(state: ChatUiState) {
        sonDurum = state
        // T60: yalnızca kullanıcı zaten dipteyse kaydır. Yukarı kaydırıp eski
        // mesajı okurken yeni metnin ekranı aşağı zorlaması paneli kullanılamaz yapar.
        val dipteydi = dipteMi()

        val mesajlar = ajanIleBirlestir(state)
        bosDurumEtiketi.isVisible = mesajlar.isEmpty()
        ajaniCiz(state.ajan)

        for (mesaj in mesajlar) {
            val mevcut = bilesenler[mesaj.id]
            if (mevcut == null) {
                val yeni = MesajBileseni(project, mesaj.role)
                bilesenler[mesaj.id] = yeni
                transcript.add(yeni)
                yeni.guncelle(mesaj)
            } else {
                mevcut.guncelle(mesaj)
            }
        }

        // Durumdan düşen mesajları kaldır ("yeniden dene" eski yanıtı siler).
        val gecerli = mesajlar.mapTo(HashSet()) { it.id }
        bilesenler.keys.filterNot { it in gecerli }.forEach { id ->
            bilesenler.remove(id)?.let {
                transcript.remove(it)
                it.dispose() // içindeki editörler serbest bırakılmalı
            }
        }

        siralamayiUygula(mesajlar)

        iptalDugmesi.isVisible = state.streaming
        gonderDugmesi.isEnabled = !state.streaming

        uygulaSeridi.isVisible = state.uygulanabilir != null

        val hata = state.error
        hataSeridi.isVisible = hata != null
        hataEtiketi.text = hata?.mesaj.orEmpty()
        yenidenDeneBaglantisi.isVisible = hata?.yenidenDenenebilir == true

        editorTavaniniUygula(state)

        transcript.revalidate()
        transcript.repaint()

        if (dipteydi) SwingUtilities.invokeLater { dibeKaydir() }
    }

    override fun dispose() {
        bilesenler.values.forEach { it.dispose() }
        bilesenler.clear()
    }

    /**
     * T44 — uzun sohbette editör tavanı.
     *
     * Her kod bloğu canlı bir IDE editörü demek; 40 bloklu bir konuşmada 40 canlı
     * editör olur. Eski mesajlar sade metne düşürülüyor: okunabilirlik çok az
     * kaybediyor, bellek çok kazanıyor.
     */
    private fun editorTavaniniUygula(state: ChatUiState) {
        if (state.messages.size <= EDITORLU_MESAJ_TAVANI) return
        state.messages
            .dropLast(EDITORLU_MESAJ_TAVANI)
            .forEach { bilesenler[it.id]?.sadelestir() }
    }

    // ---- tema ---------------------------------------------------------------

    /**
     * T61 — tema değişince panel de değişmeli (spec 002 H2).
     *
     * Bileşenler baştan kuruluyor: renkler ve editör renklendirmesi kuruluş
     * anında çözüldüğü için yalnızca `repaint` yetmez.
     */
    private fun temayiDinle() {
        val baglanti = ApplicationManager.getApplication().messageBus.connect(this)
        baglanti.subscribe(LafManagerListener.TOPIC, LafManagerListener { temayiTazele() })
        baglanti.subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { temayiTazele() },
        )
    }

    private fun temayiTazele() {
        transcript.background = UIUtil.getTextFieldBackground()
        kaydirma.viewport.background = UIUtil.getTextFieldBackground()

        bilesenler.values.forEach { it.dispose() }
        bilesenler.clear()
        transcript.removeAll()
        transcript.add(bosDurumEtiketi)

        render(sonDurum)
    }

    // ---- klavye -----------------------------------------------------------

    private fun klavyeyiKur() {
        // Enter gönderir, Shift+Enter satır atlar — sohbet arayüzlerinin beklenen
        // davranışı. JTextArea'nın varsayılanı Enter'ı satır atlamaya bağlar.
        girdi.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), GONDER_EYLEMI)
        girdi.actionMap.put(
            GONDER_EYLEMI,
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = gonder()
            },
        )
        girdi.inputMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
            "insert-break",
        )
    }

    // ---- kaydırma ---------------------------------------------------------

    private fun dipteMi(): Boolean {
        val bar = kaydirma.verticalScrollBar
        // Birkaç piksel tolerans: kullanıcı tam dipte olmasa da "dipte" sayılsın.
        return bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(24)
    }

    private fun dibeKaydir() {
        val bar = kaydirma.verticalScrollBar
        bar.value = bar.maximum
    }

    private fun gonder() {
        val metin = girdi.text
        if (metin.isBlank()) return
        girdi.text = ""
        // Anahtar açıkken yazılan şey ajana gider, sohbete değil (spec 008).
        if (ajanAnahtari.isSelected) {
            _actions.tryEmit(ChatViewAction.AjanBaslat(metin))
        } else {
            val kip = if (uzmanSecici.isSelected) SohbetKipi.UZMAN else SohbetKipi.SOHBET
            _actions.tryEmit(ChatViewAction.Gonder(metin, kip))
        }
    }

    /** Anahtar durumuna göre kip etiketini tazeler. */
    private fun ajanKipiniCiz() {
        ajanKipEtiketi.isVisible = ajanAnahtari.isSelected
        girdi.emptyText.text = AcpBundle.message(
            when {
                ajanAnahtari.isSelected -> "chat.input.hint.agent"
                uzmanSecici.isSelected -> "chat.input.hint.expert"
                else -> "chat.input.hint"
            },
        )
        girdi.repaint()
        ajanKipEtiketi.text = if (sonDurum.ajan.dogrudanUygula) {
            AcpBundle.message("chat.agent.mode.direct")
        } else {
            AcpBundle.message("chat.agent.mode.preview")
        }
    }

    /**
     * Ajan turu sohbet akışına iki mesaj olarak girer: görev ve cevap (T90 bulgusu).
     *
     * Yalnızca **çizim**: sohbet servisinin geçmişine yazılmaz, modele giden sohbet
     * geçmişi değişmez (008 T73 yalıtımı). Tur başladığı konuma yerleşir.
     */
    private fun ajanIleBirlestir(state: ChatUiState): List<ChatMessageUi> {
        val a = state.ajan
        val gorev = a.gorev?.takeIf { it.isNotBlank() } ?: return state.messages
        val sira = a.sohbetSirasi.coerceIn(0, state.messages.size)
        val cevap = listOfNotNull(
            a.hata ?: a.mesaj,
            // Komut çıktısı kod bloğu olarak: eş aralıklı, kendi yatay kaydırmasıyla.
            a.komutBilgisi?.takeIf { it.isNotBlank() }?.let { "```\n$it\n```" },
        ).joinToString("\n\n")
        return state.messages.take(sira) +
            ChatMessageUi(AJAN_SORUSU, ChatRole.USER, gorev) +
            ChatMessageUi(AJAN_CEVABI, ChatRole.ASSISTANT, cevap, adimlar = a.adimlar, streaming = a.calisiyor) +
            state.messages.drop(sira)
    }

    /** Mesaj bileşenlerini listenin sırasına dizer (ajan turu araya girebilir). */
    private fun siralamayiUygula(mesajlar: List<ChatMessageUi>) {
        val istenen = mesajlar.mapNotNull { bilesenler[it.id] }
        val mevcut = transcript.components.filterIsInstance<MesajBileseni>()
        if (mevcut == istenen) return
        istenen.forEach { transcript.remove(it) }
        istenen.forEach { transcript.add(it) }
    }

    private fun seciliKip(): PanelKipi = when {
        ajanAnahtari.isSelected -> PanelKipi.COK_DOSYALI
        uzmanSecici.isSelected -> PanelKipi.UZMAN
        else -> PanelKipi.SOHBET
    }

    /** Kullanıcı seçti: çiz ve kaydedilsin diye bildir (T53). */
    private fun kipSecildi() {
        ajanKipiniCiz()
        _actions.tryEmit(ChatViewAction.KipDegisti(seciliKip()))
    }

    override fun kipiAyarla(kip: PanelKipi) {
        // setSelected ActionEvent üretmez: hatırlanan kip geri yazılmaz.
        when (kip) {
            PanelKipi.SOHBET -> sohbetSecici
            PanelKipi.UZMAN -> uzmanSecici
            PanelKipi.COK_DOSYALI -> ajanAnahtari
        }.isSelected = true
        ajanKipiniCiz()
    }

    /** Ajan bölümünü duruma göre çizer. */
    private fun ajaniCiz(a: dev.acp.service.AjanUi) {
        ajanKipiniCiz()

        ajanDurumEtiketi.isVisible = !a.gorev.isNullOrBlank() || a.acik
        ajanDurumEtiketi.text = when {
            a.calisiyor -> AcpBundle.message("chat.agent.status.running", a.adimlar.size)
            a.hata != null -> AcpBundle.message("chat.agent.status.error")
            a.mesaj == "Durduruldu." -> AcpBundle.message("chat.agent.status.stopped")
            a.ozet?.dosyalar?.isNotEmpty() == true -> AcpBundle.message("chat.agent.status.ready", a.ozet.dosyalar.size)
            else -> AcpBundle.message("chat.agent.status.noChange")
        }
        // Gösterecek bir şey olduğu sürece görünür (yalnızca göreve bağlamak 008'in
        // özet şeridini gizliyordu — AjanSeridiTest yakaladı).
        ajanKaydirma.isVisible = a.acik || a.calisiyor || !a.gorev.isNullOrBlank() ||
            a.bekleyenKomut != null || a.mesaj != null || a.hata != null || !a.komutBilgisi.isNullOrBlank()




        val bekleyen = a.bekleyenKomut
        komutOnayEtiketi.isVisible = bekleyen != null
        komutOnaySatiri.isVisible = bekleyen != null
        if (bekleyen != null) komutOnayEtiketi.duzMetin(AcpBundle.message("chat.agent.command.ask", bekleyen))


        ajanDurdurDugmesi.isVisible = a.calisiyor
        gonderDugmesi.isEnabled = gonderDugmesi.isEnabled && !a.calisiyor

        val ozet = a.ozet
        val gosterilecek = ozet != null && ozet.dosyalar.isNotEmpty()
        // Şeritte yalnızca kısa özet; uzun mesaj ve hata sohbetteki cevapta (T90 bulgusu).
        ajanOzetEtiketi.isVisible = gosterilecek
        // Önizleme bağlantısı yalnızca uygulanacak bir şey varken anlamlı.
        ajanOnizleBaglantisi.isVisible = gosterilecek
        ajanUygulaBaglantisi.isVisible = gosterilecek
        ajanVazgecBaglantisi.isVisible = gosterilecek

        val ozetMetni = when {
            a.hata != null -> a.hata
            gosterilecek -> AcpBundle.message(
                "chat.agent.summary",
                ozet!!.dosyalar.size, ozet.toplamDegisiklik,
            ) + atlananEki(ozet.atlananlar)
            else -> ""
        }
        ajanOzetEtiketi.duzMetin(ozetMetni)
        ajanOzetEtiketi.foreground =
            if (a.hata != null) UIUtil.getErrorForeground() else UIUtil.getLabelForeground()

        /*
         * Atlananların AYRINTISI ipucunda.
         *
         * Şeritte yalnızca sayı gösteriliyor — dar panele üç kod parçası
         * sığmaz. Ama yalnızca sayı da yetersiz: "3 düzenleme uygulanamadı"
         * hangisinin olduğunu söylemiyor ve kullanıcı işi eksik uygulanmış
         * sanmadan devam edemez.
         */
        ajanOzetEtiketi.toolTipText = ozet?.atlananlar
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("<br>", prefix = "<html>", postfix = "</html>")
    }

    /**
     * Atlanan düzenlemeler **gösterilir** (spec 008 T51).
     *
     * Sessizce yutulursa kullanıcı işin eksik uygulandığını fark etmez —
     * çok dosyalı bir değişiklikte bu pahalı bir yanılgı.
     */
    private fun atlananEki(atlananlar: List<String>): String =
        if (atlananlar.isEmpty()) "" else " " + AcpBundle.message("chat.agent.skipped", atlananlar.size)

    /**
     * Tek bir mesajın kutusu: rol etiketi + solda renk şeridi + gövde.
     *
     * Gövde iki kipte çizilir:
     * - **Akış sırasında düz metin.** Her token'da markdown ayrıştırıp editör
     *   yaratmak paneli kilitlerdi.
     * - **Akış bitince bloklara ayrılmış.** Kod blokları IDE'nin gerçek
     *   renklendirmesiyle gelir.
     */
    private class MesajBileseni(
        private val project: Project,
        role: ChatRole,
    ) : JPanel(BorderLayout(0, JBUI.scale(3))), Disposable {

        private val rozetKabi = JPanel(
            java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(4), 0)
        ).apply {
            isOpaque = false
            isVisible = false
        }
        private var sonRozetler: List<dev.acp.service.ContextRozeti> = emptyList()
        private val dusunmeBolumu = ReasoningSection()

        /** Araştırma adımları (spec 010 H3): tek satır özet, ayrıntı ipucunda. */
        private val adimEtiketi = JBLabel().apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.medium()
            isVisible = false
        }
        private var sonAdimlar: List<String> = emptyList()
        private val govdeKabi = JPanel(VerticalLayout(JBUI.scale(2))).apply { isOpaque = false }
        private val duzMetin = metinAlani()
        private val kodBloklari = mutableListOf<CodeBlockComponent>()

        private var sonCizilenMetin: String? = null
        private var bloklarCizildi = false
        private var sadeKip = false

        init {
            val kullanici = role == ChatRole.USER
            val serit = if (kullanici) {
                JBColor.namedColor("Component.focusColor", JBColor(0x3574F0, 0x3574F0))
            } else {
                JBColor.namedColor("Component.borderColor", JBColor.GRAY)
            }

            isOpaque = kullanici
            if (kullanici) background = UIUtil.getPanelBackground()

            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(serit, 0, JBUI.scale(3), 0, 0),
                JBUI.Borders.empty(6, 10, 8, 6),
            )

            add(
                JBLabel(
                    AcpBundle.message(
                        if (kullanici) "chat.role.user" else "chat.role.assistant"
                    )
                ).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = JBFont.medium()
                },
                BorderLayout.NORTH,
            )
            govdeKabi.add(duzMetin)
            add(
                JPanel(VerticalLayout(JBUI.scale(2))).apply {
                    isOpaque = false
                    add(rozetKabi)
                    add(adimEtiketi)
                    add(dusunmeBolumu)
                    add(govdeKabi)
                },
                BorderLayout.CENTER,
            )
        }

        fun guncelle(mesaj: ChatMessageUi) {
            rozetleriGuncelle(mesaj.context)
            adimlariGuncelle(mesaj.adimlar)
            dusunmeBolumu.guncelle(mesaj.reasoning)
            if (mesaj.streaming) {
                if (bloklarCizildi) duzMetneDon()
                if (duzMetin.text != mesaj.text) duzMetin.text = mesaj.text
                sonCizilenMetin = null
                return
            }
            if (sonCizilenMetin == mesaj.text) return
            bloklariCiz(mesaj.text)
            sonCizilenMetin = mesaj.text
        }

        private fun adimlariGuncelle(adimlar: List<String>) {
            if (adimlar == sonAdimlar) return
            sonAdimlar = adimlar
            adimEtiketi.isVisible = adimlar.isNotEmpty()
            if (adimlar.isEmpty()) return
            // Özette yalnızca araç adları; argümanlar dar panele sığmaz.
            val adlar = adimlar.map { it.substringBefore(':').trim() }
            adimEtiketi.text = AcpBundle.message("chat.research.steps", adimlar.size, adlar.joinToString(" · "))
            adimEtiketi.toolTipText = adimlar.joinToString("<br>", "<html>", "</html>") {
                com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(it)
            }
        }

        /** Soruya hangi bağlamın eklendiğini gösterir (spec 002 H3). */
        private fun rozetleriGuncelle(rozetler: List<dev.acp.service.ContextRozeti>) {
            if (rozetler == sonRozetler) return
            sonRozetler = rozetler
            rozetKabi.removeAll()
            rozetKabi.isVisible = rozetler.isNotEmpty()
            for (rozet in rozetler) {
                rozetKabi.add(
                    JBLabel(rozet.ad).apply {
                        foreground = UIUtil.getContextHelpForeground()
                        font = JBFont.medium()
                        border = JBUI.Borders.compound(
                            JBUI.Borders.customLine(JBColor.border(), 1),
                            JBUI.Borders.empty(1, 5),
                        )
                        toolTipText = rozet.detay
                    }
                )
            }
            rozetKabi.revalidate()
        }

        private fun duzMetneDon() {
            govdeyiTemizle()
            govdeKabi.add(duzMetin)
            bloklarCizildi = false
        }

        /** Editörleri bırakıp kod bloklarını sade metne düşürür (T44). */
        fun sadelestir() {
            if (sadeKip) return
            sadeKip = true
            sonCizilenMetin?.let { bloklariCiz(it) }
        }

        private fun bloklariCiz(metin: String) {
            govdeyiTemizle()
            for (blok in MarkdownFenceSplitter.split(metin)) {
                when (blok) {
                    is MarkdownBlok.Metin ->
                        // Sade kipte (akış) ham metin; bitince biçimlendirilmiş.
                        govdeKabi.add(
                            if (sadeKip) metinAlani().apply { text = blok.text }
                            else bicimliAlan(blok.text)
                        )
                    is MarkdownBlok.Kod -> if (sadeKip) {
                        govdeKabi.add(
                            metinAlani().apply {
                                text = blok.kod
                                font = com.intellij.util.ui.JBFont.create(
                                    java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, font.size)
                                )
                            }
                        )
                    } else {
                        val bilesen = CodeBlockComponent(project, blok.dil, blok.kod)
                        kodBloklari += bilesen
                        govdeKabi.add(bilesen)
                    }
                }
            }
            bloklarCizildi = true
            govdeKabi.revalidate()
            govdeKabi.repaint()
        }

        private fun govdeyiTemizle() {
            kodBloklari.forEach { it.dispose() }
            kodBloklari.clear()
            govdeKabi.removeAll()
        }

        override fun dispose() = govdeyiTemizle()

        private companion object {
            /**
             * Biçimlendirilmiş metin bileşeni (spec 002, 2026-09-04).
             *
             * IDE'nin kendi HTML motoru kullanılıyor — tema rengini ve yazı
             * tipini kendisi çözüyor, biz sabit renk yazmıyoruz. IDE bunu kendi
             * belge açıklamalarında da kullanır.
             *
             * `sadeKip`'te kullanılmaz: akış sırasında ham metin gösterilir,
             * çünkü yarım gelmiş bir `**` işaretini render etmek anlamsız.
             */
            fun bicimliAlan(markdown: String) = JEditorPane().apply {
                editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
                isEditable = false
                isOpaque = false
                border = JBUI.Borders.empty()
                font = UIUtil.getLabelFont()
                // Yazı tipini HTML tarafına da bildir; aksi halde Swing kendi
                // varsayılanını kullanır ve panel yazı tipinden kopar.
                putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                text = "<html><body>${MarkdownToHtml.cevir(markdown)}</body></html>"
                caretPosition = 0
            }

            fun metinAlani() = JBTextArea().apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                border = JBUI.Borders.empty()
                font = UIUtil.getLabelFont()
            }
        }
    }

    private companion object {
        const val GONDER_EYLEMI = "acp.gonder"

        /** Ajan turunun sohbetteki iki mesajı; sohbet kimlikleriyle çakışmaz (sayaç 1'den artar). */
        val AJAN_SORUSU = MessageId(Long.MIN_VALUE)
        val AJAN_CEVABI = MessageId(Long.MIN_VALUE + 1)

        /** Bu sayıdan eski mesajlarda kod blokları sade metne düşer. */
        const val EDITORLU_MESAJ_TAVANI = 20
    }
}
