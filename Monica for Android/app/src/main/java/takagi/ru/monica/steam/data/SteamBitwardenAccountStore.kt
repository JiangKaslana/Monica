package takagi.ru.monica.steam.data

import androidx.room.withTransaction
import java.security.MessageDigest
import java.util.Date
import takagi.ru.monica.attachments.facade.AttachmentFacade
import takagi.ru.monica.attachments.model.AttachmentOwner
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.facade.AttachmentFacade.BitwardenContext
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.bitwarden.sync.syncForUserVisibleRequest
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.LOGIN_TYPE_STEAM_MAFILE
import takagi.ru.monica.data.model.isExternalSteamMaFileEntry
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec
import takagi.ru.monica.steam.importer.SteamMaFileParser
import takagi.ru.monica.steam.importer.SteamMaFilePayload

data class SteamBitwardenAccountRecord(
    val account: SteamAccount,
    val passwordEntryId: Long,
    val cipherId: String
)

class SteamBitwardenAccountStore(
    private val database: PasswordDatabase,
    private val bitwardenRepository: BitwardenRepository,
    private val attachmentFacade: AttachmentFacade,
    private val parser: SteamMaFileParser = SteamMaFileParser()
) {
    private val passwordDao = database.passwordEntryDao()
    private val customFieldDao = database.customFieldDao()
    private val vaultDao = database.bitwardenVaultDao()

    suspend fun loadAccounts(
        vaultId: Long,
        refreshRemote: Boolean = false
    ): List<SteamBitwardenAccountRecord> {
        val vault = vaultDao.getVaultById(vaultId) ?: return emptyList()
        check(bitwardenRepository.isVaultUnlocked(vaultId)) { "Bitwarden vault is locked" }
        val syncError = if (refreshRemote) {
            when (
                val result = bitwardenRepository.syncForUserVisibleRequest(
                    vaultId = vaultId,
                    requestIdPrefix = "steam-mafile-load"
                )
            ) {
                is BitwardenRepository.SyncResult.Error -> result.message
                is BitwardenRepository.SyncResult.EmptyVaultBlocked -> result.reason
                is BitwardenRepository.SyncResult.Success -> null
            }
        } else {
            null
        }
        val records = passwordDao.getByBitwardenVaultId(vaultId)
            .filterNot { it.isDeleted || it.isArchived }
            .mapNotNull { entry -> loadEntry(vault, entry) }
            .mapIndexed { index, record ->
                record.copy(
                    account = record.account.copy(selected = index == 0, sortOrder = index)
                )
            }
        if (records.isEmpty() && syncError != null) {
            throw IllegalStateException(syncError)
        }
        return records
    }

    private suspend fun loadEntry(
        vault: BitwardenVault,
        entry: PasswordEntry
    ): SteamBitwardenAccountRecord? {
        val vaultId = vault.id
        val cipherId = entry.bitwardenCipherId?.takeIf(String::isNotBlank) ?: return null
        val fields = customFieldDao.getFieldsByEntryIdSync(entry.id)
        val hasMarker = SteamExternalMaFileContract.isMarked(fields.map { it.title to it.value })
        if (!hasMarker && !entry.isExternalSteamMaFileEntry()) return null

        // Migrate entries created before the internal type was persisted. This update is local
        // metadata only; the marker remains the source of truth for the external Steam store.
        if (hasMarker && !entry.isExternalSteamMaFileEntry()) {
            passwordDao.updatePasswordEntry(entry.copy(loginType = LOGIN_TYPE_STEAM_MAFILE))
        }

        val cachedContext = bitwardenRepository.getAttachmentBitwardenContext(vault, cipherId)
        if (cachedContext != null) {
            loadEntryFromAttachments(vaultId, entry, cipherId, cachedContext)?.let { return it }
        }

        // A new device can receive the cipher before its attachment metadata is present in
        // Monica's local attachment table. Fetch the cipher directly as a recovery step and
        // resolve its per-item key before downloading the maFile.
        val snapshot = bitwardenRepository.fetchAttachmentCipherSnapshot(vault, cipherId)
            ?: return null
        attachmentFacade.reconcileBitwardenAttachments(
            owner = AttachmentOwner.password(entry.id),
            remoteAttachments = snapshot.attachments
        )
        return loadEntryFromAttachments(vaultId, entry, cipherId, snapshot.context)
    }

    private suspend fun loadEntryFromAttachments(
        vaultId: Long,
        entry: PasswordEntry,
        cipherId: String,
        context: BitwardenContext
    ): SteamBitwardenAccountRecord? {
        val attachments = attachmentFacade.listByPassword(entry.id)
            .filter { it.sourceEnum == AttachmentSource.BITWARDEN }
        val candidateNames = SteamExternalMaFileContract.candidateFileNames(
            attachments.map { it.fileName }
        )
        val candidates = candidateNames
            .flatMap { name -> attachments.filter { it.fileName == name } }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<takagi.ru.monica.attachments.model.Attachment> {
                    SteamExternalMaFileContract.isMaFile(it.fileName)
                }.thenByDescending { it.updatedAt }
            )
        for (attachment in candidates) {
            if (attachment.sizeBytes > SteamExternalMaFileContract.MAX_MAFILE_BYTES) continue
            val bytes = runCatching {
                attachmentFacade.readAttachmentBytes(
                    attachmentId = attachment.id,
                    maxBytes = SteamExternalMaFileContract.MAX_MAFILE_BYTES,
                    bitwardenContext = context
                )
            }.getOrNull() ?: continue
            val payload = try {
                parser.parse(
                    maFileContent = bytes.toString(Charsets.UTF_8),
                    fileName = attachment.fileName
                )
            } catch (_: Throwable) {
                null
            } finally {
                bytes.fill(0)
            }
            if (payload != null) {
                val displayName = entry.title.trim().takeIf { title ->
                    title.isNotBlank() && title != payload.accountName && title != payload.steamId
                } ?: payload.displayName
                return SteamBitwardenAccountRecord(
                    account = payload.copy(displayName = displayName).toSteamAccount(
                        id = runtimeAccountId(vaultId, entry.id)
                    ),
                    passwordEntryId = entry.id,
                    cipherId = cipherId
                )
            }
        }
        return null
    }

    suspend fun upsertPayload(
        vaultId: Long,
        payload: SteamMaFilePayload,
        existingPasswordEntryId: Long? = null
    ): SteamBitwardenAccountRecord {
        val provisional = payload.toSteamAccount(
            id = existingPasswordEntryId?.let { runtimeAccountId(vaultId, it) } ?: 0L
        )
        return upsertAccount(vaultId, existingPasswordEntryId, provisional)
    }

    suspend fun upsertAccount(
        vaultId: Long,
        existingPasswordEntryId: Long?,
        account: SteamAccount
    ): SteamBitwardenAccountRecord {
        val vault = vaultDao.getVaultById(vaultId)
            ?: throw IllegalStateException("Bitwarden vault not found")
        check(bitwardenRepository.isVaultUnlocked(vaultId)) { "Bitwarden vault is locked" }
        val resolvedExisting = existingPasswordEntryId
            ?.let { entryId ->
                passwordDao.getPasswordEntryById(entryId)
                    ?.takeIf { it.bitwardenVaultId == vaultId }
            }
            ?: account.steamId.takeIf(String::isNotBlank)?.let { steamId ->
                findMarkedEntryBySteamId(vaultId, steamId)
            }
        val title = account.displayName
            .ifBlank { account.accountName }
            .ifBlank { account.visibleSteamId }
            .ifBlank { "Steam" }
        val now = Date()
        val candidate = (resolvedExisting ?: PasswordEntry(
            title = title,
            website = "https://steamcommunity.com",
            username = account.steamId.ifBlank { account.accountName },
            password = "",
            bitwardenVaultId = vaultId,
            bitwardenLocalModified = true
        )).copy(
            title = title,
            website = "https://steamcommunity.com",
            username = account.steamId.ifBlank { account.accountName },
            password = "",
            updatedAt = now,
            bitwardenVaultId = vaultId,
            isDeleted = false,
            deletedAt = null,
            isArchived = false,
            archivedAt = null,
            loginType = LOGIN_TYPE_STEAM_MAFILE,
            bitwardenLocalModified = true
        )
        val entryId = database.withTransaction {
            val id = if (candidate.id == 0L) {
                passwordDao.insertPasswordEntry(candidate)
            } else {
                passwordDao.updatePasswordEntry(candidate)
                candidate.id
            }
            val preserved = customFieldDao.getFieldsByEntryIdSync(id)
                .filterNot {
                    it.title.equals(
                        SteamExternalMaFileContract.MARKER_FIELD,
                        ignoreCase = true
                    )
                }
            customFieldDao.replaceFieldsForEntry(
                entryId = id,
                newFields = preserved + CustomField(
                    entryId = id,
                    title = SteamExternalMaFileContract.MARKER_FIELD,
                    value = SteamExternalMaFileContract.MARKER_VALUE,
                    isProtected = false,
                    sortOrder = preserved.size
                )
            )
            id
        }

        bitwardenRepository.requestLocalMutationSync(vaultId)
        val syncResult = bitwardenRepository.syncForUserVisibleRequest(
            vaultId = vaultId,
            requestIdPrefix = "steam-mafile-upsert"
        )
        if (syncResult is BitwardenRepository.SyncResult.Error) {
            throw IllegalStateException(syncResult.message)
        }
        val syncedEntry = passwordDao.getPasswordEntryById(entryId)
            ?.takeIf { !it.bitwardenCipherId.isNullOrBlank() }
            ?: account.steamId.takeIf(String::isNotBlank)?.let { steamId ->
                findMarkedEntryBySteamId(vaultId, steamId)
            }
                ?.takeIf { !it.bitwardenCipherId.isNullOrBlank() }
            ?: throw IllegalStateException("Bitwarden cipher was not created")
        val cipherId = syncedEntry.bitwardenCipherId.orEmpty()
        val context = bitwardenRepository.getAttachmentBitwardenContext(vault, cipherId)
            ?: throw IllegalStateException("Bitwarden attachment context is unavailable")
        val oldAttachments = attachmentFacade.listByPassword(syncedEntry.id)
            .filter { it.sourceEnum == AttachmentSource.BITWARDEN }
            .let { attachments ->
                val namedMaFiles = attachments.filter {
                    SteamExternalMaFileContract.isMaFile(it.fileName)
                }
                if (namedMaFiles.isNotEmpty()) namedMaFiles
                else attachments.singleOrNull()?.let(::listOf).orEmpty()
            }
        val bytes = SteamMaFileBackupCodec.encode(account).toByteArray(Charsets.UTF_8)
        val newAttachment = try {
            attachmentFacade.addInlineAttachment(
                AttachmentFacade.InlineUploadRequest(
                    owner = AttachmentOwner.password(syncedEntry.id),
                    source = AttachmentSource.BITWARDEN,
                    fileName = SteamExternalMaFileContract.attachmentFileName(account),
                    mimeType = SteamExternalMaFileContract.MIME_TYPE,
                    bytes = bytes,
                    isPlusActivated = true,
                    bitwardenPremium = bitwardenRepository.isVaultPremium(vaultId),
                    bitwardenContext = context
                )
            )
        } finally {
            bytes.fill(0)
        }
        try {
            oldAttachments.forEach { old ->
                attachmentFacade.deleteAttachment(
                    attachmentId = old.id,
                    bitwardenContext = context
                )
            }
        } catch (error: Throwable) {
            runCatching {
                attachmentFacade.deleteAttachment(
                    attachmentId = newAttachment.id,
                    bitwardenContext = context
                )
            }
            throw error
        }
        return SteamBitwardenAccountRecord(
            account = account.copy(
                id = runtimeAccountId(vaultId, syncedEntry.id),
                updatedAt = System.currentTimeMillis()
            ),
            passwordEntryId = syncedEntry.id,
            cipherId = cipherId
        )
    }

    suspend fun deleteAccount(vaultId: Long, passwordEntryId: Long) {
        val entry = passwordDao.getPasswordEntryById(passwordEntryId) ?: return
        val cipherId = entry.bitwardenCipherId
            ?: throw IllegalStateException("Bitwarden cipher is missing")
        bitwardenRepository.queueCipherDelete(
            vaultId = vaultId,
            cipherId = cipherId,
            entryId = passwordEntryId
        ).getOrThrow()
        val syncResult = bitwardenRepository.syncForUserVisibleRequest(
            vaultId = vaultId,
            requestIdPrefix = "steam-mafile-delete"
        )
        if (syncResult is BitwardenRepository.SyncResult.Error) {
            throw IllegalStateException(syncResult.message)
        }
        attachmentFacade.purgeByPassword(passwordEntryId)
        passwordDao.getPasswordEntryById(passwordEntryId)?.let { entry ->
            passwordDao.deletePasswordEntry(entry)
        }
    }

    private suspend fun findMarkedEntryBySteamId(vaultId: Long, steamId: String): PasswordEntry? {
        val markedEntries = passwordDao.getByBitwardenVaultId(vaultId).filter { entry ->
            SteamExternalMaFileContract.isMarked(
                customFieldDao.getFieldsByEntryIdSync(entry.id).map { it.title to it.value }
            )
        }
        // 新建 Cipher 在首次同步后可能尚未完成附件元数据对齐，先按 Monica 写入的
        // Steam ID 查找；已有外部条目则再以 maFile 中解析出的 Steam ID为准。
        markedEntries.firstOrNull { it.username == steamId }?.let { return it }
        return loadAccounts(vaultId)
            .firstOrNull { it.account.steamId == steamId }
            ?.let { record -> passwordDao.getPasswordEntryById(record.passwordEntryId) }
    }

    private fun SteamMaFilePayload.toSteamAccount(id: Long): SteamAccount {
        val now = System.currentTimeMillis()
        return SteamAccount(
            id = id,
            steamId = steamId,
            accountName = accountName,
            displayName = displayName,
            deviceId = deviceId,
            sharedSecret = sharedSecret,
            identitySecret = identitySecret,
            revocationCode = revocationCode,
            tokenGid = tokenGid,
            accessToken = accessToken,
            refreshToken = refreshToken,
            steamLoginSecure = steamLoginSecure,
            rawSteamGuardJson = rawJson,
            selected = true,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now
        )
    }

    companion object {
        fun runtimeAccountId(vaultId: Long, passwordEntryId: Long): Long {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("bitwarden:$vaultId:$passwordEntryId".toByteArray(Charsets.UTF_8))
            var value = 0L
            repeat(7) { index ->
                value = (value shl 8) or (digest[index].toLong() and 0xff)
            }
            return -value.coerceAtLeast(1L)
        }
    }
}
