package takagi.ru.monica.rustcore

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto

@RunWith(AndroidJUnit4::class)
class BitwardenRustKdfInstrumentedTest {
    @Test
    fun rustPbkdf2MatchesCurrentBitwardenImplementation() {
        val password = "correct horse battery staple"
        val salt = "user@example.com"
        val iterations = 10_000
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val saltBytes = salt.toByteArray(StandardCharsets.UTF_8)

        try {
            val expected = BitwardenCrypto.deriveMasterKeyPbkdf2(password, salt, iterations)
            val actual = RustBitwardenKdfCore.derivePbkdf2Sha256(
                passwordBytes = passwordBytes,
                saltBytes = saltBytes,
                iterations = iterations,
            )
            assertNotNull("Rust PBKDF2 JNI returned null", actual)
            assertArrayEquals(expected, actual)
            expected.fill(0)
            actual?.fill(0)
        } finally {
            passwordBytes.fill(0)
            saltBytes.fill(0)
        }
    }

    @Test
    fun rustArgon2idMatchesCurrentBitwardenSaltAndParameterSemantics() {
        val password = "correct horse battery staple"
        val salt = "user@example.com"
        val iterations = 2
        val memoryMb = 1
        val parallelism = 1
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val saltBytes = salt.toByteArray(StandardCharsets.UTF_8)
        val saltHash = MessageDigest.getInstance("SHA-256").digest(saltBytes)

        try {
            val expected = BitwardenCrypto.deriveMasterKeyArgon2(
                password = password,
                salt = salt,
                iterations = iterations,
                memory = memoryMb,
                parallelism = parallelism,
            )
            val actual = RustBitwardenKdfCore.deriveArgon2id(
                passwordBytes = passwordBytes,
                saltBytes = saltHash,
                iterations = iterations,
                memoryKiB = memoryMb * 1024,
                parallelism = parallelism,
            )
            assertNotNull("Rust Argon2id JNI returned null", actual)
            assertArrayEquals(expected, actual)
            expected.fill(0)
            actual?.fill(0)
        } finally {
            passwordBytes.fill(0)
            saltBytes.fill(0)
            saltHash.fill(0)
        }
    }
}
