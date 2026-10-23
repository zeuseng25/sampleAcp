package dev.acp

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.AcpBundle"

/**
 * Kullanıcıya görünen tüm metinler buradan geçer.
 * Baştan bundle kullanmak, sonradan geriye dönüp yüzlerce string'i toplamaktan ucuzdur.
 */
internal object AcpBundle : DynamicBundle(BUNDLE) {

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
