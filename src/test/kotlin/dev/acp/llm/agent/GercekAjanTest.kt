package dev.acp.llm.agent

import com.fasterxml.jackson.databind.ObjectMapper
import dev.acp.llm.AracCagrisi
import dev.acp.llm.AracTanimi
import dev.acp.llm.openai.OpenAiLlmClient
import dev.acp.settings.UrlNormalizer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ajan döngüsü **gerçek modelle ve gerçek araçlarla** (spec 008).
 *
 * Sahte araçlı testler (`AjanDongusuTest`) hata yollarını doğruluyor — onları
 * gerçek modele istendiği anda yaptıramayız. Bu test ise tam tersini yapıyor:
 * gerçek `grep`, gerçek dosyalar, gerçek model. **T10'un otomatik hâli.**
 *
 * Ağ gerektirir, varsayılan olarak atlanır:
 *
 * ```
 * export OPENROUTER_API_KEY=...
 * ./gradlew test -Dacp.canliTest=true --tests "*GercekAjanTest*"
 * ```
 *
 * Sunucu ve model değiştirmek için: `ACP_AJAN_URL`, `ACP_AJAN_KEY`, `ACP_AJAN_MODEL`.
 *
 * **Hiçbir dosya değiştirilmez** — `dosya_duzenle` yalnızca plana ekler, ki
 * ajanın gerçek davranışı da budur (spec 008: yazma döngüden sonra).
 */
class GercekAjanTest {

    private val mapper = ObjectMapper()
    private val kok = File(System.getProperty("user.dir"))

    private fun canliMi() = System.getProperty("acp.canliTest") == "true"

    // ---- gerçek araçlar ----

    private val toplananDuzenlemeler = mutableListOf<Triple<String, String, String>>()

    private fun ara(desen: String): String {
        val p = ProcessBuilder("grep", "-rn", "--include=*.kt", "-F", desen, "src/main")
            .directory(kok).redirectErrorStream(true).start()
        val cikti = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val satirlar = cikti.trim().lines().filter { it.isNotBlank() }
        if (satirlar.isEmpty()) return "sonuç yok"
        // Bağlamı doldurmamak için kırpılıyor (spec 008 T41).
        val kirpik = satirlar.take(40)
        return kirpik.joinToString("\n") +
            if (satirlar.size > 40) "\n... (${satirlar.size - 40} sonuç daha)" else ""
    }

    private fun oku(yol: String): String {
        val f = File(kok, yol)
        if (!f.canonicalPath.startsWith(kok.canonicalPath)) return "HATA: proje dışı yol"
        if (!f.isFile) return "HATA: bulunamadı: $yol"
        val icerik = f.readText()
        return if (icerik.length > 6000) icerik.take(6000) + "\n... (kısaltıldı)" else icerik
    }

    /** DİSKE YAZMAZ — spec 008'in "ya tamamen ya hiç" kuralı buna dayanıyor. */
    private fun duzenle(yol: String, eski: String, yeni: String): String {
        val f = File(kok, yol)
        if (!f.isFile) return "HATA: bulunamadı: $yol"
        if (eski !in f.readText()) {
            return "HATA: 'eski' metin dosyada birebir bulunamadı."
        }
        toplananDuzenlemeler += Triple(yol, eski, yeni)
        return "kaydedildi"
    }

    private val yurutucu = object : AracYurutucu {
        override suspend fun calistir(cagri: AracCagrisi): String {
            val a = mapper.readTree(cagri.argumanlarJson)
            return when (cagri.ad) {
                "dosya_ara" -> ara(a.path("desen").asText())
                "dosya_oku" -> oku(a.path("yol").asText())
                "dosya_duzenle" -> duzenle(
                    a.path("yol").asText(), a.path("eski").asText(), a.path("yeni").asText(),
                )
                else -> "HATA: bilinmeyen araç: ${cagri.ad}"
            }
        }
    }

    private fun sema(vararg alanlar: String): com.fasterxml.jackson.databind.JsonNode {
        val n = mapper.createObjectNode()
        n.put("type", "object")
        val p = n.putObject("properties")
        alanlar.forEach { p.putObject(it).put("type", "string") }
        val r = n.putArray("required"); alanlar.forEach { r.add(it) }
        return n
    }

    private val araclar = listOf(
        AracTanimi("dosya_ara", "Projede metin arar; dosya yolu ve tam satırı döndürür.", sema("desen")),
        AracTanimi("dosya_oku", "Bir dosyanın içeriğini döndürür (projeye göreli yol).", sema("yol")),
        AracTanimi(
            "dosya_duzenle",
            "Bir düzenleme kaydeder. Diske YAZMAZ. 'eski' metin dosyada birebir geçmelidir.",
            sema("yol", "eski", "yeni"),
        ),
    )

    // ---- test ----

    @Test
    fun `gercek depoda cok dosyali degisiklik planlanir`() = runBlocking {
        assumeTrue(canliMi(), "Canlı test kapalı (-Dacp.canliTest=true ile açın)")
        val anahtar = System.getenv("ACP_AJAN_KEY") ?: System.getenv("OPENROUTER_API_KEY")
        assumeTrue(!anahtar.isNullOrBlank(), "API anahtarı tanımlı değil")

        val url = System.getenv("ACP_AJAN_URL")?.takeIf { it.isNotBlank() }
            ?: "https://openrouter.ai/api/v1"
        val model = System.getenv("ACP_AJAN_MODEL")?.takeIf { it.isNotBlank() }
            ?: "qwen/qwen3.8-27b"

        val istemci = OpenAiLlmClient(
            http = HttpClient.newHttpClient(),
            baseUrl = UrlNormalizer.normalize(url)!!,
            apiKey = anahtar,
            readTimeoutMs = 120_000,
        )

        val olaylar = AjanDongusu(istemci, yurutucu, araclar, model, maxAdim = 12)
            .calistir(
                "Bu Kotlin projesinde UIUtil.getContextHelpForeground() cagrilarini bul ve " +
                    "hepsini JBColor.GRAY ile degistir.",
            )
            .toList()

        val bitis = olaylar.last() as AjanOlayi.Bitti
        val cagrilar = olaylar.filterIsInstance<AjanOlayi.AracCagirdi>()

        println("Gerçek ajan — model=$model")
        println("  bitiş     : ${bitis.neden}  ${bitis.mesaj?.take(120)}")
        println("  araç çağrı: ${cagrilar.size} (${cagrilar.map { it.ad }.distinct()})")
        println("  düzenleme : ${toplananDuzenlemeler.size} " +
            "(${toplananDuzenlemeler.map { it.first }.distinct().size} dosya)")

        // T10'da bu görev 3 adımda, 9 düzenlemeyle bitti. Eşikler gevşek
        // tutuldu: model sürümü değişince test kırılgan olmasın, ama
        // "hiç düzenleme üretmedi" durumu yakalansın.
        assertTrue(
            bitis.neden == BitisNedeni.TAMAMLANDI,
            "Döngü tamamlanmadı: ${bitis.neden}. Çağrılar: ${cagrilar.map { it.ad }}",
        )
        assertTrue(
            toplananDuzenlemeler.isNotEmpty(),
            "Hiç düzenleme üretilmedi — model yerleri buldu ama harekete geçmedi",
        )
        assertTrue(
            toplananDuzenlemeler.all { (_, eski, yeni) -> eski != yeni },
            "Değişiklik içermeyen düzenleme üretildi",
        )
        assertTrue(
            toplananDuzenlemeler.none { (_, _, yeni) -> "getContextHelpForeground" in yeni },
            "Yeni metin hâlâ eski çağrıyı içeriyor",
        )
    }
}
