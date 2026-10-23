package dev.acp.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 003/T60–T61.
 *
 * En kritik iddia: [AcpIntentionAction.startInWriteAction] `false` olmalı.
 * `true` olsaydı ağ çağrısı yazma kilidi altında beklenirdi ve IDE yanıt boyunca
 * **tamamen donardı** — spec 003 H1: "Eylem çalışırken IDE donmaz."
 */
class IntentionTest : BasePlatformTestCase() {

    private val intention = AcpIntentionAction()

    fun testYazmaKilidiAltindaBaslamaz() {
        assertFalse(
            "startInWriteAction=true — ağ çağrısı yazma kilidi altında IDE'yi dondurur",
            intention.startInWriteAction(),
        )
    }

    /**
     * IntelliJ her intention için `intentionDescriptions/<SınıfAdı>/description.html`
     * bekliyor. Dosya yoksa Alt+Enter önizlemesi çalışma zamanında SEVERE ile
     * çöküyor:
     *
     * ```text
     * Intention Description Dir URL is null: AcpIntentionAction;
     * while looking for description.html
     * ```
     *
     * `availableIntentions` bu dosyayı aramadığı için diğer testler sorunu
     * göremedi; hatayı kullanıcı buldu.
     */
    fun testAciklamaDosyasiPakette() {
        val yol = "/intentionDescriptions/${AcpIntentionAction::class.java.simpleName}/description.html"
        val kaynak = AcpIntentionAction::class.java.getResource(yol)
        assertNotNull("intention açıklaması pakette yok: $yol", kaynak)
        assertTrue(
            "açıklama dosyası boş",
            kaynak!!.readText().contains("<html", ignoreCase = true),
        )
    }

    fun testSecimYokkenGorunmez() {
        myFixture.configureByText("Ornek.kt", "fun a() = 1")
        assertFalse(
            "seçim yokken Alt+Enter listesinde görünüyor",
            intention.isAvailable(project, myFixture.editor, myFixture.file),
        )
    }

    fun testSecimVarkenGorunur() {
        myFixture.configureByText("Ornek.kt", "fun a() = 1")
        myFixture.editor.selectionModel.setSelection(0, 8)
        assertTrue(
            "seçim varken Alt+Enter listesinde görünmüyor",
            intention.isAvailable(project, myFixture.editor, myFixture.file),
        )
    }

    fun testMetinVeAileAdiBundleDanGeliyor() {
        assertTrue("intention metni boş", intention.text.isNotBlank())
        assertTrue("aile adı boş", intention.familyName.isNotBlank())
        assertTrue("metin ACP'den söz etmiyor: ${intention.text}", intention.text.contains("ACP"))
    }

    fun testAltEnterListesindeTekGirisVar() {
        // Dört ayrı intention Alt+Enter listesini boğardı; tek giriş olmalı.
        myFixture.configureByText("Ornek.kt", "fun a() = 1")
        myFixture.editor.selectionModel.setSelection(0, 8)

        // Platform ayrıca kendi "Disable '<aile>'" girişini ekliyor; o bizim
        // değil ve sayılmamalı.
        val acpGirisleri = myFixture.availableIntentions
            .filter { it.text.contains("ACP") }
            .filterNot { it.text.startsWith("Disable") }

        assertEquals(
            "Alt+Enter'da ACP girişi sayısı beklenenden farklı: ${acpGirisleri.map { it.text }}",
            1,
            acpGirisleri.size,
        )
        assertEquals(intention.text, acpGirisleri.single().text)
    }
}
