package dev.acp.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Testlerde `InlineCompletionRequest` kurar.
 *
 * Platform bu nesneyi normalde kendi üretir; testte sağlayıcıyı doğrudan
 * çağırabilmek için elle kuruluyor.
 */
internal object InlineCompletionIstekYardimcisi {

    fun kur(fixture: CodeInsightTestFixture): InlineCompletionRequest {
        val editor = fixture.editor
        val imlec = editor.caretModel.offset
        return InlineCompletionRequest(
            InlineCompletionEvent.DirectCall(editor, editor.caretModel.currentCaret, DataContext.EMPTY_CONTEXT),
            fixture.file,
            editor,
            editor.document,
            imlec,
            imlec,
            null,
        )
    }
}
