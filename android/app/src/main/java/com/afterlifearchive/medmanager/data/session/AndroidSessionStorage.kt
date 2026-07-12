package com.afterlifearchive.medmanager.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.afterlifearchive.medmanager.ui.AppMode
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSessionStorage(context: Context) : SessionStorage {
    private val preferences = context.getSharedPreferences("session_preferences", Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)

    override var mode: AppMode?
        get() = preferences.getString(MODE, null)?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
        set(value) = preferences.edit().apply {
            if (value == null) remove(MODE) else putString(MODE, value.name)
        }.apply()

    override var currentPatientId: String?
        get() = preferences.getString(PATIENT_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(PATIENT_ID) else putString(PATIENT_ID, value)
        }.apply()

    override fun getSecret(key: String): String? {
        val encoded = secrets.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = bytes.first().toInt()
            val iv = bytes.copyOfRange(1, 1 + ivLength)
            val encrypted = bytes.copyOfRange(1 + ivLength, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override fun putSecret(key: String, value: String?) {
        if (value == null) {
            secrets.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray())
        val packed = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        secrets.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val MODE = "lastAppMode"
        const val PATIENT_ID = "currentPatientId"
        const val KEY_ALIAS = "medication_app_session_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
