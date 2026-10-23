package dev.acp.context

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import dev.acp.llm.prompt.ContextItem

/**
 * Soruya eklenecek bağlamı toplayan sağlayıcı.
 *
 * Uzantı noktası olarak tanımlı: ileride dile özgü bir modül (örn. Java PSI ile
 * ilgili sınıfları bulan bir sağlayıcı) çekirdeğe dokunmadan katkı verebilir.
 *
 * @property priority Küçük değer önce toplanır ve bütçeden önce pay alır.
 */
internal interface ContextProvider {

    val priority: Int

    suspend fun collect(request: ContextRequest): List<ContextItem>

    companion object {
        val EP: ExtensionPointName<ContextProvider> =
            // DİKKAT: tam ad = <eklenti kimliği>.<name>. plugin.xml'de name="contextProvider"
        // yazdığımız ve kimlik dev.acp.assistant olduğu için burası da öyle olmalı.
        // Uyuşmazsa uzantı noktası bulunamaz ve bağlam sessizce boş gelir.
        ExtensionPointName.create("dev.acp.assistant.contextProvider")
    }
}

/**
 * @param tokenButcesi Ayarlardan gelen üst sınır. Sağlayıcılar bunu kendileri
 *   uygulamaz; toplama sonrası [CompositeContextProvider] uygular.
 */
internal data class ContextRequest(
    val project: Project,
    val tokenButcesi: Int,
)
