package dev.acp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `dev.acp.llm` paketi saf kalmalı: IntelliJ platformuna hiç bağlanmamalı.
 *
 * Bunun iki faydası var — o katman IDE başlatmadan saniyeler içinde test edilebilir,
 * ve ileride çekirdek başka bir yerde (örn. bir CLI) yeniden kullanılabilir.
 *
 * Ayrı bir Gradle modülü kurmak yerine bu testle zorluyoruz: modül sınırı
 * IntelliJ Platform Gradle Plugin kurulumunu gereksiz yere karmaşıklaştırırdı.
 */
class ArchitectureTest {

    @Test
    fun `llm paketi IntelliJ platformuna baglanmaz`() = safMi("src/main/kotlin/dev/acp/llm")

    /** Spec 009: komut tahmini, denetimi ve kırpma IDE'siz test edilebilmeli. */
    @Test
    fun `komut paketi IntelliJ platformuna baglanmaz`() = safMi("src/main/kotlin/dev/acp/komut")

    private fun safMi(yol: String) {
        val root = File(yol)
        assertTrue(root.isDirectory, "Beklenen kaynak dizini yok: ${root.absolutePath}")

        val ihlaller = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { dosya ->
                dosya.readLines().asSequence().withIndex()
                    .filter { (_, satir) -> satir.trimStart().startsWith("import com.intellij") }
                    .map { (i, satir) -> "${dosya.path}:${i + 1}  ${satir.trim()}" }
            }
            .toList()

        assertTrue(
            ihlaller.isEmpty(),
            "$yol saf kalmalı ama IntelliJ importu bulundu:\n" +
                ihlaller.joinToString("\n")
        )
    }
}
