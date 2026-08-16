package takagi.ru.monica.util

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.utils.KeePassKeyFileStore
import takagi.ru.monica.utils.readKeePassKeyFileBytesLimited

class KeePassKeyFileStorePolicyTest {

    @Test
    fun internalPathIsOpaqueAndStableForFingerprint() {
        val fingerprint = "A".repeat(64)

        assertEquals(
            "keepass_keyfiles/${"a".repeat(64)}.bin",
            KeePassKeyFileStore.relativePathForFingerprint(fingerprint)
        )
    }

    @Test
    fun displayNameCannotEscapeWithPathSeparators() {
        val safe = KeePassKeyFileStore.sanitizeDisplayName("../private/key.xml")

        assertFalse(safe.contains('/'))
        assertFalse(safe.contains('\\'))
        assertTrue(safe.isNotBlank())
    }

    @Test
    fun keyFileSizeLimitMatchesKdbxCredentialPolicy() {
        assertEquals(1024 * 1024, KeePassKeyFileStore.MAX_KEY_FILE_BYTES)
    }

    @Test
    fun boundedReaderAcceptsExactLimit() {
        val bytes = ByteArray(32) { it.toByte() }

        assertTrue(
            bytes.contentEquals(
                ByteArrayInputStream(bytes).readKeePassKeyFileBytesLimited(bytes.size)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundedReaderRejectsContentLargerThanLimit() {
        ByteArrayInputStream(ByteArray(33))
            .readKeePassKeyFileBytesLimited(32)
    }
}
