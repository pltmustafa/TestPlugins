package com.pltmustafa.pltstream.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

object CryptoJS {
    private const val KEY_SIZE    = 256
    private const val IV_SIZE     = 128
    private const val HASH_CIPHER = "AES/CBC/PKCS7Padding"
    private const val AES         = "AES"
    private const val KDF_DIGEST  = "MD5"
    private const val APPEND      = "Salted__"

    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key       = ByteArray(KEY_SIZE / 8)
        val iv        = ByteArray(IV_SIZE / 8)
        evpkdf(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)

        val keyS   = SecretKeySpec(key, AES)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keyS, ivSpec)

        val cipherText = cipher.doFinal(plainText.toByteArray())
        val sBytes     = APPEND.toByteArray()
        val b          = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        System.arraycopy(sBytes, 0, b, 0, sBytes.size)
        System.arraycopy(saltBytes, 0, b, sBytes.size, saltBytes.size)
        System.arraycopy(cipherText, 0, b, sBytes.size + saltBytes.size, cipherText.size)

        return Base64.encodeToString(b, Base64.DEFAULT)
    }

    fun decrypt(password: String, cipherText: String): String {
        val ctBytes         = Base64.decode(cipherText.toByteArray(), Base64.DEFAULT)
        val saltBytes       = Arrays.copyOfRange(ctBytes, 8, 16)
        val cipherTextBytes = Arrays.copyOfRange(ctBytes, 16, ctBytes.size)

        val key = ByteArray(KEY_SIZE / 8)
        val iv  = ByteArray(IV_SIZE / 8)
        evpkdf(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)

        val cipher = Cipher.getInstance(HASH_CIPHER)
        val keyS   = SecretKeySpec(key, AES)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(iv))

        val plainText = cipher.doFinal(cipherTextBytes)
        return String(plainText)
    }

    private fun evpkdf(password: ByteArray, keySize: Int, ivSize: Int, salt: ByteArray, resultKey: ByteArray, resultIv: ByteArray): ByteArray {
        return evpkdf(password, keySize, ivSize, salt, 1, KDF_DIGEST, resultKey, resultIv)
    }

    private fun evpkdf(password: ByteArray, keySize: Int, ivSize: Int, salt: ByteArray, iterations: Int, hashAlgorithm: String, resultKey: ByteArray, resultIv: ByteArray): ByteArray {
        val keySizeInternal = keySize / 32
        val ivSizeInternal  = ivSize / 32
        val targetKeySize   = keySizeInternal + ivSizeInternal
        val derivedBytes    = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray?    = null
        val hash                 = MessageDigest.getInstance(hashAlgorithm)

        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) {
                hash.update(block)
            }

            hash.update(password)
            block = hash.digest(salt)
            hash.reset()

            for (i in 1 until iterations) {
                block = hash.digest(block!!)
                hash.reset()
            }

            System.arraycopy(
                block!!, 0, derivedBytes, numberOfDerivedWords * 4,
                min(block.size, (targetKeySize - numberOfDerivedWords) * 4)
            )

            numberOfDerivedWords += block.size / 4
        }

        System.arraycopy(derivedBytes, 0, resultKey, 0, keySizeInternal * 4)
        System.arraycopy(derivedBytes, keySizeInternal * 4, resultIv, 0, ivSizeInternal * 4)

        return derivedBytes
    }

    private fun generateSalt(length: Int): ByteArray {
        return ByteArray(length).apply {
            SecureRandom().nextBytes(this)
        }
    }
}
