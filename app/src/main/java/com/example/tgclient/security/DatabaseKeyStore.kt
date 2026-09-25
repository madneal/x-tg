package com.example.tgclient.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/** Keeps TDLib's database key encrypted by a device-bound Android Keystore key. */
class DatabaseKeyStore(context: Context, accountId: String = DEFAULT_ACCOUNT_ID) {
    private val accountSuffix = accountId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private val preferences = context.getSharedPreferences(
        if (accountId == DEFAULT_ACCOUNT_ID) LEGACY_PREFERENCES else "$PREFERENCES_PREFIX.$accountSuffix",
        Context.MODE_PRIVATE,
    )
    private val keyAlias = if (accountId == DEFAULT_ACCOUNT_ID) LEGACY_KEY_ALIAS else "$KEY_ALIAS_PREFIX.$accountSuffix"

    fun getOrCreateKey(): ByteArray {
        val encoded = preferences.getString(KEY_VALUE, null)
        if (encoded == null) {
            val databaseKey = Random.nextBytes(32)
            persist(databaseKey)
            return databaseKey
        }
        return decrypt(Base64.decode(encoded, Base64.NO_WRAP))
    }

    fun clear() {
        preferences.edit().remove(KEY_VALUE).apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
        }
    }

    private fun persist(databaseKey: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(databaseKey)
        preferences.edit().putString(KEY_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > GCM_IV_BYTES) { "Corrupt database key" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, encrypted.copyOfRange(0, GCM_IV_BYTES)),
        )
        return cipher.doFinal(encrypted.copyOfRange(GCM_IV_BYTES, encrypted.size))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ACCOUNT_ID = "default"
        const val LEGACY_KEY_ALIAS = "chatwave.tdlib.database"
        const val KEY_ALIAS_PREFIX = "chatwave.tdlib.database"
        const val LEGACY_PREFERENCES = "secure_database_key"
        const val PREFERENCES_PREFIX = "secure_database_key"
        const val KEY_VALUE = "wrapped_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
