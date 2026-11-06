package io.issac.pleosandroid.core.database.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only a Keystore-wrapped random DB key is written, inside Android's no-backup directory. */
internal class DatabaseKeyStore(private val context: Context) {
    @Synchronized
    fun getOrCreate(name: String): ByteArray {
        val file = AtomicFile(File(context.noBackupFilesDir, "$name.key"))
        val alias = "pleos.collection.$name"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (file.baseFile.exists()) {
            val key = keyStore.getKey(alias, null) as? SecretKey ?: throw IOException("Database key unavailable")
            val data = file.readFully()
            if (data.size <= 12) throw IOException("Invalid wrapped database key")
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
                doFinal(data.copyOfRange(12, data.size))
            }
        }
        if (context.getDatabasePath(name).exists()) throw IOException("Existing database has no wrapped key")
        val key = keyStore.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        ).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
        val password = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val wrapped = cipher.doFinal(password)
        val output = file.startWrite()
        try {
            output.write(cipher.iv + wrapped)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            password.fill(0)
            throw error
        }
        return password
    }
}
