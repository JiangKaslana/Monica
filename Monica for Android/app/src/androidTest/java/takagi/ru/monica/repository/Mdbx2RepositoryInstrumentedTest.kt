package takagi.ru.monica.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.storage.AttachmentKeyVault
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.MdbxStorageLocation
import takagi.ru.monica.data.MdbxSyncStatus
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.security.SecurityManager

@RunWith(AndroidJUnit4::class)
class Mdbx2RepositoryInstrumentedTest {
    @Test
    fun passwordAttachmentAndReopenRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = PasswordDatabase.getDatabase(context)
        val databaseDao = room.localMdbxDatabaseDao()
        val securityManager = SecurityManager(context)
        val password = "mdbx2-repository-password"
        val bootstrapRepository = Mdbx2Repository(
            context = context,
            databaseDao = databaseDao,
            securityManager = securityManager,
            passwordEntryDao = room.passwordEntryDao(),
            secureItemDao = room.secureItemDao(),
            customFieldDao = room.customFieldDao()
        )
        val vaultFile = bootstrapRepository.createInitializedVaultFile(MdbxTigaMode.SKY, password)
        var databaseId = 0L
        var encryptedAttachmentPath: String? = null
        try {
            databaseId = databaseDao.insertDatabase(
                LocalMdbxDatabase(
                    name = "MDBX2 repository test",
                    filePath = vaultFile.absolutePath,
                    storageLocation = MdbxStorageLocation.INTERNAL.name,
                    sourceType = MdbxSourceType.LOCAL_INTERNAL.name,
                    engineType = MdbxEngineType.RUST_MDBX2.name,
                    tigaMode = MdbxTigaMode.SKY.name,
                    encryptedPassword = securityManager.encryptData(password),
                    unlockMethod = MdbxUnlockMethod.MASTER_PASSWORD.storedValue,
                    kdfProfile = "argon2id-mdbx2",
                    workingCopyPath = vaultFile.absolutePath,
                    cacheCopyPath = vaultFile.absolutePath,
                    isOfflineAvailable = true,
                    lastSyncStatus = MdbxSyncStatus.LOCAL_ONLY.name
                )
            )
            val repository = MdbxRepositoryFactory.create(context, room, securityManager)
            val folder = repository.createFolder(databaseId, "Personal", null)
            val entry = PasswordEntry(
                id = 42L,
                title = "Initial",
                website = "https://example.com",
                username = "alice",
                password = "secret",
                mdbxDatabaseId = databaseId,
                mdbxFolderId = folder.folderId
            )

            repository.upsertPassword(entry)
            val created = repository.readStoredEntries(databaseId).single { !it.deleted }
            assertEquals("password:42", created.entryId)
            assertEquals("Initial", created.title)
            assertEquals("alice", JSONObject(created.payloadJson).getString("username"))

            repository.upsertPassword(entry.copy(title = "Updated", username = "bob"))
            val updated = repository.readStoredEntries(databaseId).single { !it.deleted }
            assertEquals("Updated", updated.title)
            assertEquals("bob", JSONObject(updated.payloadJson).getString("username"))

            val authenticator = SecureItem(
                id = 51L,
                itemType = ItemType.TOTP,
                title = "GitHub OTP",
                itemData = """{"secret":"JBSWY3DPEHPK3PXP","issuer":"GitHub","accountName":"alice","digits":6,"period":30,"algorithm":"SHA1","otpType":"TOTP"}""",
                mdbxDatabaseId = databaseId,
                mdbxFolderId = folder.folderId
            )
            val passkey = PasskeyEntry(
                id = 61L,
                credentialId = "credential-test-61",
                rpId = "example.com",
                rpName = "Example",
                userId = "dXNlci02MQ",
                userName = "alice@example.com",
                userDisplayName = "Alice",
                publicKey = "test-public-key",
                privateKeyAlias = "missing-test-private-key",
                mdbxDatabaseId = databaseId,
                mdbxFolderId = folder.folderId
            )
            repository.upsertSecureItem(authenticator)
            repository.upsertPasskey(passkey)
            val steamEntryId = repository.upsertSteamMaFileEntry(
                databaseId = databaseId,
                entryId = null,
                title = "Steam test account",
                maFileJson = """{"steamid":"76561199000000001","account_name":"mdbx2_test"}"""
            )
            val activeEntries = repository.readStoredEntries(databaseId).filterNot { it.deleted }
            assertEquals(
                setOf("login", "totp", "passkey", "steam-mafile"),
                activeEntries.map { it.entryType }.toSet()
            )
            assertEquals(
                "GitHub OTP",
                activeEntries.single { it.entryType == "totp" }.title
            )
            assertEquals(
                "credential-test-61",
                JSONObject(activeEntries.single { it.entryType == "passkey" }.payloadJson)
                    .getString("credential_id")
            )
            assertEquals("steam-mafile:76561199000000001", steamEntryId)
            assertEquals(
                "mdbx2_test",
                JSONObject(repository.listSteamMaFileEntries(databaseId).single().payloadJson)
                    .getString("account_name")
            )

            val attachmentStorage = AttachmentStorage(context)
            val encrypted = attachmentStorage.writeEncrypted("mdbx2 attachment".byteInputStream())
            encryptedAttachmentPath = encrypted.relativePath
            val wrappedCek = try {
                AttachmentKeyVault(securityManager).wrap(encrypted.cek)
            } finally {
                encrypted.cek.fill(0)
            }
            val attachment = Attachment(
                parentPasswordId = entry.id,
                source = AttachmentSource.LOCAL.name,
                fileName = "sample.txt",
                mimeType = "text/plain",
                sizeBytes = encrypted.sizeBytes,
                sha256Hex = encrypted.sha256Hex,
                wrappedCek = wrappedCek,
                localPath = encrypted.relativePath,
                downloadState = AttachmentDownloadState.DOWNLOADED.name,
                createdAt = Date().time,
                updatedAt = Date().time
            )
            repository.upsertAttachment(databaseId, "password:42", attachment)
            val storedAttachment = repository.readStoredAttachments(databaseId).single()
            assertEquals("password:42", storedAttachment.entryId)
            assertEquals("sample.txt", storedAttachment.fileName)
            assertTrue(storedAttachment.blob.isNotEmpty())
            assertFalse(storedAttachment.wrappedCek.isNullOrBlank())

            repository.deleteAttachment(databaseId, "password:42", attachment)
            assertTrue(repository.readStoredAttachments(databaseId).isEmpty())

            repository.deletePassword(entry)
            repository.deleteSecureItem(authenticator)
            repository.deletePasskey(passkey)
            repository.deleteSteamMaFileEntry(databaseId, steamEntryId)
            val reopenedRepository = MdbxRepositoryFactory.create(context, room, securityManager)
            val deleted = reopenedRepository.readStoredEntries(databaseId)
            assertEquals(4, deleted.size)
            assertEquals(
                setOf(
                    "password:42",
                    "totp:51",
                    "passkey:credential-test-61",
                    "steam-mafile:76561199000000001"
                ),
                deleted.map { it.entryId }.toSet()
            )
            assertTrue(deleted.all { it.deleted })
        } finally {
            encryptedAttachmentPath?.let { AttachmentStorage(context).delete(it) }
            if (databaseId > 0L) databaseDao.deleteDatabaseById(databaseId)
            vaultFile.delete()
        }
    }
}
