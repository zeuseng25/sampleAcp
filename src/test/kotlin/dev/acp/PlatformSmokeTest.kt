package dev.acp

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform test altyapısının gerçekten ayağa kalktığını kanıtlar.
 *
 * Bu test olmadan `testFramework(...)` satırının işe yarayıp yaramadığı
 * bilinmez: bağımlılık çözülür, kod derlenir, ama ilk gerçek platform testi
 * yazıldığında altyapı patlar.
 *
 * JUnit 5 değil JUnit 3 biçimindedir (metot adı `test` ile başlar) — çünkü
 * `BasePlatformTestCase` öyle çalışır ve vintage motoruyla koşar.
 */
class PlatformSmokeTest : BasePlatformTestCase() {

    fun testFixtureDosyaOlusturabiliyor() {
        val dosya = myFixture.configureByText("deneme.txt", "merhaba dünya")
        assertEquals("merhaba dünya", dosya.text)
    }

    fun testProjeVeEditorErisilebiliyor() {
        myFixture.configureByText("a.txt", "birinci satır\nikinci satır")
        assertNotNull(myFixture.editor)
        assertEquals(2, myFixture.editor.document.lineCount)
    }

    fun testSecimOkunabiliyor() {
        // Seçim tabanlı eylemler (spec 003) bu mekanizmayla test edilecek.
        myFixture.configureByText("a.txt", "abcdef")
        myFixture.editor.selectionModel.setSelection(1, 4)
        assertEquals("bcd", myFixture.editor.selectionModel.selectedText)
    }
}
