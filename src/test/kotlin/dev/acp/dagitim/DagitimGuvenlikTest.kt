package dev.acp.dagitim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Yayın kimliği yalnızca ortam değişkeninden okunur (spec 005 T43).
 *
 * Kimlik `gradle.properties`'e veya build betiğine sızarsa depoya gider ve herkes
 * okur. Bu test metin düzeyinde: kimliğin okunduğu tek yol `environmentVariable`.
 */
class DagitimGuvenlikTest {

    private val betik = File("build.gradle.kts").readText()
    private val ozellikler = File("gradle.properties").readText()

    @Test
    fun `kimlik ortam degiskeninden okunur`() {
        assertTrue("""environmentVariable("ACP_REPO_USER")""" in betik, "kullanıcı ortamdan okunmuyor")
        assertTrue("""environmentVariable("ACP_REPO_TOKEN")""" in betik, "anahtar ortamdan okunmuyor")
    }

    @Test
    fun `kimlik ozelliklerden okunmaz ve yazilmaz`() {
        assertFalse(Regex("""gradleProperty\("[^"]*(token|password|user)[^"]*"\)""", RegexOption.IGNORE_CASE).containsMatchIn(betik))
        assertFalse(Regex("(ACP_REPO|token|password)", RegexOption.IGNORE_CASE).containsMatchIn(ozellikler))
    }

    @Test
    fun `depo adresi koda gomulmez`() {
        // Kurumdan kuruma değişir (kullanıcı kararı); yalnızca -P ile.
        assertFalse(Regex("""acp\.repo\.(url|publicUrl)\s*=""").containsMatchIn(ozellikler))
    }
}
