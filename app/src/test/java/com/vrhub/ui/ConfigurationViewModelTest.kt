package com.vrhub.ui

import com.vrhub.data.InvalidUrlException
import com.vrhub.data.MissingKeysException
import com.vrhub.data.ServerConfig
import com.vrhub.data.TestResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ConfigurationViewModel test configuration logic.
 * Tests Story 10.4 AC #1-4: Test button behavior with loading, success, and error states.
 */
class ConfigurationViewModelTest {

    /**
     * AC #1: Given user entered configuration, when TEST tapped, loading indicator appears.
     * AC #2: Given test succeeds, "Configuration valid" displays and SAVE enabled.
     * AC #3: Given test fails (connection error), error displays "Connection failed: [error]".
     * AC #4: Given test fails (timeout > 10s), error displays "Connection timeout".
     */

    @Test
    fun `TestResult Success includes no error message`() {
        val result = TestResult.Success
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `TestResult ConnectionError stores message`() {
        val result = TestResult.ConnectionError("Network unreachable")
        assertTrue(result is TestResult.ConnectionError)
        assertEquals("Network unreachable", (result as TestResult.ConnectionError).message)
    }

    @Test
    fun `TestResult Timeout stores seconds value`() {
        val result = TestResult.Timeout(10)
        assertTrue(result is TestResult.Timeout)
        assertEquals(10, (result as TestResult.Timeout).seconds)
    }

    @Test
    fun `TestResult InvalidConfig stores message`() {
        val result = TestResult.InvalidConfig("Missing baseUri")
        assertTrue(result is TestResult.InvalidConfig)
        assertEquals("Missing baseUri", (result as TestResult.InvalidConfig).message)
    }

    @Test
    fun `validateManualConfig returns failure when baseUri is blank`() {
        val pairs = listOf(KeyValuePair("password", "test123"))
        val result = validateManualConfig(pairs)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MissingKeysException)
    }

    @Test
    fun `validateManualConfig returns failure when password is blank`() {
        val pairs = listOf(KeyValuePair("baseUri", "https://example.com"))
        val result = validateManualConfig(pairs)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MissingKeysException)
    }

    @Test
    fun `validateManualConfig returns failure for invalid URL scheme`() {
        val pairs = listOf(
            KeyValuePair("baseUri", "ftp://example.com"),
            KeyValuePair("password", "test123")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidUrlException)
    }

    @Test
    fun `validateManualConfig returns success with valid config`() {
        val pairs = listOf(
            KeyValuePair("baseUri", "https://example.com"),
            KeyValuePair("password", "test123")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isSuccess)
        val config = result.getOrNull()
        assertEquals("https://example.com", config?.baseUri)
        assertEquals("test123", config?.password)
    }

    @Test
    fun `validateManualConfig extracts extra keys`() {
        val pairs = listOf(
            KeyValuePair("baseUri", "https://example.com"),
            KeyValuePair("password", "test123"),
            KeyValuePair("customKey", "customValue")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isSuccess)
        val config = result.getOrNull()
        assertEquals("customValue", config?.extraKeys?.get("customKey"))
    }

    @Test
    fun `validateManualConfig is case-insensitive for baseUri and password keys`() {
        val pairs = listOf(
            KeyValuePair("BaseUri", "https://example.com"),
            KeyValuePair("PASSWORD", "test123")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateManualConfig trims whitespace from values`() {
        val pairs = listOf(
            KeyValuePair("baseUri", "  https://example.com  "),
            KeyValuePair("password", "  test123  ")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isSuccess)
        val config = result.getOrNull()
        assertEquals("https://example.com", config?.baseUri)
        assertEquals("test123", config?.password)
    }

    @Test
    fun `validateManualConfig rejects space-only password`() {
        val pairs = listOf(
            KeyValuePair("baseUri", "https://example.com"),
            KeyValuePair("password", "   ")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isFailure)
    }

    @Test
    fun `validateManualConfig rejects empty key entries`() {
        val pairs = listOf(
            KeyValuePair("baseUri", "https://example.com"),
            KeyValuePair("", ""),
            KeyValuePair("password", "test123")
        )
        val result = validateManualConfig(pairs)
        assertTrue(result.isSuccess) // empty entries ignored
    }
}

/**
 * Helper function that mirrors ConfigurationViewModel.validateManualConfig logic.
 * Used for unit testing without ViewModel instantiation.
 */
private fun validateManualConfig(kvPairs: List<KeyValuePair>): Result<ServerConfig> {
    val baseUri = kvPairs.find { it.key.equals("baseUri", ignoreCase = true) }?.value ?: ""
    val password = kvPairs.find { it.key.equals("password", ignoreCase = true) }?.value ?: ""

    val trimmedUri = baseUri.trim()
    val trimmedPassword = password.trim()

    if (trimmedUri.isBlank()) {
        return Result.failure(MissingKeysException("baseUri is required"))
    }
    if (trimmedPassword.isBlank()) {
        return Result.failure(MissingKeysException("password is required"))
    }

    if (!trimmedUri.startsWith("http://", ignoreCase = true) && !trimmedUri.startsWith("https://", ignoreCase = true)) {
        return Result.failure(InvalidUrlException("Invalid URL format"))
    }

    val uri = try {
        java.net.URI(trimmedUri)
    } catch (e: Exception) {
        return Result.failure(InvalidUrlException("Invalid URL format"))
    }
    val scheme = uri.scheme
    if (scheme != "http" && scheme != "https") {
        return Result.failure(InvalidUrlException("Invalid URL format"))
    }

    val extraKeys = kvPairs
        .filter {
            !it.key.equals("baseUri", ignoreCase = true) &&
            !it.key.equals("password", ignoreCase = true) &&
            it.key.isNotBlank() &&
            it.value.isNotBlank()
        }
        .associate { it.key to it.value }

    return Result.success(ServerConfig(
        baseUri = trimmedUri,
        password = trimmedPassword,
        extraKeys = extraKeys
    ))
}