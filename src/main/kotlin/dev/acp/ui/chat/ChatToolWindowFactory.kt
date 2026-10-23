package dev.acp.ui.chat

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.acp.AcpBranding
import dev.acp.service.AjanOzet
import dev.acp.service.AjanService
import dev.acp.service.AjanUi
import dev.acp.service.ChatSessionService
import dev.acp.settings.AjanKipi
import kotlinx.coroutines.flow.combine

/**
 * Sohbet panelini IDE'ye kaydeder.
 *
 * Burada yalnızca bağlantı kurulur: görünüm durumu servisten alır, kullanıcı
 * niyetlerini servise iletir. Konuşmanın kendisi **serviste** yaşar, bu yüzden
 * panel kapatılıp açıldığında yerinde durur (spec 002 H1).
 */
internal class ChatToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // plugin.xml'deki id ("ACP") sabit tanımlayıcıdır; görünen başlık
        // build zamanındaki addan gelir (spec 004).
        toolWindow.stripeTitle = AcpBranding.displayName

        val gorunum = SwingChatView(project)
        val servis = ChatSessionService.getInstance(project)
        val ajan = AjanService.getInstance(project)

        // Son kip hatırlanır (spec 010 T53); görünüm ayara dokunmaz, burada okunur/yazılır.
        val projeAyarlari = dev.acp.settings.AcpProjeAyarlari.getInstance(project)
        gorunum.kipiAyarla(PanelKipi.coz(projeAyarlari.state.sonKip))

        val content = ContentFactory.getInstance().createContent(gorunum.component, null, false)
        content.setDisposer(gorunum)
        toolWindow.contentManager.addContent(content)

        /*
         * Durum → görünüm. İki kaynak birleştiriliyor.
         *
         * Sohbet ve ajan durumları AYRI tutuluyor: ajan çalışırken sohbet
         * mesajları değişmiyor ve ikisi birbirini bozmuyor. Görünüm ise tek bir
         * değişmez durum çiziyor (ChatView disiplini), o yüzden birleştirme
         * burada yapılıyor.
         */
        servis.collectOnEdt(
            gorunum,
            combine(servis.state, ajan.durum) { sohbet, a ->
                sohbet.copy(
                    ajan = AjanUi(
                        acik = a.calisiyor || a.sonuc != null,
                        bekleyenKomut = a.bekleyenKomut,
                        komutBilgisi = a.komutBilgisi,
                        sohbetSirasi = a.sohbetSirasi,
                        calisiyor = a.calisiyor,
                        gorev = a.gorev,
                        adimlar = a.adimlar,
                        ozet = a.sonuc?.let { sonuc ->
                            AjanOzet(
                                dosyalar = sonuc.dosyalar.map { it.yol },
                                toplamDegisiklik = sonuc.toplamDegisiklik,
                                atlananlar = sonuc.atlananlar,
                            )
                        },
                        mesaj = a.mesaj,
                        hata = a.hata,
                        dogrudanUygula = ajan.kip() == AjanKipi.DOGRUDAN,
                    ),
                )
            },
        ) { durum -> gorunum.render(durum) }

        // Görünüm → servis. Tek yönlü akış: görünüm niyet yayınlar, servis karar verir.
        servis.collectOnEdt(gorunum, gorunum.actions) { eylem ->
            when (eylem) {
                is ChatViewAction.Gonder -> servis.send(eylem.metin, eylem.kip)
                is ChatViewAction.KipDegisti -> projeAyarlari.state.sonKip = eylem.kip.name
                ChatViewAction.Iptal -> servis.cancel()
                ChatViewAction.YenidenDene -> servis.retry()
                ChatViewAction.Onizle -> servis.onizle()
                ChatViewAction.Uygula -> servis.uygula()

                // ---- çok dosyalı düzenleme (spec 008) ----
                is ChatViewAction.AjanBaslat -> ajan.baslat(eylem.gorev)
                ChatViewAction.AjanDurdur -> ajan.durdur()
                ChatViewAction.AjanOnizle -> ajan.onizle()
                ChatViewAction.AjanUygula -> ajan.uygula()
                ChatViewAction.AjanVazgec -> ajan.temizle()
                ChatViewAction.AjanKomutCalistir -> ajan.komutuCalistir()
                ChatViewAction.AjanKomutAtla -> ajan.komutuAtla()
            }
        }
    }
}
