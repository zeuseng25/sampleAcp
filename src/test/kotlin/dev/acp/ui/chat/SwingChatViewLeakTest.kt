package dev.acp.ui.chat

import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.acp.service.ChatMessageUi
import dev.acp.service.ChatRole
import dev.acp.service.ChatUiState
import dev.acp.service.MessageId

/**
 * 002/T43 — H2: "Uzun bir sohbet sonrası panel kapatıldığında bellekte artık kalmaz."
 *
 * Kod blokları IDE'nin gerçek editör bileşenini kullanıyor; her biri için
 * `EditorFactory.releaseEditor` çağrılmazsa sızarlar. Bu, IntelliJ eklentilerinde
 * klasik bir hatadır ve elle fark edilmesi çok zordur.
 */
class SwingChatViewLeakTest : BasePlatformTestCase() {

    private fun editorSayisi() = EditorFactory.getInstance().allEditors.size

    private fun kodluMesaj(id: Long, metin: String) = ChatUiState(
        messages = listOf(
            ChatMessageUi(MessageId(id), ChatRole.ASSISTANT, metin, streaming = false),
        ),
    )

    fun testKodBloklariDisposeEdilinceSerbestBirakilir() {
        val once = editorSayisi()
        val gorunum = SwingChatView(project)

        gorunum.render(
            kodluMesaj(
                1,
                "İşte:\n```kotlin\nfun a() {}\n```\nve bir tane daha:\n```java\nint b;\n```",
            ),
        )

        // Testin boş olmadığının kanıtı: editörler gerçekten yaratıldı.
        assertTrue(
            "kod bloğu için editör yaratılmamış — test bir şey doğrulamıyor olurdu",
            editorSayisi() > once,
        )

        gorunum.dispose()
        assertEquals("editör sızdı", once, editorSayisi())
    }

    fun testYenidenCizimEskiEditorleriBirakir() {
        val once = editorSayisi()
        val gorunum = SwingChatView(project)
        try {
            // Aynı mesaj birkaç kez farklı içerikle çizilirse eskiler birikmemeli.
            repeat(5) { tur ->
                gorunum.render(kodluMesaj(1, "```kotlin\nval x = $tur\n```"))
            }
            assertEquals(
                "her yeniden çizimde yeni editör birikmiş",
                once + 1,
                editorSayisi(),
            )
        } finally {
            gorunum.dispose()
        }
        assertEquals(once, editorSayisi())
    }

    fun testAkisSirasindaEditorYaratilmaz() {
        // Akış sırasında düz metin gösteriliyor: her token'da editör yaratmak
        // paneli kilitlerdi.
        val once = editorSayisi()
        val gorunum = SwingChatView(project)
        try {
            gorunum.render(
                ChatUiState(
                    messages = listOf(
                        ChatMessageUi(
                            MessageId(1),
                            ChatRole.ASSISTANT,
                            "```kotlin\nfun yarim(",
                            streaming = true,
                        ),
                    ),
                    streaming = true,
                ),
            )
            assertEquals("akış sırasında editör yaratılmış", once, editorSayisi())
        } finally {
            gorunum.dispose()
        }
    }

    fun testEskiMesajlarSadeMetneDuser() {
        // T44: uzun sohbette canlı editör sayısı sınırlı kalmalı.
        val once = editorSayisi()
        val gorunum = SwingChatView(project)
        try {
            val mesajlar = (1..30).map {
                ChatMessageUi(
                    MessageId(it.toLong()),
                    ChatRole.ASSISTANT,
                    "```kotlin\nval x = $it\n```",
                    streaming = false,
                )
            }
            gorunum.render(ChatUiState(mesajlar))

            val canli = editorSayisi() - once
            assertTrue(
                "30 mesajda $canli canlı editör var — tavan uygulanmamış",
                canli <= 20,
            )
            assertTrue("hiç editör yaratılmamış", canli > 0)
        } finally {
            gorunum.dispose()
        }
        assertEquals(once, editorSayisi())
    }

    fun testAkisBitinceBloklaraGecilir() {
        val once = editorSayisi()
        val gorunum = SwingChatView(project)
        try {
            val mesaj = ChatMessageUi(MessageId(1), ChatRole.ASSISTANT, "```kotlin\nval x = 1\n```")
            gorunum.render(ChatUiState(listOf(mesaj.copy(streaming = true)), streaming = true))
            assertEquals(once, editorSayisi())

            gorunum.render(ChatUiState(listOf(mesaj.copy(streaming = false))))
            assertEquals("akış bitince kod bloğu çizilmemiş", once + 1, editorSayisi())
        } finally {
            gorunum.dispose()
        }
        assertEquals(once, editorSayisi())
    }
}
