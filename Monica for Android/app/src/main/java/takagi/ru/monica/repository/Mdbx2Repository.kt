package takagi.ru.monica.repository

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.storage.AttachmentKeyVault
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.data.CustomFieldDao
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalMdbxDatabaseDao
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordEntryDao
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.SecureItemDao
import takagi.ru.monica.data.resolvedActiveFilePath
import takagi.ru.monica.passkey.PasskeyPrivateKeyStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.util.TotpDataResolver
import uniffi.mdbx_ffi.MdbxAttachmentContentLimits
import uniffi.mdbx_ffi.MdbxAttachmentCreateRequest
import uniffi.mdbx_ffi.MdbxVault
import uniffi.mdbx_ffi.MdbxWriteCommand

class Mdbx2Repository(
    context: Context,
    private val databaseDao: LocalMdbxDatabaseDao,
    private val securityManager: SecurityManager,
    private val passwordEntryDao: PasswordEntryDao? = null,
    private val secureItemDao: SecureItemDao? = null,
    private val customFieldDao: CustomFieldDao? = null
) : MdbxRepository {
    private val appContext = context.applicationContext
    private val sessions = Mdbx2VaultSessionExecutor(appContext, databaseDao, securityManager)
    private val attachmentStorage = AttachmentStorage(appContext)
    private val attachmentKeyVault = AttachmentKeyVault(securityManager)

    override suspend fun requiresStrictMutationConsistency(databaseId: Long): Boolean = true

    suspend fun createInitializedVaultFile(
        tigaMode: MdbxTigaMode,
        password: String
    ): File = sessions.createInitializedVaultFile(tigaMode, password)

    suspend fun deleteOwnedVaultFile(file: File): Boolean = sessions.deleteOwnedVaultFile(file)

    override suspend fun readStoredEntries(databaseId: Long): List<MdbxStoredVaultEntry> =
        sessions.withVault(databaseId) { _, vault ->
            buildList {
                vault.listAllProjects().forEach { project ->
                    vault.listEntries(project.collectionId, null).forEach { entry ->
                        add(entry.toStoredEntry())
                    }
                    vault.listDeletedEntries(project.collectionId, null).forEach { entry ->
                        add(entry.toStoredEntry())
                    }
                }
            }.distinctBy { it.entryId to it.deleted }
        }

    override suspend fun readStoredAttachments(databaseId: Long): List<MdbxStoredAttachment> =
        sessions.withVault(databaseId) { _, vault ->
            val logicalEntryIds = vault.listAllProjects()
                .flatMap { project ->
                    vault.listEntries(project.collectionId, null) +
                        vault.listDeletedEntries(project.collectionId, null)
                }
                .associate { entry -> entry.entryId to entry.logicalEntryId() }
            buildList {
                vault.listAllProjects().forEach { project ->
                    vault.listAttachments(project.collectionId, null)
                        .filterNot { it.deleted }
                        .forEach { attachment ->
                            val plaintext = vault.readAttachmentContent(
                                attachmentId = attachment.attachmentId,
                                maxPlaintextBytes = MAX_ATTACHMENT_BYTES.toULong()
                            )
                            try {
                                val encrypted = attachmentStorage.writeEncrypted(plaintext.inputStream())
                                val encryptedFile = attachmentStorage.absolutePathOf(encrypted.relativePath)
                                var conversionFailure: Throwable? = null
                                try {
                                    val localWrappedCek = attachmentKeyVault.wrap(encrypted.cek)
                                    val portableCek = MdbxAttachmentCekPayload.fromLocalWrappedCek(
                                        wrappedCek = localWrappedCek,
                                        unwrapToBase64 = securityManager::decryptData
                                    )
                                    val blob = encryptedFile.readBytes()
                                    add(
                                        MdbxStoredAttachment(
                                            attachmentId = attachment.attachmentId,
                                            projectId = attachment.projectId,
                                            entryId = attachment.entryId?.let { logicalEntryIds[it] ?: it },
                                            fileName = attachment.fileName,
                                            mimeType = attachment.mediaType ?: DEFAULT_MIME_TYPE,
                                            contentHash = attachment.contentHash,
                                            originalSize = attachment.originalSize.toLong(),
                                            storedSize = blob.size.toLong(),
                                            wrappedCek = portableCek,
                                            createdAtMillis = 0L,
                                            updatedAtMillis = 0L,
                                            deleted = false,
                                            blob = blob
                                        )
                                    )
                                } catch (error: Throwable) {
                                    conversionFailure = error
                                    throw error
                                } finally {
                                    encrypted.cek.fill(0)
                                    deleteTemporaryAttachmentFile(encryptedFile, conversionFailure)
                                }
                            } finally {
                                plaintext.fill(0)
                            }
                        }
                }
            }
        }

    override suspend fun createFolder(
        databaseId: Long,
        name: String,
        parentFolderId: String?
    ): MdbxStoredFolderEntry {
        val title = name.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Folder name cannot be empty")
        check(parentFolderId.isNullOrBlank() || parentFolderId.equals("root", ignoreCase = true)) {
            "Nested MDBX2 folders are not supported yet"
        }
        return sessions.withVault(databaseId) { _, vault ->
            val folderId = UUID.randomUUID().toString()
            vault.executeWriteOperation(
                operationId = UUID.randomUUID().toString(),
                operationKind = "monica-create-folder",
                commands = listOf(MdbxWriteCommand.CreateProject(folderId, title))
            )
            MdbxStoredFolderEntry(
                folderId = folderId,
                parentFolderId = null,
                name = title,
                pathKey = title.lowercase(),
                objectClock = System.currentTimeMillis()
            )
        }
    }

    override suspend fun listFolders(databaseId: Long): List<MdbxStoredFolderEntry> =
        sessions.withVault(databaseId) { _, vault ->
            val rootId = Mdbx2VaultSessionExecutor.rootProjectId(vault.info().vaultId)
            vault.listAllProjects()
                .asSequence()
                .filterNot { it.collectionId == rootId }
                .map { project ->
                    MdbxStoredFolderEntry(
                        folderId = project.collectionId,
                        parentFolderId = null,
                        name = project.title,
                        pathKey = project.title.lowercase(),
                        objectClock = project.updatedAt.hashCode().toLong()
                    )
                }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

    internal suspend fun createMigrationFolders(
        databaseId: Long,
        folders: List<MdbxMigrationFolderPlan>
    ): Map<String, String> = sessions.withVault(databaseId) { _, vault ->
        val vaultId = vault.info().vaultId
        val mapping = folders.associate { folder ->
            folder.sourceFolderId to UUID.nameUUIDFromBytes(
                "monica-migration-folder:$vaultId:${folder.sourceFolderId}".toByteArray(Charsets.UTF_8)
            ).toString()
        }
        folders.chunked(MIGRATION_BATCH_SIZE).forEach { batch ->
            val commands = batch.mapNotNull { folder ->
                val targetFolderId = mapping.getValue(folder.sourceFolderId)
                if (vault.getCollectionSummary(targetFolderId) == null) {
                    MdbxWriteCommand.CreateProject(targetFolderId, folder.targetDisplayName)
                } else {
                    null
                }
            }
            if (commands.isNotEmpty()) {
                vault.executeWriteOperation(
                    operationId = UUID.randomUUID().toString(),
                    operationKind = "monica-migration-folders",
                    commands = commands
                )
            }
        }
        mapping
    }

    internal suspend fun importMigrationEntries(
        databaseId: Long,
        entries: List<MdbxMigrationEntryPlan>,
        targetFolderIds: Map<String, String>
    ) {
        entries.chunked(MIGRATION_BATCH_SIZE).forEach { batch ->
            val mutations = batch.map { plan ->
                val targetFolderId = plan.sourceFolderId?.let(targetFolderIds::get)
                val rewritten = MdbxMigrationEntryMapper.rewrite(plan, targetFolderId)
                EntryMutation(
                    databaseId = databaseId,
                    folderId = targetFolderId,
                    entryId = rewritten.entryId,
                    entryType = rewritten.entryType,
                    title = rewritten.title,
                    payloadJson = rewritten.payloadJson,
                    deleted = rewritten.deleted
                )
            }
            upsertMutations(mutations)
        }
    }

    internal suspend fun importMigrationAttachments(
        databaseId: Long,
        attachments: List<MdbxMigrationAttachmentPlan>,
        onImported: (Int, Int) -> Unit = { _, _ -> }
    ) {
        attachments.forEachIndexed { index, plan ->
            val plaintext = readPortableAttachmentPlaintext(plan.attachment)
            try {
                val expectedHash = plan.attachment.contentHash.trim()
                if (expectedHash.isNotEmpty()) {
                    check(sha256Hex(plaintext).equals(expectedHash, ignoreCase = true)) {
                        "Source attachment content hash does not match its metadata"
                    }
                }
                sessions.withVault(databaseId) { _, vault ->
                    val vaultId = vault.info().vaultId
                    val physicalParentEntryId = mdbx2PhysicalEntryId(vaultId, plan.parentEntryId)
                    val parent = vault.getObjectSummary(physicalParentEntryId)
                        ?: error("MDBX2 migration attachment parent is missing")
                    val attachmentId = mdbx2PhysicalAttachmentId(vaultId, plan.attachment.attachmentId)
                    val existing = vault.getAttachment(attachmentId)
                    if (existing == null) {
                        vault.createAttachmentWithExternalContent(
                            operationId = UUID.randomUUID().toString(),
                            request = MdbxAttachmentCreateRequest(
                                attachmentId = attachmentId,
                                projectId = parent.collectionId,
                                entryId = physicalParentEntryId,
                                fileName = plan.attachment.fileName,
                                mediaType = plan.attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE }
                            ),
                            content = plaintext,
                            limits = attachmentLimits()
                        )
                    } else {
                        if (
                            existing.fileName != plan.attachment.fileName ||
                            existing.mediaType != plan.attachment.mimeType
                        ) {
                            vault.renameAttachment(
                                attachmentId = attachmentId,
                                fileName = plan.attachment.fileName,
                                mediaType = plan.attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE }
                            )
                        }
                        vault.replaceAttachmentExternalContent(
                            operationId = UUID.randomUUID().toString(),
                            attachmentId = attachmentId,
                            content = plaintext,
                            limits = attachmentLimits()
                        )
                    }
                }
            } finally {
                plaintext.fill(0)
            }
            onImported(index + 1, attachments.size)
        }
    }

    internal suspend fun verifyMigration(
        databaseId: Long,
        plan: MdbxMigrationPlan,
        targetFolderIds: Map<String, String>
    ): MdbxMigrationVerification {
        val folderErrors = MdbxMigrationVerifier.folderErrors(
            plan = plan,
            targetFolderIds = targetFolderIds,
            actualFolders = listFolders(databaseId)
        )
        check(folderErrors.isEmpty()) { folderErrors.joinToString() }

        val entryErrors = MdbxMigrationVerifier.entryErrors(
            plan = plan,
            targetFolderIds = targetFolderIds,
            actualEntries = readStoredEntries(databaseId)
        )
        check(entryErrors.isEmpty()) { entryErrors.joinToString() }

        val expectedAttachments = plan.attachments.map { attachmentPlan ->
            val plaintext = readPortableAttachmentPlaintext(attachmentPlan.attachment)
            try {
                AttachmentFingerprint(
                    parentEntryId = attachmentPlan.parentEntryId,
                    fileName = attachmentPlan.attachment.fileName,
                    mimeType = attachmentPlan.attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE },
                    size = plaintext.size.toLong(),
                    sha256 = sha256Hex(plaintext)
                )
            } finally {
                plaintext.fill(0)
            }
        }.sortedWith(attachmentFingerprintComparator)
        val actualAttachments = readStoredAttachments(databaseId).map { attachment ->
            val plaintext = readPortableAttachmentPlaintext(attachment)
            try {
                AttachmentFingerprint(
                    parentEntryId = attachment.entryId ?: attachment.projectId,
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE },
                    size = plaintext.size.toLong(),
                    sha256 = sha256Hex(plaintext)
                )
            } finally {
                plaintext.fill(0)
                attachment.blob.fill(0)
            }
        }.sortedWith(attachmentFingerprintComparator)
        check(expectedAttachments == actualAttachments) { "Migrated attachment content does not match the source" }

        return MdbxMigrationVerification(
            folderCount = plan.folders.size,
            entryCount = plan.entries.size,
            attachmentCount = plan.attachments.size,
            attachmentBytes = plan.attachmentBytes
        )
    }

    override suspend fun upsertPassword(entry: PasswordEntry) {
        passwordMutation(entry)?.let { upsertMutations(listOf(it)) }
    }

    override suspend fun upsertPasswords(entries: List<PasswordEntry>) {
        upsertMutations(entries.mapNotNull { passwordMutation(it) })
    }

    override suspend fun deletePassword(entry: PasswordEntry) {
        entry.mdbxDatabaseId?.let { deleteEntries(it, listOf(passwordObjectId(entry))) }
    }

    override suspend fun deletePasswords(entries: List<PasswordEntry>) {
        entries.groupBy { it.mdbxDatabaseId }.forEach { (databaseId, values) ->
            if (databaseId != null) deleteEntries(databaseId, values.map(::passwordObjectId))
        }
    }

    override suspend fun upsertSecureItem(item: SecureItem) {
        secureItemMutation(item)?.let { upsertMutations(listOf(it)) }
    }

    override suspend fun upsertSecureItems(items: List<SecureItem>) {
        upsertMutations(items.mapNotNull { secureItemMutation(it) })
    }

    override suspend fun deleteSecureItem(item: SecureItem) {
        item.mdbxDatabaseId?.let { deleteEntries(it, listOf(secureItemObjectId(item))) }
    }

    override suspend fun deleteSecureItems(items: List<SecureItem>) {
        items.groupBy { it.mdbxDatabaseId }.forEach { (databaseId, values) ->
            if (databaseId != null) deleteEntries(databaseId, values.map(::secureItemObjectId))
        }
    }

    override suspend fun upsertPasskey(passkey: PasskeyEntry) {
        passkeyMutation(passkey)?.let { upsertMutations(listOf(it)) }
    }

    override suspend fun upsertPasskeys(passkeys: List<PasskeyEntry>) {
        upsertMutations(passkeys.mapNotNull { passkeyMutation(it) })
    }

    override suspend fun deletePasskey(passkey: PasskeyEntry) {
        passkey.mdbxDatabaseId?.let { deleteEntries(it, listOf(passkeyObjectId(passkey))) }
    }

    override suspend fun deletePasskeys(passkeys: List<PasskeyEntry>) {
        passkeys.groupBy { it.mdbxDatabaseId }.forEach { (databaseId, values) ->
            if (databaseId != null) deleteEntries(databaseId, values.map(::passkeyObjectId))
        }
    }

    override suspend fun listSteamMaFileEntries(databaseId: Long): List<MdbxStoredVaultEntry> =
        readStoredEntries(databaseId).filter { entry ->
            !entry.deleted && entry.entryType.equals(STEAM_MAFILE_ENTRY_TYPE, ignoreCase = true)
        }

    override suspend fun upsertSteamMaFileEntry(
        databaseId: Long,
        entryId: String?,
        title: String,
        maFileJson: String
    ): String {
        val resolvedEntryId = entryId?.takeIf { it.isNotBlank() }
            ?: steamMaFileObjectId(maFileJson)
        val payload = JSONObject()
            .put("kind", "steam_mafile")
            .put("monica_entry_id", resolvedEntryId)
            .put("steamid", steamField(maFileJson, "steamid", "SteamID").orEmpty())
            .put("account_name", steamField(maFileJson, "account_name", "accountName", "AccountName").orEmpty())
            .put("mafile_json", maFileJson)
        upsertMutations(
            listOf(
                EntryMutation(
                    databaseId = databaseId,
                    folderId = null,
                    entryId = resolvedEntryId,
                    entryType = STEAM_MAFILE_ENTRY_TYPE,
                    title = title,
                    payloadJson = payload.toString(),
                    deleted = false
                )
            )
        )
        return resolvedEntryId
    }

    override suspend fun deleteSteamMaFileEntry(databaseId: Long, entryId: String) {
        if (entryId.isNotBlank()) deleteEntries(databaseId, listOf(entryId))
    }

    override suspend fun getVaultDiagnostics(databaseId: Long): MdbxVaultDiagnostics =
        sessions.withVault(databaseId) { database, vault ->
            val file = File(database.resolvedActiveFilePath())
            val projects = vault.listAllProjects()
            val entries = projects.flatMap { vault.listEntries(it.collectionId, null) }
            val deletedEntries = projects.flatMap { vault.listDeletedEntries(it.collectionId, null) }
            val attachments = projects.flatMap { vault.listAttachments(it.collectionId, null) }
            MdbxVaultDiagnostics(
                databaseId = databaseId,
                filePath = file.absolutePath,
                fileExists = file.isFile,
                fileSizeBytes = file.takeIf(File::isFile)?.length() ?: 0L,
                isReadable = true,
                currentDeviceId = vault.info().deviceId,
                formatVersion = "MDBX2",
                releaseLabel = "Rust MDBX2",
                capabilityFlags = "local-crud,attachments",
                defaultTigaMode = database.tigaMode,
                integrityOk = true,
                integrityMessage = "Vault opened successfully",
                folderCount = projects.count { it.title != Mdbx2VaultSessionExecutor.ROOT_PROJECT_TITLE },
                indexedObjectCount = entries.size + deletedEntries.size,
                entryCount = entries.size,
                deletedEntryCount = deletedEntries.size,
                attachmentCount = attachments.count { !it.deleted },
                originalAttachmentBytes = attachments.sumOf { it.originalSize.toLong() },
                storedAttachmentBytes = attachments.sumOf { it.storedSize.toLong() },
                lastSyncStatus = database.lastSyncStatus,
                lastSyncError = database.lastSyncError
            )
        }

    override suspend fun getPendingSyncCount(databaseId: Long): Int = 0

    override suspend fun setProjectTags(databaseId: Long, projectId: String, tags: List<String>) {
        unsupported<Unit>("Project tags")
    }

    override suspend fun listProjectTags(databaseId: Long, projectId: String): List<String> = emptyList()

    override suspend fun listAllProjectTags(databaseId: Long): List<MdbxProjectTagSummary> = emptyList()

    override suspend fun searchProjects(
        databaseId: Long,
        query: String,
        requiredTags: List<String>
    ): List<MdbxProjectSearchResult> = sessions.withVault(databaseId) { _, vault ->
        if (requiredTags.isNotEmpty()) return@withVault emptyList()
        val normalized = query.trim()
        vault.listAllProjects()
            .filterNot { it.title == Mdbx2VaultSessionExecutor.ROOT_PROJECT_TITLE }
            .filter { normalized.isBlank() || it.title.contains(normalized, ignoreCase = true) }
            .map { project ->
                MdbxProjectSearchResult(
                    projectId = project.collectionId,
                    title = project.title,
                    parentFolderId = null,
                    entryTypes = vault.listEntries(project.collectionId, null)
                        .map { it.entryType }
                        .distinct(),
                    tags = emptyList(),
                    updatedAt = project.updatedAt
                )
            }
    }

    override suspend fun listDeltaHistory(databaseId: Long): List<MdbxDeltaSummary> = emptyList()
    override suspend fun listCommitDiff(databaseId: Long, commitId: String): List<MdbxCommitDiff> = emptyList()
    override suspend fun revertCommit(databaseId: Long, commitId: String): Int = unsupported("Commit revert")
    override suspend fun listSnapshots(databaseId: Long): List<MdbxSnapshotSummary> = emptyList()

    override suspend fun createSnapshot(
        databaseId: Long,
        name: String,
        fullSnapshot: Boolean,
        autoPrune: Boolean
    ): MdbxSnapshotSummary = unsupported("Snapshots")

    override suspend fun deleteSnapshot(databaseId: Long, snapshotId: String) {
        unsupported<Unit>("Snapshots")
    }

    override suspend fun revertToSnapshot(databaseId: Long, snapshotId: String): Int =
        unsupported("Snapshots")

    override suspend fun getSnapshotStructurePreview(
        databaseId: Long,
        snapshotId: String
    ): MdbxStructurePreview = unsupported("Snapshots")

    override suspend fun exportSyncBundle(databaseId: Long, baseCommitId: String?): MdbxSyncBundle =
        unsupported("Sync bundles")

    override suspend fun importSyncBundle(databaseId: Long, bundle: MdbxSyncBundle): MdbxApplyResult =
        unsupported("Sync bundles")

    override suspend fun flushPendingWorkingCopy(databaseId: Long) = Unit
    override suspend fun flushWorkingCopy(databaseId: Long) = Unit
    override suspend fun listConflicts(databaseId: Long): List<MdbxConflictSummary> = emptyList()

    override suspend fun resolveConflict(
        databaseId: Long,
        conflictId: String,
        resolution: MdbxConflictResolution
    ) {
        unsupported<Unit>("Conflict resolution")
    }

    override suspend fun upsertAttachment(
        databaseId: Long,
        parentEntryId: String,
        attachment: Attachment
    ): Unit = withContext(Dispatchers.IO) {
        require(attachment.sizeBytes in 0..MAX_ATTACHMENT_BYTES) {
            "MDBX2 attachment exceeds ${MAX_ATTACHMENT_BYTES / (1024 * 1024)} MiB"
        }
        val localPath = attachment.localPath?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Attachment has no local content")
        val wrappedCek = attachment.wrappedCek?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Attachment has no local content key")
        val cek = attachmentKeyVault.unwrap(wrappedCek)
        val plaintext = try {
            attachmentStorage.openDecryptedStream(localPath, cek).use { stream ->
                stream.readBytesBounded(MAX_ATTACHMENT_BYTES)
            }
        } finally {
            cek.fill(0)
        }
        sessions.withVault(databaseId) { _, vault ->
            val vaultId = vault.info().vaultId
            val physicalParentEntryId = mdbx2PhysicalEntryId(vaultId, parentEntryId)
            val parent = vault.getObjectSummary(physicalParentEntryId)
                ?: error("MDBX2 parent entry not found: $parentEntryId")
            val logicalAttachmentId = attachmentObjectId(parentEntryId, attachment)
            val attachmentId = mdbx2PhysicalAttachmentId(vaultId, logicalAttachmentId)
            val existing = vault.getAttachment(attachmentId)
            if (existing == null) {
                vault.createAttachmentWithExternalContent(
                    operationId = UUID.randomUUID().toString(),
                    request = MdbxAttachmentCreateRequest(
                        attachmentId = attachmentId,
                        projectId = parent.collectionId,
                        entryId = physicalParentEntryId,
                        fileName = attachment.fileName,
                        mediaType = attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE }
                    ),
                    content = plaintext,
                    limits = attachmentLimits()
                )
            } else {
                if (existing.fileName != attachment.fileName || existing.mediaType != attachment.mimeType) {
                    vault.renameAttachment(
                        attachmentId = attachmentId,
                        fileName = attachment.fileName,
                        mediaType = attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE }
                    )
                }
                vault.replaceAttachmentExternalContent(
                    operationId = UUID.randomUUID().toString(),
                    attachmentId = attachmentId,
                    content = plaintext,
                    limits = attachmentLimits()
                )
            }
        }
        Unit
    }

    override suspend fun upsertExternalAttachmentRef(
        databaseId: Long,
        parentEntryId: String,
        attachment: Attachment,
        externalUri: String
    ) {
        upsertAttachment(databaseId, parentEntryId, attachment)
    }

    override suspend fun deleteAttachment(
        databaseId: Long,
        parentEntryId: String,
        attachment: Attachment
    ) {
        sessions.withVault(databaseId) { _, vault ->
            val logicalAttachmentId = attachmentObjectId(parentEntryId, attachment)
            val attachmentId = mdbx2PhysicalAttachmentId(vault.info().vaultId, logicalAttachmentId)
            if (vault.getAttachment(attachmentId) != null) {
                vault.deleteAttachment(attachmentId)
            }
        }
    }

    private suspend fun upsertMutations(mutations: List<EntryMutation>) {
        mutations.groupBy { it.databaseId }.forEach { (databaseId, grouped) ->
            sessions.withVault(databaseId) { _, vault ->
                val vaultId = vault.info().vaultId
                val rootProjectId = Mdbx2VaultSessionExecutor.rootProjectId(vaultId)
                val commands = grouped.flatMap { mutation ->
                    val physicalEntryId = mdbx2PhysicalEntryId(vaultId, mutation.entryId)
                    val desiredProjectId = mutation.folderId
                        ?.takeIf { it.isNotBlank() && vault.getCollectionSummary(it)?.deleted == false }
                        ?: rootProjectId
                    val current = vault.getObjectSummary(physicalEntryId)
                    buildList {
                        if (current == null) {
                            add(
                                MdbxWriteCommand.CreateEntry(
                                    entryId = physicalEntryId,
                                    projectId = desiredProjectId,
                                    entryType = mutation.entryType,
                                    title = mutation.title,
                                    payloadJson = mutation.payloadJson
                                )
                            )
                        } else {
                            if (current.deleted) {
                                add(MdbxWriteCommand.RestoreEntry(physicalEntryId, current.collectionId))
                            }
                            if (current.collectionId != desiredProjectId) {
                                add(
                                    MdbxWriteCommand.MoveEntry(
                                        entryId = physicalEntryId,
                                        projectId = current.collectionId,
                                        targetProjectId = desiredProjectId
                                    )
                                )
                            }
                            add(
                                MdbxWriteCommand.UpdateEntry(
                                    entryId = physicalEntryId,
                                    projectId = desiredProjectId,
                                    entryType = mutation.entryType,
                                    title = mutation.title,
                                    payloadJson = mutation.payloadJson
                                )
                            )
                        }
                        if (mutation.deleted) {
                            add(MdbxWriteCommand.DeleteEntry(physicalEntryId, desiredProjectId))
                        }
                    }
                }
                if (commands.isNotEmpty()) {
                    vault.executeWriteOperation(
                        operationId = UUID.randomUUID().toString(),
                        operationKind = "monica-upsert-entries",
                        commands = commands
                    )
                }
            }
        }
    }

    private suspend fun deleteEntries(databaseId: Long, entryIds: List<String>) {
        sessions.withVault(databaseId) { _, vault ->
            val vaultId = vault.info().vaultId
            val commands = entryIds.distinct().mapNotNull { entryId ->
                val physicalEntryId = mdbx2PhysicalEntryId(vaultId, entryId)
                vault.getObjectSummary(physicalEntryId)
                    ?.takeUnless { it.deleted }
                    ?.let { summary -> MdbxWriteCommand.DeleteEntry(physicalEntryId, summary.collectionId) }
            }
            if (commands.isNotEmpty()) {
                vault.executeWriteOperation(
                    operationId = UUID.randomUUID().toString(),
                    operationKind = "monica-delete-entries",
                    commands = commands
                )
            }
        }
    }

    private suspend fun passwordMutation(entry: PasswordEntry): EntryMutation? {
        val databaseId = entry.mdbxDatabaseId ?: return null
        val entryId = passwordObjectId(entry)
        val payload = JSONObject()
            .put("kind", "password")
            .put("monica_entry_id", entryId)
            .put("room_id", entry.id)
            .put("website", entry.website)
            .put("username", entry.username)
            .put("app_package_name", entry.appPackageName)
            .put("app_name", entry.appName)
            .put("password_plain", decryptSensitiveValue(entry.password, "password", entry.id))
            .put("notes", entry.notes)
            .put("category_id", entry.categoryId)
            .put("mdbx_folder_id", entry.mdbxFolderId)
            .put("bound_note_room_id", entry.boundNoteId)
            .put("bound_note_entry_id", resolveBoundNoteEntryId(entry))
            .put("login_type", entry.loginType)
            .put("authenticator_key", decryptSensitiveValue(entry.authenticatorKey, "authenticator_key", entry.id))
            .put("passkey_bindings", entry.passkeyBindings)
            .put("custom_fields", passwordCustomFieldsPayload(entry.id))
            .put("bitwarden_mode", entry.bitwardenVaultId != null)
            .put("keepass_mode", entry.keepassDatabaseId != null)
        return EntryMutation(
            databaseId = databaseId,
            folderId = entry.mdbxFolderId,
            entryId = entryId,
            entryType = "login",
            title = entry.title,
            payloadJson = payload.toString(),
            deleted = entry.isDeleted
        )
    }

    private suspend fun secureItemMutation(item: SecureItem): EntryMutation? {
        val databaseId = item.mdbxDatabaseId ?: return null
        val prefix = secureItemPrefix(item)
        val entryId = secureItemObjectId(item)
        val payload = JSONObject()
            .put("kind", item.itemType.name.lowercase())
            .put("monica_entry_id", entryId)
            .put("room_id", item.id)
            .put("notes", item.notes)
            .put("item_data", decryptSensitiveValue(item.itemData, "item_data", item.id))
            .put("image_paths", item.imagePaths)
            .put("category_id", item.categoryId)
            .put("mdbx_folder_id", item.mdbxFolderId)
            .put("bound_password_entry_id", resolveBoundPasswordEntryId(item))
            .put("bitwarden_mode", item.bitwardenVaultId != null)
            .put("keepass_mode", item.keepassDatabaseId != null)
        return EntryMutation(
            databaseId = databaseId,
            folderId = item.mdbxFolderId,
            entryId = entryId,
            entryType = prefix,
            title = item.title,
            payloadJson = payload.toString(),
            deleted = item.isDeleted
        )
    }

    private fun passkeyMutation(passkey: PasskeyEntry): EntryMutation? {
        val databaseId = passkey.mdbxDatabaseId ?: return null
        val entryId = passkeyObjectId(passkey)
        val payload = JSONObject()
            .put("kind", "passkey")
            .put("monica_entry_id", entryId)
            .put("room_id", passkey.id)
            .put("credential_id", passkey.credentialId)
            .put("rp_id", passkey.rpId)
            .put("rp_name", passkey.rpName)
            .put("user_id", passkey.userId)
            .put("user_name", passkey.userName)
            .put("user_display_name", passkey.userDisplayName)
            .put("public_key_algorithm", passkey.publicKeyAlgorithm)
            .put("public_key", passkey.publicKey)
            .put("private_key_alias", PasskeyPrivateKeyStore.resolve(appContext, passkey.privateKeyAlias).orEmpty())
            .put("transports", passkey.transports)
            .put("aaguid", passkey.aaguid)
            .put("sign_count", passkey.signCount)
            .put("notes", passkey.notes)
            .put("passkey_mode", passkey.passkeyMode)
            .put("mdbx_folder_id", passkey.mdbxFolderId)
            .put("bitwarden_compatible", passkey.isBitwardenCompatible())
            .put("keepass_compatible", passkey.isKeePassCompatible())
        return EntryMutation(
            databaseId = databaseId,
            folderId = passkey.mdbxFolderId,
            entryId = entryId,
            entryType = "passkey",
            title = passkey.rpName.ifBlank { passkey.rpId },
            payloadJson = payload.toString(),
            deleted = false
        )
    }

    private suspend fun passwordCustomFieldsPayload(entryId: Long): JSONArray =
        JSONArray().also { array ->
            customFieldDao?.getFieldsByEntryIdSync(entryId).orEmpty()
                .filter { it.title.isNotBlank() }
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                .forEach { field ->
                    array.put(
                        JSONObject()
                            .put("title", field.title)
                            .put("value", field.value)
                            .put("is_protected", field.isProtected)
                            .put("sort_order", field.sortOrder)
                    )
                }
        }

    private fun decryptSensitiveValue(value: String, fieldName: String, roomId: Long): String {
        if (value.isBlank() || !securityManager.looksLikeMonicaCiphertext(value)) return value
        return runCatching { securityManager.decryptData(value) }.getOrElse { error ->
            throw IllegalStateException(
                "Cannot write encrypted $fieldName for Room item $roomId into MDBX2",
                error
            )
        }
    }

    private suspend fun resolveBoundNoteEntryId(entry: PasswordEntry): String? {
        val note = entry.boundNoteId?.let { secureItemDao?.getItemById(it) } ?: return null
        return note.takeIf { it.itemType == ItemType.NOTE }?.let(::secureItemObjectId)
    }

    private suspend fun resolveBoundPasswordEntryId(item: SecureItem): String? {
        if (item.itemType != ItemType.TOTP) return null
        val data = TotpDataResolver.parseStoredItemData(
            itemData = item.itemData,
            fallbackIssuer = item.title,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        ) ?: return null
        return data.boundPasswordId
            ?.let { passwordEntryDao?.getPasswordEntryById(it) }
            ?.let(::passwordObjectId)
    }

    private fun passwordObjectId(entry: PasswordEntry): String =
        mdbxPasswordObjectId(entry)

    private fun secureItemObjectId(item: SecureItem): String {
        val prefix = secureItemPrefix(item)
        return item.replicaGroupId?.takeIf { it.startsWith("$prefix:") } ?: "$prefix:${item.id}"
    }

    private fun secureItemPrefix(item: SecureItem): String = when (item.itemType) {
        ItemType.NOTE -> "note"
        ItemType.TOTP -> "totp"
        ItemType.BANK_CARD -> "card"
        ItemType.DOCUMENT -> "document-ref"
        ItemType.BILLING_ADDRESS -> "billing-address"
        ItemType.PAYMENT_ACCOUNT -> "payment-account"
        ItemType.PASSWORD -> "password"
    }

    private fun passkeyObjectId(passkey: PasskeyEntry): String =
        "passkey:${passkey.credentialId.ifBlank { passkey.id.toString() }}"

    private fun attachmentObjectId(parentEntryId: String, attachment: Attachment): String {
        val stableValue = listOf(
            parentEntryId,
            attachment.fileName,
            attachment.sha256Hex ?: attachment.localPath ?: attachment.id.toString(),
            attachment.createdAt.takeIf { it > 0L } ?: attachment.id
        ).joinToString("|")
        return "attachment:${sha256Hex(stableValue.toByteArray(Charsets.UTF_8)).take(32)}"
    }

    private fun steamMaFileObjectId(maFileJson: String): String {
        val steamId = steamField(maFileJson, "steamid", "SteamID")
        val account = steamField(maFileJson, "account_name", "accountName", "AccountName")
        val stable = steamId ?: account ?: sha256Hex(maFileJson.toByteArray(Charsets.UTF_8)).take(24)
        return "steam-mafile:$stable"
    }

    private fun steamField(json: String, vararg names: String): String? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return names.asSequence().map { root.optString(it).trim() }.firstOrNull { it.isNotBlank() }
    }

    private fun attachmentLimits(): MdbxAttachmentContentLimits = MdbxAttachmentContentLimits(
        chunkSize = ATTACHMENT_CHUNK_BYTES.toULong(),
        maxPlaintextBytes = MAX_ATTACHMENT_BYTES.toULong()
    )

    private fun java.io.InputStream.readBytesBounded(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(ATTACHMENT_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count <= 0) break
            total += count
            require(total <= maxBytes) { "MDBX2 attachment exceeds the supported size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private suspend fun readPortableAttachmentPlaintext(attachment: MdbxStoredAttachment): ByteArray {
        val storedCek = attachment.wrappedCek?.takeIf(String::isNotBlank)
            ?: error("MDBX migration attachment key is missing")
        val relativePath = "mdbx-migration-${UUID.randomUUID()}.enc"
        val encryptedFile = attachmentStorage.absolutePathOf(relativePath)
        encryptedFile.parentFile?.mkdirs()
        encryptedFile.writeBytes(attachment.blob)
        var cek: ByteArray? = null
        var conversionFailure: Throwable? = null
        return try {
            val localWrappedCek = MdbxAttachmentCekPayload.toLocalWrappedCek(
                storedValue = storedCek,
                wrapBase64 = securityManager::encryptData
            )
            cek = attachmentKeyVault.unwrap(localWrappedCek)
            attachmentStorage.openDecryptedStream(relativePath, cek).use { stream ->
                stream.readBytesBounded(MAX_ATTACHMENT_BYTES)
            }
        } catch (error: Throwable) {
            conversionFailure = error
            throw error
        } finally {
            cek?.fill(0)
            deleteTemporaryAttachmentFile(encryptedFile, conversionFailure)
        }
    }

    private fun deleteTemporaryAttachmentFile(file: File, originalFailure: Throwable?) {
        if (!file.exists() || file.delete()) return
        val cleanupFailure = IllegalStateException("Unable to remove a temporary attachment file")
        if (originalFailure == null) throw cleanupFailure
        originalFailure.addSuppressed(cleanupFailure)
    }

    private fun uniffi.mdbx_ffi.EntryRecord.logicalEntryId(): String =
        runCatching { JSONObject(payloadJson).optString("monica_entry_id").trim() }
            .getOrDefault("")
            .ifBlank { entryId }

    private fun uniffi.mdbx_ffi.EntryRecord.toStoredEntry(): MdbxStoredVaultEntry =
        MdbxStoredVaultEntry(logicalEntryId(), entryType, title, payloadJson, deleted)

    private fun MdbxVault.listAllProjects(): List<uniffi.mdbx_ffi.MdbxCollectionSummary> {
        val projects = mutableListOf<uniffi.mdbx_ffi.MdbxCollectionSummary>()
        var cursor: String? = null
        do {
            val page = listCollectionSummaries(COLLECTION_PAGE_SIZE, cursor)
            projects += page.items.filterNot { it.deleted }
            cursor = page.nextCursor
        } while (cursor != null)
        return projects
    }

    private fun <T> unsupported(feature: String): T =
        throw UnsupportedOperationException("$feature is not available for local MDBX2 vaults yet")

    private data class EntryMutation(
        val databaseId: Long,
        val folderId: String?,
        val entryId: String,
        val entryType: String,
        val title: String,
        val payloadJson: String,
        val deleted: Boolean
    )

    private data class AttachmentFingerprint(
        val parentEntryId: String,
        val fileName: String,
        val mimeType: String,
        val size: Long,
        val sha256: String
    )

    companion object {
        private const val MIGRATION_BATCH_SIZE = 100
        private const val STEAM_MAFILE_ENTRY_TYPE = "steam-mafile"
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private val attachmentFingerprintComparator = compareBy<AttachmentFingerprint>(
            AttachmentFingerprint::parentEntryId,
            AttachmentFingerprint::fileName,
            AttachmentFingerprint::mimeType,
            AttachmentFingerprint::size,
            AttachmentFingerprint::sha256
        )
        private const val COLLECTION_PAGE_SIZE = 200u
        private const val ATTACHMENT_CHUNK_BYTES = 256L * 1024L
        private const val ATTACHMENT_BUFFER_BYTES = 8 * 1024
        private const val MAX_ATTACHMENT_BYTES = 64L * 1024L * 1024L
    }
}
