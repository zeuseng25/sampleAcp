package dev.acp.ui.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import dev.acp.service.ChatMessageUi
import dev.acp.service.ChatRole
import dev.acp.service.ChatUiState
import dev.acp.service.MessageId
import java.awt.Container

/**
 * Dar panelde yatay kaydırma çıkmaz (kullanıcı bulgusu, spec 010 T90).
 *
 * Uzman cevapları uzun dosya yolları ve çok adımlı araştırma satırı içeriyor;
 * tek satırlık bir bileşen tam metin genişliğini isterse bütün sohbet yatayda kayar.
 */
class YatayKaydirmaTest : BasePlatformTestCase() {

    private fun yerlesim(c: Container) {
        c.doLayout()
        for (alt in c.components) if (alt is Container) yerlesim(alt)
    }

    private fun kaydirma(k: Container): JBScrollPane? {
        for (c in k.components) {
            if (c is JBScrollPane) return c
            if (c is Container) kaydirma(c)?.let { return it }
        }
        return null
    }

    private fun olc(mesaj: ChatMessageUi): Pair<Int, Int> {
        val g = SwingChatView(project)
        try {
            g.render(ChatUiState(messages = listOf(ChatMessageUi(MessageId(1), ChatRole.USER, "soru"), mesaj)))
            val kok = g.component
            kok.setSize(300, 700)
            repeat(2) { yerlesim(kok) }
            val k = kaydirma(kok)!!
            // Kullanıcının gördüğü: yatay çubuk var mı, içerik görünen alandan geniş mi.
            k.doLayout()
            return (if (k.horizontalScrollBar.isVisible) Int.MAX_VALUE else k.viewport.view.width) to k.viewport.width
        } finally {
            g.dispose()
        }
    }

    /** Kontrol: ölçümün kendisi geçerli mi — kısa cevap hiç taşmamalı. */
    fun testKisaCevapTasmaz() {
        val (icerik, gorunen) = olc(ChatMessageUi(MessageId(2), ChatRole.ASSISTANT, "Merhaba"))
        assertTrue("bilinen-iyi taşıyor: içerik $icerik > görünen $gorunen", icerik <= gorunen)
    }

    fun testUzunAdimSatiriYatayKaydirmaYapmaz() {
        val adimlar = listOf(
            "kullanimlari_bul: {}", "uygulamalari_bul: {}", "tanima_git: {}", "dosya_oku: {}",
            "kullanimlari_bul: {}", "dosya_ara: {}", "tanima_git: {}", "dosya_oku: {}",
        )
        val (icerik, gorunen) = olc(ChatMessageUi(MessageId(2), ChatRole.ASSISTANT, "kısa cevap", adimlar = adimlar))
        assertTrue("yatay kaydırma: içerik $icerik > görünen $gorunen", icerik <= gorunen)
    }

    fun testUzunYolluUzmanCevabiYatayKaydirmaYapmaz() {
        val metin = """
            **Kim çağırıyor**
            - `src/main/kotlin/dev/acp/service/ChatSessionService.kt:283` içindeki `ChatSessionService.arastir` metodu bunu çağırıyor.
            - src/main/kotlin/dev/acp/anlama/PsiIliskiArayici.kt:39:PsiIliskiArayici.kullanimlar_cok_uzun_bir_tanimlayici_adi_ornegi
        """.trimIndent()
        val (icerik, gorunen) = olc(ChatMessageUi(MessageId(2), ChatRole.ASSISTANT, metin))
        assertTrue("yatay kaydırma: içerik $icerik > görünen $gorunen", icerik <= gorunen)
    }
}
