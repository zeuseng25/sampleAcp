package dev.acp.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * API anahtarının saklandığı yer: işletim sisteminin anahtar zinciri.
 *
 * Anahtar **kesinlikle** [AcpSettings]'e konmaz; oraya konursa `acp.xml` içinde
 * düz metin olarak diske yazılır ve oradan yedeklere, ayar senkronizasyonuna
 * yayılır. `AcpSecretsTest.testAnahtarAyarXmlineASLAYazilmaz` bunu korur.
 *
 * Anahtar sunucu adresine göre saklanır: iki sunucu arasında geçiş yapıldığında
 * her ikisinin anahtarı da korunur.
 *
 * ⚠️ **EDT'de çağırmayın.** [PasswordSafe] işletim sisteminin anahtar zincirine
 * erişir ve bloklayabilir (kullanıcıya parola sorabilir). Arka planda okuyup
 * bellekte önbelleğe alın.
 */
internal object AcpSecrets {

    private const val ALT_SISTEM = "ACP"

    fun getApiKey(baseUrl: String): String? =
        PasswordSafe.instance.getPassword(nitelikler(baseUrl))
            ?.takeIf { it.isNotBlank() }

    /** `null` veya yalnızca boşluk verilirse anahtar silinir. */
    fun setApiKey(baseUrl: String, key: String?) {
        val temiz = key?.takeIf { it.isNotBlank() }
        PasswordSafe.instance.set(
            nitelikler(baseUrl),
            temiz?.let { Credentials(null, it) },
        )
    }

    private fun nitelikler(baseUrl: String) =
        CredentialAttributes(generateServiceName(ALT_SISTEM, baseUrl))
}
