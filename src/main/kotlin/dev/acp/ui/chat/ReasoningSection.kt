package dev.acp.ui.chat

import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.acp.AcpBundle
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Modelin düşünme bölümü.
 *
 * Cevaptan ayrı, katlanmış ve **varsayılan olarak kapalı** (spec 002 H2).
 * Gerekçe: düşünme metni çoğu zaman cevabın kendisinden uzundur; açık gelseydi
 * asıl cevabı bulmak için her seferinde kaydırmak gerekirdi.
 *
 * Kullanıcının açtığı bölüm, akış devam ederken **açık kalır** — okurken
 * altından kapanması sinir bozucu olurdu.
 */
internal class ReasoningSection : JPanel(BorderLayout(0, JBUI.scale(2))) {

    private var acik = false

    private val icerik = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        foreground = UIUtil.getContextHelpForeground()
        font = JBFont.medium()
        border = JBUI.Borders.emptyLeft(12)
        isVisible = false
    }

    private val baslik = ActionLink("") { degistir() }

    init {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(4)
        add(baslik, BorderLayout.NORTH)
        add(icerik, BorderLayout.CENTER)
        isVisible = false
        basligiGuncelle()
    }

    fun guncelle(metin: String) {
        isVisible = metin.isNotEmpty()
        if (!isVisible) return
        if (icerik.text != metin) icerik.text = metin
        basligiGuncelle()
    }

    private fun degistir() {
        acik = !acik
        icerik.isVisible = acik
        basligiGuncelle()
        revalidate()
        repaint()
    }

    private fun basligiGuncelle() {
        val ok = if (acik) "▾" else "▸"
        baslik.text = "$ok ${AcpBundle.message("chat.reasoning")}"
    }
}
