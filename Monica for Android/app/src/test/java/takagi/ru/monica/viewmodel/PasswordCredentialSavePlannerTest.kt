package takagi.ru.monica.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry

class PasswordCredentialSavePlannerTest {

    @Test
    fun identicalCredentialsStillReceiveIndependentFreshIdentities() {
        val source = PasswordEntry(
            id = 42,
            title = "Example",
            website = "https://example.com",
            username = "old-account",
            password = "old-password",
            notes = "shared notes",
            keepassDatabaseId = 7,
            keepassEntryUuid = "old-keepass-entry",
            keepassGroupUuid = "old-keepass-group",
            bitwardenCipherId = "old-cipher",
            bitwardenRevisionDate = "old-revision",
            bitwardenLocalModified = true,
            replicaGroupId = "old-replica-group"
        )
        val replicaIds = ArrayDeque(listOf("password:credential-a", "password:credential-b"))

        val templates = buildIndependentPasswordCredentialTemplates(
            commonEntry = source,
            credentials = listOf(
                PasswordCredentialDraft(username = "same", password = "same-secret"),
                PasswordCredentialDraft(username = "same", password = "same-secret")
            ),
            replicaGroupIdFactory = { replicaIds.removeFirst() }
        )

        assertEquals(2, templates.size)
        assertEquals(0L, templates[0].id)
        assertEquals(0L, templates[1].id)
        assertEquals("same", templates[0].username)
        assertEquals("same-secret", templates[0].password)
        assertEquals("same", templates[1].username)
        assertEquals("same-secret", templates[1].password)
        assertNotEquals(templates[0].replicaGroupId, templates[1].replicaGroupId)
        assertEquals("password:credential-a", templates[0].replicaGroupId)
        assertEquals("password:credential-b", templates[1].replicaGroupId)
        templates.forEach { template ->
            assertNull(template.keepassEntryUuid)
            assertNull(template.keepassGroupUuid)
            assertNull(template.bitwardenCipherId)
            assertNull(template.bitwardenRevisionDate)
            assertTrue(!template.bitwardenLocalModified)
        }
    }

    @Test
    fun commonMetadataIsCopiedWhileAuthenticatorRemainsCredentialScoped() {
        val common = PasswordEntry(
            title = "Shared title",
            website = "https://example.com/login",
            username = "",
            password = "",
            notes = "Shared note",
            appPackageName = "com.example.app",
            appName = "Example",
            categoryId = 9,
            authenticatorKey = "must-not-leak-to-all",
            customIconType = "UPLOADED",
            customIconValue = "shared.png"
        )

        val templates = buildIndependentPasswordCredentialTemplates(
            commonEntry = common,
            credentials = listOf(
                PasswordCredentialDraft(
                    username = "first@example.com",
                    password = "first-secret",
                    authenticatorKey = "totp-for-first",
                    customIconValue = "first.png"
                ),
                PasswordCredentialDraft(
                    username = "second@example.com",
                    password = "second-secret",
                    authenticatorKey = "",
                    customIconValue = "second.png"
                )
            ),
            replicaGroupIdFactory = { java.util.UUID.randomUUID().toString() }
        )

        assertEquals(listOf("Shared title", "Shared title"), templates.map { it.title })
        assertEquals(listOf("Shared note", "Shared note"), templates.map { it.notes })
        assertEquals(listOf("com.example.app", "com.example.app"), templates.map { it.appPackageName })
        assertEquals(listOf("totp-for-first", ""), templates.map { it.authenticatorKey })
        assertEquals(listOf("first.png", "second.png"), templates.map { it.customIconValue })
    }

    @Test
    fun identicalIndependentCredentialTemplatesRemainVisibleAsTwoItems() {
        val templates = buildIndependentPasswordCredentialTemplates(
            commonEntry = PasswordEntry(
                title = "Same",
                website = "example.com",
                username = "",
                password = ""
            ),
            credentials = listOf(
                PasswordCredentialDraft("same", "same-secret"),
                PasswordCredentialDraft("same", "same-secret")
            ),
            replicaGroupIdFactory = ArrayDeque(
                listOf("password:first", "password:second")
            )::removeFirst
        ).mapIndexed { index, entry -> entry.copy(id = (index + 1).toLong()) }

        val visible = dedupePasswordDisplayRows(
            templates.map { it to "same-secret" }
        ) { candidates -> candidates.firstOrNull() }

        assertEquals(2, visible.size)
    }
}
