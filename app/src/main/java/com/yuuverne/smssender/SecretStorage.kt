package com.yuuverne.smssender

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SecretCodec {
    fun encrypt(plainText: String, associatedData: String): String
    fun decrypt(cipherText: String, associatedData: String): String
}

/**
 * 使用 Android Keystore 中的 AES 密钥加密秘密值。
 *
 * 密文格式为 Base64(IV || ciphertext)，IV 由 Cipher 在加密时随机生成。
 * preference key 作为 AAD 传入，避免同一密钥下的密文被互换。
 */
internal object AndroidKeystoreSecretCodec : SecretCodec {

    // Object 保证进程内只有一个 codec；@Synchronized 保证所有 ConfigManager 共用同一把初始化锁，
    // 防止并发首次访问时重复创建并覆盖同别名 Keystore 密钥。
    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: String, associatedData: String): String {
        return AesGcmCipher.encrypt(getOrCreateSecretKey(), plainText, associatedData)
    }

    override fun decrypt(cipherText: String, associatedData: String): String {
        return AesGcmCipher.decrypt(getOrCreateSecretKey(), cipherText, associatedData)
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "sms_sender_secrets_v1"
    private const val KEY_SIZE_BITS = 256
}

/** AES/GCM 加解密实现，独立于密钥存储，便于在 JVM 单元测试中验证。 */
internal object AesGcmCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(key: SecretKey, plainText: String, associatedData: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_LENGTH_BYTES) { "Unexpected GCM IV length" }

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(key: SecretKey, encodedPayload: String, associatedData: String): String {
        val payload = Base64.getDecoder().decode(encodedPayload)
        require(payload.size >= IV_LENGTH_BYTES + TAG_LENGTH_BITS / Byte.SIZE_BITS) {
            "Invalid encrypted payload"
        }

        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
