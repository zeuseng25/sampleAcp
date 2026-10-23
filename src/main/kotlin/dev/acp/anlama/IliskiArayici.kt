package dev.acp.anlama

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import dev.acp.llm.agent.IliskiSonucu
import dev.acp.llm.agent.SembolIstegi

/**
 * IDE'nin anlam dizininden ilişkiler (spec 010).
 *
 * Uygulaması `acp-anlama.xml`'de, `com.intellij.modules.lang` varken kaydedilir
 * (006'daki tamamlamayla aynı kalıp). Kayıtlı değilse [getInstance] null döner ve
 * araçlar metin aramasına yönlendirir.
 */
internal interface IliskiArayici {

    /** Dizinleme sürüyorsa false; ilişki araması o sırada yapılamaz. */
    fun hazirMi(): Boolean

    suspend fun kullanimlar(s: SembolIstegi, max: Int): IliskiSonucu
    suspend fun uygulamalar(s: SembolIstegi, max: Int): IliskiSonucu
    suspend fun tanim(yol: String, satir: Int, ad: String): IliskiSonucu

    companion object {
        fun getInstance(project: Project): IliskiArayici? = project.serviceOrNull()
    }
}
