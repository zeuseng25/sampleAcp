import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
// `java` betikte proje uzantısını gösterir; java.* sınıfları içe aktarılarak kullanılır.
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    // Kotlin sürümü hedef platformun bundle ettiği stdlib ile eşleşmeli.
    // 2026.1 → Kotlin 2.3.20. Hedef IDE yükseltilirse burası da yükseltilmeli.
    kotlin("jvm") version "2.3.20"
    // 2.18.1'E YÜKSELTMEYİN. 2.18.1'de bir regresyon var: platform 2026.1 ile
    // :test görevi çözümlenemiyor —
    //   "The 'Core' plugin (ID: 'com.intellij') was not found in .../lib"
    // compileKotlin sorunsuz geçtiği için hata geç fark ediliyor.
    // Üstkaynak: JetBrains/intellij-platform-gradle-plugin#2189 (açık).
    // 2.17.0 ile 2026.1 sorunsuz. Yükseltmeden önce ./gradlew test ile doğrulayın.
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "dev.acp"
// Sürüm gradle.properties'te (`version=`), elle artırılır — spec 005.

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Yerel olarak kurulu IDE'yi kullan — 1 GB'lık indirmeden kaçınır.
// Farklı kurulumlar için: gradle.properties veya ~/.gradle/gradle.properties içinde
//   acp.ide.path=/kendi/yolunuz/IDE.app
val yerelIdeYolu: Provider<String> = providers.gradleProperty("acp.ide.path")
    .orElse("/Applications/IntelliJ IDEA.app")

// Görünen ad. Kasten yedek değeri YOK: gradle.properties tek doğruluk kaynağıdır,
// eksikse build anlaşılır biçimde patlasın (sessizce yanlış adla paketlemektense).
val gorunenAd: Provider<String> = providers.gradleProperty("acp.displayName")

/**
 * Görünen adı çalışma zamanında okunabilecek bir kaynağa yazar.
 * `dev.acp.AcpBranding` bu dosyayı okur.
 */
val generateBranding = tasks.register("generateBranding") {
    val ad = gorunenAd
    val ciktiDizini = layout.buildDirectory.dir("generated/branding")
    inputs.property("displayName", ad)
    outputs.dir(ciktiDizini)
    doLast {
        val deger = ad.orNull
            ?: error("gradle.properties içinde acp.displayName tanımlı değil.")
        // Properties biçiminde anlam taşıyan karakterleri kaçır.
        val kacisli = deger
            .replace("\\", "\\\\")
            .replace("=", "\\=")
            .replace(":", "\\:")
            .replace("\n", " ")
        val kok = ciktiDizini.get().asFile

        // 1) Çalışma zamanında AcpBranding'in okuduğu kaynak.
        val dosya = kok.resolve("acp-branding.properties")
        dosya.parentFile.mkdirs()
        dosya.writeText("displayName=$kacisli\n", Charsets.UTF_8)

        // 2) plugin.xml'in key/bundle ile gösterdiği ad.
        //    Bu olmadan platform "No display name specified in plugin descriptor"
        //    diye SEVERE düşüyor; sabit bir bundle anahtarı kullansaydık da ad
        //    markalanamazdı. Bundle'ı üreterek ikisini birden çözüyoruz.
        val bundle = kok.resolve("messages/AcpBrandingBundle.properties")
        bundle.parentFile.mkdirs()
        bundle.writeText("settings.title=$kacisli\n", Charsets.UTF_8)
    }
}

sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("generated/branding"))
}
tasks.named("processResources") { dependsOn(generateBranding) }

dependencies {
    intellijPlatform {
        // Temel: IDEA Community 2026.1 — ekipteki tüm IDE'ler 2026 ve üzeri.
        // Eklenti yalnızca com.intellij.modules.platform'a bağlı olduğu için
        // DataGrip / WebStorm / PyCharm'da da çalışır.
        // useInstaller = false: düz arşiv, macOS'ta .app/Contents sarmalayıcısı olmadan gelir.
        local(yerelIdeYolu)

        // BasePlatformTestCase için. Plugin 2.18.1 ile bu satır :test görevini
        // çözümlenemez hâle getiriyordu; 2.17.0 ile sorunsuz.
        testFramework(TestFrameworkType.Platform)
    }

    // ÇALIŞMA ZAMANI BAĞIMLILIĞI YOK — bkz. verifyNoBundledJars.
    // JSON için platformun bundle ettiği Jackson kullanılır (JsonNode seviyesinde).

    testImplementation(kotlin("test"))

    // kotlinx-coroutines-test EKLENMEZ. Vanilya coroutines-core'u sürükler ve
    // platformun YAMALI sürümünü (…-intellij-N) gölgeler; BasePlatformTestCase
    // o yamanın API'sini çağırdığı için testler
    //   NoSuchMethodError: runBlockingWithParallelismCompensation
    // ile patlar. Coroutines platformdan gelir — test sınıf yolunda da.
    // runBlocking zaten yeterli; sanal zamana ihtiyaç duyulursa saat enjekte edin
    // (bkz. CoalesceFlow).

    // BasePlatformTestCase JUnit3/4 biçimindedir; platformun test dinleyicisi de
    // junit.framework.TestCase'i arar. Saf testlerimiz JUnit 5 olduğu için ikisi
    // vintage motoruyla bir arada koşar.
    // Not: bunlar yalnızca test sınıf yolundadır, eklenti paketine GİRMEZ
    // (verifyNoBundledJars doğrular).
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Hedef IDE'nin Kotlin sürümünden yeni API kullanmayı derleme anında engeller.
        apiVersion = KotlinVersion.KOTLIN_2_3
        languageVersion = KotlinVersion.KOTLIN_2_3
    }
}

intellijPlatform {
    pluginConfiguration {
        // KİMLİK — asla değiştirilmez; IDE güncellemeleri buna göre eşleşir.
        id = "dev.acp.assistant"
        // GÖRÜNEN AD — gradle.properties'ten (H6).
        name = gorunenAd
        version = project.version.toString()
        /*
         * Eklenti açıklaması. İki kuralı var, ikisi de deneyle öğrenildi:
         *
         * 1. ZORUNLU. Eksikse `verifyPlugin` eklentiyi "geçersiz yapı" sayıp
         *    HİÇBİR doğrulama yapmadan başarısız olur. Uyarı listesi boş
         *    geldiği için "temiz geçti" sanmak kolay — oysa kontrol hiç
         *    çalışmamıştır.
         *
         * 2. BAŞLANGICI ASCII OLMALI. Doğrulayıcı "must start with Latin
         *    characters" diyor ve Türkçe diyakritikleri (ü, ı, ş) Latin
         *    saymıyor. Bu yüzden ilk cümle İngilizce; projenin Türkçe
         *    kuralından bilinçli sapma, gerisi Türkçe.
         *    Doğrulandı: yalnızca ilk satırdaki "ü" kaldırılınca kontrol geçti.
         *
         * Marka adı gömülmez (Altın Kural 6) — yalnızca ne yaptığı anlatılır.
         */
        description = """
            Code assistant for a self-hosted, OpenAI-compatible model server.
            <br/>
            Kendi kontrolünüzdeki bir model sunucusuna (LiteLLM, SGLang, vLLM)
            bağlanır.
            <ul>
              <li>Sohbet paneli — akışlı yanıt, iptal edilebilir</li>
              <li>Seçili koda açıklama, test yazma, iyileştirme ve belgeleme</li>
              <li>Sonucu diff önizlemesiyle uygulama, tek adımda geri alma</li>
              <li>Satır içi kod tamamlama (varsayılan kapalı)</li>
            </ul>
            Vekil sunucu ve sertifika ayarları IDE'den alınır; API anahtarı
            işletim sisteminin anahtar zincirinde saklanır.
        """.trimIndent()
        vendor {
            name = "acp"
        }
        ideaVersion {
            sinceBuild = "261"
            // Açık uçlu: yeni IDE sürümleri çıktığında eklenti kendiliğinden uyumsuz olmasın.
            untilBuild = provider { null }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // Canlı (ağ gerektiren) testler varsayılan olarak atlanır:
    //   ./gradlew test -Dacp.canliTest=true
    // Test JVM'i ayrı bir süreç olduğu için özellik açıkça iletilmeli.
    systemProperty("acp.canliTest", System.getProperty("acp.canliTest") ?: "false")
    // Spec 005: dağıtım kaydı testi gerçek görev çıktısını okur.
    dependsOn("dagitimKaydiOrnek")
    systemProperty("acp.dagitimKaydi", layout.buildDirectory.file("dagitim-ornek/updatePlugins.xml").get().asFile.absolutePath)
    testLogging {
        showStandardStreams = true
    }
}

/**
 * Eklenti dağıtımında bizim jar'ımızdan başka hiçbir şey olmamalı.
 * Platform kendi kotlin-stdlib, coroutines ve Jackson'ını sağlar; ikinci bir kopya
 * göndermek kullanıcının IDE'sinde çalışma zamanı LinkageError'una yol açar.
 * Bu görev, o hata sınıfını tamamen ve kalıcı olarak yakalar.
 */
val verifyNoBundledJars = tasks.register("verifyNoBundledJars") {
    // Klasör taranmaz: sürüm artınca eski zip'ler de orada kalır ve "tek zip"
    // beklentisi bozulurdu (spec 005 yayın denemesinde bulundu). Denetlenen, şu an
    // üretilen paket.
    val paket = tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }
    dependsOn(tasks.named("buildPlugin"))
    doLast {
        val zip = paket.get().asFile.takeIf { it.isFile } ?: error("Dağıtım zip'i bulunamadı: ${paket.get().asFile}")
        val libEntries = zipTree(zip).matching { include("**/lib/*.jar") }.files
            .map { it.name }
            .filterNot { it.startsWith("instrumented-") }
        val yabanci = libEntries.filterNot { it.startsWith("acp") }
        require(yabanci.isEmpty()) {
            "Dağıtıma yabancı jar sızmış: $yabanci — bir çalışma zamanı bağımlılığı eklendi mi?"
        }
        logger.lifecycle("Dağıtım temiz: $libEntries")
    }
}

tasks.named("check") {
    dependsOn(verifyNoBundledJars)
}

/*
 * İlişki denetimi (spec 010 T32) — yalnızca `-Pacp.iliskiDenetimi=true` ile.
 *
 * Test ortamında dil eklentisi yok ve eklemek tuzak #2'ye çarpıyor; ilişki
 * araması bu yüzden gerçek IDE'de doğrulanıyor. Sandbox örnek projeyi açar,
 * senaryoları koşar, sonucu idea.log'a yazar ve kendini kapatır. Güven
 * penceresi atlanır, yoksa açılış onu bekler.
 */
val iliskiDenetimi = providers.gradleProperty("acp.iliskiDenetimi")
tasks.named<JavaExec>("runIde") {
    if (iliskiDenetimi.orNull == "true") {
        systemProperty("acp.iliskiDenetimi", "true")
        systemProperty("idea.trust.all.projects", "true")
        args(layout.projectDirectory.dir("src/test/testData/iliski").asFile.absolutePath)
    }
}

/*
 * Dağıtım kaydı (spec 005 T20-T21): IDE'nin özel eklenti deposu için `updatePlugins.xml`.
 *
 * Kaynak, PAKETE GİREN yamalı plugin.xml — kimlik, sürüm, ad ve since-build oradan
 * okunur; kayıt elle yazılmaz, paketle ayrışamaz (spec Davranış Kuralı 3).
 * Depo adresi kurumdan kuruma değişir ve koda gömülmez (Altın Kural 6):
 *   ./gradlew dagitimKaydiUret -Pacp.repo.publicUrl=https://depo.kurum/…/acp/
 */
abstract class DagitimKaydiGorevi : DefaultTask() {
    @get:InputFile abstract val eklentiXml: RegularFileProperty
    @get:Input abstract val paketAdi: Property<String>
    @get:Input abstract val publicUrl: Property<String>
    @get:OutputFile abstract val cikti: RegularFileProperty

    @TaskAction
    fun uret() {
        val kok = publicUrl.get().trim()
        if (kok.isEmpty()) {
            throw GradleException(
                "Depo adresi tanımlı değil. Örnek: -Pacp.repo.publicUrl=https://depo.kurum.local/repository/acp/",
            )
        }
        if (!kok.startsWith("https://") && !kok.startsWith("http://")) {
            throw GradleException("Depo adresi http(s) olmalı: $kok")
        }
        val fabrika = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val kaynak = fabrika.newDocumentBuilder().parse(eklentiXml.get().asFile)
        fun tek(etiket: String): String =
            kaynak.getElementsByTagName(etiket).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw GradleException("plugin.xml'de <$etiket> yok — kayıt üretilemez")
        val ide = kaynak.getElementsByTagName("idea-version").item(0) as? org.w3c.dom.Element
            ?: throw GradleException("plugin.xml'de <idea-version> yok")

        val belge = fabrika.newDocumentBuilder().newDocument()
        val plugins = belge.createElement("plugins").also { belge.appendChild(it) }
        val plugin = belge.createElement("plugin").also { plugins.appendChild(it) }
        plugin.setAttribute("id", tek("id"))
        plugin.setAttribute("url", kok.trimEnd('/') + "/" + paketAdi.get())
        plugin.setAttribute("version", tek("version"))
        plugin.appendChild(belge.createElement("idea-version").also { e ->
            e.setAttribute("since-build", ide.getAttribute("since-build"))
            // until-build yalnızca pakette varsa: açık uçlu aralık korunur.
            ide.getAttribute("until-build").takeIf { it.isNotBlank() }?.let { e.setAttribute("until-build", it) }
        })
        plugin.appendChild(belge.createElement("name").also { it.textContent = tek("name") })

        val dosya = cikti.get().asFile
        dosya.parentFile.mkdirs()
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
        }.transform(javax.xml.transform.dom.DOMSource(belge), javax.xml.transform.stream.StreamResult(dosya))
        logger.lifecycle("Dağıtım kaydı: ${dosya.absolutePath}")
    }
}

fun DagitimKaydiGorevi.paketten(url: Provider<String>, hedef: String) {
    eklentiXml.set(tasks.named<PatchPluginXmlTask>("patchPluginXml").flatMap { it.outputFile })
    paketAdi.set(tasks.named<Zip>("buildPlugin").flatMap { it.archiveFileName })
    publicUrl.set(url)
    cikti.set(layout.buildDirectory.file("$hedef/updatePlugins.xml"))
}

// Kullanıcıların gördüğü kök; verilmezse yükleme kökü (çoğu depoda aynı).
val depoYuklemeKoku: Provider<String> = providers.gradleProperty("acp.repo.url").orElse("")
val depoOkumaKoku: Provider<String> = providers.gradleProperty("acp.repo.publicUrl").orElse(depoYuklemeKoku)

val dagitimKaydiUret = tasks.register<DagitimKaydiGorevi>("dagitimKaydiUret") {
    group = "dağıtım"
    description = "updatePlugins.xml'i paketin plugin.xml'inden üretir (spec 005)."
    paketten(depoOkumaKoku, "dagitim")
}

// Testin okuduğu örnek: kurum adresi gömülmez, .invalid alan adı hiçbir yere gitmez.
tasks.register<DagitimKaydiGorevi>("dagitimKaydiOrnek") {
    paketten(provider { "https://depo.ornek.invalid/acp/" }, "dagitim-ornek")
}

/*
 * Depo erişimi (spec 005 T30-T43). Düz HTTP: HEAD (var mı), PUT (yükle) — Nexus "raw"
 * ve Artifactory ikisini de destekler. Kimlik YALNIZCA ortam değişkeninden
 * (ACP_REPO_USER / ACP_REPO_TOKEN); @Internal: önbelleğe ve günlüğe girmez.
 */
abstract class DepoGorevi : DefaultTask() {
    @get:Input abstract val depoKoku: Property<String>
    @get:Internal abstract val kullanici: Property<String>
    @get:Internal abstract val anahtar: Property<String>

    protected fun kok(): String {
        val k = depoKoku.get().trim()
        if (k.isEmpty()) {
            throw GradleException("Depo adresi tanımlı değil. Örnek: -Pacp.repo.url=https://depo.kurum.local/repository/acp/")
        }
        if (!k.startsWith("https://") && !k.startsWith("http://")) throw GradleException("Depo adresi http(s) olmalı: $k")
        return k.trimEnd('/') + "/"
    }

    protected fun istek(yol: String): HttpRequest.Builder {
        val b = HttpRequest.newBuilder(URI.create(kok() + yol))
            .timeout(Duration.ofMinutes(2))
        val u = kullanici.orNull
        val t = anahtar.orNull
        if (!u.isNullOrBlank() && !t.isNullOrBlank()) {
            val kimlik = Base64.getEncoder().encodeToString("$u:$t".toByteArray())
            b.header("Authorization", "Basic $kimlik")
        }
        return b
    }

    // Fonksiyon, özellik değil: Gradle görev özelliklerini girdi/çıktı sanıp doğrular.
    protected fun http(): HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
}

/** Aynı sürüm ikinci kez yayınlanmaz: depoda paket varsa DURUR (T30-T31). */
abstract class SurumKontrolGorevi : DepoGorevi() {
    @get:Input abstract val paketAdi: Property<String>

    @TaskAction
    fun kontrol() {
        val yanit = http().send(
            istek(paketAdi.get()).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        when (yanit.statusCode()) {
            404 -> logger.lifecycle("Sürüm kontrolü: ${paketAdi.get()} depoda yok, yayınlanabilir.")
            in 200..299 -> throw GradleException(
                "${paketAdi.get()} depoda ZATEN VAR — üzerine yazılmaz. gradle.properties'te `version`'ı artırın.",
            )
            401, 403 -> throw GradleException("Depo erişimi reddetti (HTTP ${yanit.statusCode()}). ACP_REPO_USER / ACP_REPO_TOKEN doğru mu?")
            else -> throw GradleException("Depo beklenmeyen yanıt verdi (HTTP ${yanit.statusCode()}); yayın durduruldu.")
        }
    }
}

/**
 * Paket ve kayıt birlikte yüklenir (T42). Sıra bilinçli: ÖNCE paket, SONRA kayıt.
 * Paket yüklenemezse kayıt hiç gitmez; kayıt yüklenemezse eski kayıt geçerli kalır
 * ve kullanıcılar eski (çalışan) sürümü görür. Kaydın olmayan bir paketi göstermesi
 * — tek bozuk güncelleme biçimi — böylece imkânsız.
 */
abstract class DepoyaYukleGorevi : DepoGorevi() {
    @get:InputFile abstract val paket: RegularFileProperty
    @get:InputFile abstract val kayit: RegularFileProperty

    @TaskAction
    fun yukle() {
        if (kullanici.orNull.isNullOrBlank() || anahtar.orNull.isNullOrBlank()) {
            throw GradleException("Yükleme kimliği yok: ACP_REPO_USER ve ACP_REPO_TOKEN ortam değişkenlerini tanımlayın.")
        }
        for (dosya in listOf(paket.get().asFile, kayit.get().asFile)) {
            val yanit = http().send(
                istek(dosya.name).PUT(HttpRequest.BodyPublishers.ofFile(dosya.toPath())).build(),
                HttpResponse.BodyHandlers.discarding(),
            )
            if (yanit.statusCode() !in 200..299) {
                throw GradleException("${dosya.name} yüklenemedi (HTTP ${yanit.statusCode()}); yayın yarıda kaldı, kayıt güncellenmedi.")
            }
            logger.lifecycle("Yüklendi: ${dosya.name}")
        }
    }
}

fun DepoGorevi.depoyaBagla() {
    depoKoku.set(depoYuklemeKoku)
    kullanici.set(providers.environmentVariable("ACP_REPO_USER"))
    anahtar.set(providers.environmentVariable("ACP_REPO_TOKEN"))
}

val surumKontrol = tasks.register<SurumKontrolGorevi>("surumKontrol") {
    group = "dağıtım"
    description = "Depoda aynı sürüm varsa durur (spec 005)."
    depoyaBagla()
    paketAdi.set(tasks.named<Zip>("buildPlugin").flatMap { it.archiveFileName })
    // Ucuz kontrol pahalı testlerden sonra: test kırmızıysa depoya hiç gidilmez.
    mustRunAfter("check")
}

val depoyaYukle = tasks.register<DepoyaYukleGorevi>("depoyaYukle") {
    group = "dağıtım"
    description = "Paketi ve dağıtım kaydını depoya yükler (önce paket)."
    depoyaBagla()
    paket.set(tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile })
    kayit.set(dagitimKaydiUret.flatMap { it.cikti })
    dependsOn("check", surumKontrol)
}

tasks.register("yayinla") {
    group = "dağıtım"
    description = "check → paket → sürüm kontrolü → yükleme (spec 005 H3). Testler kırmızıysa yayın olmaz."
    dependsOn(depoyaYukle)
}
