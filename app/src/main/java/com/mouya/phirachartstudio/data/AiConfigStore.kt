package com.mouya.phirachartstudio.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores non-secret AI preferences separately from an AES-GCM encrypted API token. */
class AiConfigStore(context: Context) {
    data class Config(val enabled: Boolean, val baseUrl: String, val model: String, val apiKey: String)

    private val preferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    fun load(): Config = Config(
        enabled = preferences.getBoolean("enabled", false),
        baseUrl = preferences.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
        model = preferences.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini",
        apiKey = decryptStoredKey(),
    )

    fun save(enabled: Boolean, baseUrl: String, model: String, apiKey: String?) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val parsed = runCatching { java.net.URI(normalizedBase) }.getOrNull()
        require(
            parsed != null && parsed.scheme?.equals("https", ignoreCase = true) == true &&
                !parsed.host.isNullOrBlank() && parsed.userInfo == null && parsed.fragment == null &&
                parsed.rawQuery == null && !parsed.isOpaque && (parsed.port == -1 || parsed.port in 1..65535)
        ) {
            "AI 服务地址必须是有效的 HTTPS 地址，且不能包含用户信息、查询参数或片段"
        }
        require(normalizedBase.length <= 512) { "AI 服务地址过长" }
        require(model.isNotBlank() && model.length <= 160) { "模型 ID 无效" }
        val editor = preferences.edit()
            .putBoolean("enabled", enabled)
            .putString("base_url", normalizedBase)
            .putString("model", model.trim())
        if (!apiKey.isNullOrBlank()) {
            val (iv, encrypted) = encrypt(apiKey.trim())
            editor.putString("key_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("key_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
        check(editor.commit()) { "无法保存 AI 设置" }
    }

    fun clearKey() {
        preferences.edit().remove("key_iv").remove("key_ciphertext").apply()
    }

    private fun encrypt(value: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv to cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun decryptStoredKey(): String {
        val ivText = preferences.getString("key_iv", null) ?: return ""
        val dataText = preferences.getString("key_ciphertext", null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse {
            // Never fall back to plaintext if keystore material is invalid or has been reset.
            clearKey()
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object { const val KEY_ALIAS = "phira_chart_studio_ai_key_v1" }
}
