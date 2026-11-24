package fr.boardaventure.ia
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("board_ai_secure", Context.MODE_PRIVATE)
    private val alias = "board_aventure_openrouter_key"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }
    fun saveApiKey(value: String) {
        if (value.isBlank()) { prefs.edit().remove("key").apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("key", Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP)).apply()
    }
    fun apiKey(): String? = runCatching {
        val b = Base64.decode(prefs.getString("key", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        String(c.doFinal(b.copyOfRange(12, b.size)))
    }.getOrNull()
    fun saveModel(v: String) = prefs.edit().putString("model", v).apply()
    fun model() = prefs.getString("model", "nvidia/nemotron-3.5-super-vl:free") ?: "nvidia/nemotron-3.5-super-vl:free"
}
