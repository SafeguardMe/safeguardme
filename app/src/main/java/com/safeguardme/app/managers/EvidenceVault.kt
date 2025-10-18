package com.safeguardme.app.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceVault @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class ReadResult(val content: String?, val tampered: Boolean)

    private val integrityPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setUserAuthenticationRequired(true, AUTH_VALIDITY_SECONDS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun writeEncrypted(file: File, payload: String) {
        ensureParent(file)
        val encrypted = buildEncryptedFile(file)
        encrypted.openFileOutput().use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
        }
        integrityPrefs.edit()
            .putString(file.name, sha256(payload))
            .remove(tamperKey(file.name))
            .apply()
    }

    fun readEncrypted(file: File): ReadResult {
        if (!file.exists()) {
            return ReadResult(null, false)
        }

        return try {
            val encrypted = buildEncryptedFile(file)
            val content = encrypted.openFileInput().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }

            val digest = sha256(content)
            val expected = integrityPrefs.getString(file.name, null)
            val tampered = expected != null && expected != digest

            if (tampered) {
                flagTamper(file.name)
            }

            ReadResult(content, tampered)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt evidence file ${file.name}", e)
            flagTamper(file.name)
            ReadResult(null, true)
        }
    }

    fun wasTampered(fileName: String): Boolean =
        integrityPrefs.getBoolean(tamperKey(fileName), false)

    fun clearTamperFlag(fileName: String) {
        integrityPrefs.edit().remove(tamperKey(fileName)).apply()
    }

    private fun buildEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private fun ensureParent(file: File) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
    }

    private fun flagTamper(fileName: String) {
        integrityPrefs.edit()
            .putBoolean(tamperKey(fileName), true)
            .apply()
    }

    private fun tamperKey(fileName: String): String = "tamper_$fileName"

    companion object {
        private const val TAG = "EvidenceVault"
        private const val PREFS_NAME = "evidence_vault_integrity"
        private const val AUTH_VALIDITY_SECONDS = 30 // require biometric/device credential within 30s
    }
}
