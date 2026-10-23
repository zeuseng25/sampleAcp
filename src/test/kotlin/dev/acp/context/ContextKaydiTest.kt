package dev.acp.context

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.llm.prompt.ContextKind

/**
 * 002/T70 — uzantı noktasının **gerçekten kayıtlı** olduğunu doğrular.
 *
 * Bu testin varlık sebebi yaşanmış bir hata: uzantı noktasının tam adı
 * `<eklenti kimliği>.<name>` olduğu hâlde kodda kimliksiz yazılmıştı. Sağlayıcı
 * hiç bulunamadı, `CompositeContextProvider` istisna fırlattı, `ChatSessionService`
 * içindeki "bağlam bir kolaylıktır" yakalayıcısı istisnayı yuttu ve sorular
 * sessizce bağlamsız gitti.
 *
 * Yani hem bir isim hatası hem de onu gizleyen bir savunma vardı. Bu test ikisini
 * de yakalar: uzantı noktası çözülemezse [ContextProvider.EP] erişimi patlar.
 */
class ContextKaydiTest : BasePlatformTestCase() {

    fun testUzantiNoktasiCozulebiliyor() {
        // Ad yanlışsa burası istisna fırlatır.
        val saglayicilar = ContextProvider.EP.extensionList
        assertTrue(
            "Hiç bağlam sağlayıcısı kayıtlı değil — plugin.xml ile EP adı uyuşmuyor olabilir",
            saglayicilar.isNotEmpty(),
        )
    }

    fun testSelectionContextProviderKayitli() {
        assertTrue(
            "SelectionContextProvider plugin.xml'de kayıtlı değil",
            ContextProvider.EP.extensionList.any { it is SelectionContextProvider },
        )
    }

    fun testButceUygulamasiSaglayicidanBagimsizCalisir() {
        // Sağlayıcılar IDE durumuna bağlı; bütçe mantığı değil.
        val ogeler = listOf(
            dev.acp.llm.prompt.ContextItem(
                kind = ContextKind.SELECTION,
                displayName = "Foo.kt (1-2)",
                text = "seçili",
            ),
        )
        assertEquals(1, CompositeContextProvider.butceyeSigdir(ogeler, 1_000).size)
    }
}
