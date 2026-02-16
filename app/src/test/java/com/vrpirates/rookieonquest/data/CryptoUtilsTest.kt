package com.vrpirates.rookieonquest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class CryptoUtilsTest {

    @Test
    fun hmacSha256_generatesCorrectSignature() {
        // Known test vector for HMAC-SHA256
        val secret = "key"
        val message = "The quick brown fox jumps over the lazy dog"
        val expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"
        
        val actual = CryptoUtils.hmacSha256(message, secret)
        assertEquals(expected, actual)
    }

    @Test
    fun hmacSha256_differentInput_generatesDifferentSignature() {
        val secret = "secret"
        val sig1 = CryptoUtils.hmacSha256("input1", secret)
        val sig2 = CryptoUtils.hmacSha256("input2", secret)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun hmacSha256_differentSecret_generatesDifferentSignature() {
        val input = "input"
        val sig1 = CryptoUtils.hmacSha256(input, "secret1")
        val sig2 = CryptoUtils.hmacSha256(input, "secret2")
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun sha256_generatesCorrectFileHash() {
        val tempFile = File.createTempFile("sha256_test", ".txt")
        try {
            FileOutputStream(tempFile).use { it.write("hello world".toByteArray()) }
            
            // echo -n "hello world" | sha256sum
            val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
            val actual = CryptoUtils.sha256(tempFile)
            assertEquals(expected, actual)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun sha256_reportsProgress() {
        val tempFile = File.createTempFile("sha256_progress", ".txt")
        try {
            // Write some data to ensure chunks for progress
            FileOutputStream(tempFile).use { fos ->
                val data = ByteArray(1024 * 16)
                repeat(4) { fos.write(data) }
            }
            
            val progressUpdates = mutableListOf<Float>()
            CryptoUtils.sha256(tempFile) { progress ->
                progressUpdates.add(progress)
            }
            
            assertNotEquals(0, progressUpdates.size)
            assertEquals(1.0f, progressUpdates.last(), 0.001f)
            // Progress should be monotonic
            for (i in 0 until progressUpdates.size - 1) {
                assert(progressUpdates[i] <= progressUpdates[i+1])
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun md5_generatesCorrectHash() {
        // echo -n "hello" | md5sum
        val expected = "5d41402abc4b2a76b9719d911017c592"
        val actual = CryptoUtils.md5("hello")
        assertEquals(expected, actual)
    }
}
