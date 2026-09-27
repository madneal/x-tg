package com.example.tgclient.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/** Keeps TDLib's database key encrypted by a device-bound Android Keystore key. */
class DatabaseKeyStore(context: Context, accountId: String = DEFAULT_ACCOUNT_ID) {
    private val appContext = context.applicationContext
    private val accountSuffix = accountId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private val preferences = appContext.getSharedPreferences(
        if (accountId == DEFAULT_ACCOUNT_ID) LEGACY_PREFERENCES else "$PREFERENCES_PREFIX.$accountSuffix",
        Context.MODE_PRIVATE,
    )
    private val keyAlias = if (accountId == DEFAULT_ACCOUNT_ID) LEGACY_KEY_ALIAS else "$KEY_ALIAS_PREFIX.$accountSuffix"
    private val keyFile = File(accountRoot(appContext, accountId), DATABASE_KEY_FILE)

    fun getOrCreateKey(): ByteArray {
        val encoded = preferences.getString(KEY_VALUE, null)
        var preferenceError: Exception? = null
        if (!encoded.isNullOrBlank()) {
            try {
                val databaseKey = decrypt(Base64.decode(encoded, Base64.NO_WRAP))
                writeKeyFileIfMissing(encoded)
                return databaseKey
            } catch (error: Exception) {
                preferenceError = error
            }
        }

        // SharedPreferences can be lost or left unwritten if the process is
        // killed immediately after the first login. Keep the same encrypted
        // value beside the account's TDLib database as a second durable copy.
        keyFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }?.let { fileValue ->
            val databaseKey = decrypt(Base64.decode(fileValue, Base64.NO_WRAP))
            check(preferences.edit().putString(KEY_VALUE, fileValue).commit()) {
                "Unable to persist the TDLib database key"
            }
            return databaseKey
        }

        preferenceError?.let { error ->
            throw IllegalStateException("Unable to recover the TDLib database key", error)
        }

        val databaseKey = Random.nextBytes(32)
        persist(databaseKey)
        return databaseKey
    }

    fun clear() {
        preferences.edit().remove(KEY_VALUE).commit()
        keyFile.delete()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
        }
    }

    private fun persist(databaseKey: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(databaseKey)
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        writeKeyFile(encoded)
        check(preferences.edit().putString(KEY_VALUE, encoded).commit()) {
            "Unable to persist the TDLib database key"
        }
    }

    private fun writeKeyFileIfMissing(encoded: String) {
        if (!keyFile.isFile) writeKeyFile(encoded)
    }

    private fun writeKeyFile(encoded: String) {
        val parent = keyFile.parentFile ?: error("Unable to determine TDLib key directory")
        check(parent.exists() || parent.mkdirs()) { "Unable to create TDLib key directory" }
        val temporary = File(parent, "${keyFile.name}.tmp")
        temporary.writeText(encoded)
        check(temporary.renameTo(keyFile) || run {
            temporary.copyTo(keyFile, overwrite = true)
            temporary.delete()
            true
        }) { "Unable to persist the TDLib database key file" }
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
        const val DATABASE_KEY_FILE = ".database-key"

        fun accountRoot(context: Context, accountId: String): File = if (accountId == DEFAULT_ACCOUNT_ID) {
            File(context.noBackupFilesDir, "tdlib")
        } else {
            File(context.noBackupFilesDir, "tdlib/accounts/$accountId")
        }
    }
}
