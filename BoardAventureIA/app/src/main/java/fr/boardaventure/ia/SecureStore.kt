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
    private fun saveSecret(name: String, value: String) {
        if (value.isBlank()) { prefs.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString(name, Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP)).apply()
    }
    private fun readSecret(name: String): String? = runCatching {
        val b = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        require(b.size > 12) { "Secret chiffré invalide" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        String(c.doFinal(b.copyOfRange(12, b.size)))
    }.getOrNull()
    fun saveApiKey(value: String) = saveSecret("key", value)
    fun apiKey(): String? = readSecret("key")
    fun saveProviderKey(provider: String, value: String) = saveSecret(provider.lowercase() + "_key", value)
    fun providerKey(provider: String): String? = readSecret(provider.lowercase() + "_key")
    fun saveProvider(value: String) { prefs.edit().putString("provider", value).apply() }
    fun provider(): String = prefs.getString("provider", "openrouter") ?: "openrouter"
    fun energyReserve(): Long = prefs.getLong("energy_reserve", 20L)
    fun bonusReserve(): Long = prefs.getLong("bonus_reserve", 5L)
    fun coinReservePercent(): Long = prefs.getLong("coin_reserve_percent", 10L)
    fun saveRiskSettings(energy: Long, bonus: Long, coins: Long) {
        prefs.edit().putLong("energy_reserve", energy.coerceIn(0L, 100L))
            .putLong("bonus_reserve", bonus.coerceIn(0L, 50L))
            .putLong("coin_reserve_percent", coins.coerceIn(0L, 30L)).apply()
    }
    fun saveModel(v: String) = saveProviderModel("openrouter", v)
    fun model() = providerModel("openrouter", "nvidia/nemotron-3.5-super-vl:free")
    fun saveProviderModel(provider: String, value: String) {
        prefs.edit().putString(provider.lowercase() + "_model", value.trim()).apply()
    }
    fun providerModel(provider: String, defaultValue: String): String =
        prefs.getString(provider.lowercase() + "_model", defaultValue) ?: defaultValue
    fun saveFallbackProviders(values: Set<String>) {
        val safe = values.map { it.lowercase() }.filter { it in setOf("openrouter", "gemini", "groq") }.toSet()
        prefs.edit().putString("fallback_providers", safe.joinToString(",")).apply()
    }
    fun fallbackProviders(): Set<String> {
        val raw = prefs.getString("fallback_providers", null)
        return raw?.split(",")?.map { it.trim().lowercase() }?.filter {
            it in setOf("openrouter", "gemini", "groq")
        }?.toSet() ?: setOf("openrouter", "gemini", "groq")
    }
}
