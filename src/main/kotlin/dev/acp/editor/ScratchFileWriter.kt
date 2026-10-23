package dev.acp.editor

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * "Test yaz" sonucunu geçici bir dosyada açar.
 *
 * Mevcut dosyaya **dokunmaz** (spec 003 H2). Testin doğru kaynak klasörüne ve
 * pakete yerleştirilmesi spec'te kapsam dışı: modül ve test kaynak kökü çözümü
 * gerektiriyor ve dile özgü kurallara giriyor.
 */
internal object ScratchFileWriter {

    fun yaz(project: Project, dosyaAdi: String, uzanti: String?, icerik: String): VirtualFile? {
        val dosya = ScratchRootType.getInstance()
            .createScratchFile(project, dosyaAdi, dil(uzanti), icerik)
        dosya?.let { FileEditorManager.getInstance(project).openFile(it, true) }
        return dosya
    }

    /** Dil, uzantıdan `FileType` üzerinden çözülüyor — dile özgü PSI API'si yok. */
    private fun dil(uzanti: String?): Language {
        val tur = uzanti
            ?.takeIf { it.isNotBlank() }
            ?.let { FileTypeManager.getInstance().getFileTypeByFileName("acp.$it") }
        return (tur as? LanguageFileType)?.language ?: Language.ANY
    }
}
