package takagi.ru.monica.repository

import android.content.Context
import java.io.File
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.LocalMdbxDatabaseDao
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.resolvedActiveFilePath
import takagi.ru.monica.mdbx.MdbxDiagLogger
import takagi.ru.monica.security.SecurityManager
import uniffi.mdbx_ffi.MdbxVault
import uniffi.mdbx_ffi.MdbxWriteCommand
import uniffi.mdbx_ffi.createVaultWithTigaMode
import uniffi.mdbx_ffi.openVault
import uniffi.mdbx_ffi.MdbxTigaMode as RustTigaMode

internal class Mdbx2VaultSessionExecutor(
    context: Context,
    private val databaseDao: LocalMdbxDatabaseDao,
    private val securityManager: SecurityManager
) {
    private val appContext = context.applicationContext
    private val vaultLocks = ConcurrentHashMap<Long, Mutex>()

    private val deviceId: String by lazy {
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.getString(DEVICE_ID_KEY, null)?.takeIf { it.isNotBlank() }
            ?: "monica-android-${UUID.randomUUID()}".also { generated ->
                preferences.edit().putString(DEVICE_ID_KEY, generated).apply()
            }
    }

    suspend fun createInitializedVaultFile(
        tigaMode: MdbxTigaMode,
        password: String
    ): File = withContext(Dispatchers.IO) {
        val directory = File(appContext.filesDir, MDBX2_DIRECTORY).also { target ->
            check(target.exists() || target.mkdirs()) { "Cannot create MDBX2 directory" }
        }
        val file = File(directory, "${UUID.randomUUID()}.mdbx")
        val normalizedPassword = normalizePassword(password)
        val vault = try {
            createVaultWithTigaMode(
                path = file.absolutePath,
                password = normalizedPassword,
                deviceId = deviceId,
                mode = tigaMode.toRustMode()
            )
        } catch (error: Throwable) {
            runCatching { file.delete() }
            val mapped = Mdbx2ErrorMapper.createFailure(error)
            MdbxDiagLogger.append(
                "[MDBX2][create] failed kind=${mapped.kind.name} cause=${error::class.java.simpleName}"
            )
            throw mapped
        }
        try {
            val rootProjectId = rootProjectId(vault.info().vaultId)
            vault.executeWriteOperation(
                operationId = UUID.randomUUID().toString(),
                operationKind = "monica-initialize",
                commands = listOf(
                    MdbxWriteCommand.CreateProject(
                        projectId = rootProjectId,
                        title = ROOT_PROJECT_TITLE
                    )
                )
            )
        } catch (error: Throwable) {
            runCatching { file.delete() }
            val mapped = Mdbx2ErrorMapper.createFailure(error)
            MdbxDiagLogger.append(
                "[MDBX2][create] failed kind=${mapped.kind.name} cause=${error::class.java.simpleName}"
            )
            throw mapped
        } finally {
            runCatching { vault.close() }
        }
        file
    }

    suspend fun deleteOwnedVaultFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val directory = ownedVaultDirectory()
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return@withContext false
        if (candidate.parentFile != directory || candidate.extension.lowercase() != "mdbx") {
            return@withContext false
        }
        val blobSidecar = File(directory, "${candidate.name}.blobs").canonicalFile
        if (blobSidecar.parentFile != directory) return@withContext false
        val sidecarDeleted = !blobSidecar.exists() || blobSidecar.deleteRecursively()
        val vaultDeleted = !candidate.exists() || candidate.delete()
        sidecarDeleted && vaultDeleted
    }

    internal fun isOwnedVaultFile(file: File): Boolean {
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return candidate.parentFile == ownedVaultDirectory() &&
            candidate.extension.equals("mdbx", ignoreCase = true)
    }

    suspend fun <T> withVault(
        databaseId: Long,
        block: suspend (LocalMdbxDatabase, MdbxVault) -> T
    ): T = withContext(Dispatchers.IO) {
        vaultLocks.getOrPut(databaseId) { Mutex() }.withLock {
            val database = requireDatabase(databaseId)
            val file = resolveLocalFile(database)
            if (!file.isFile) throw Mdbx2ErrorMapper.fileMissing()
            val password = try {
                database.encryptedPassword
                    ?.takeIf { it.isNotBlank() }
                    ?.let(securityManager::decryptData)
                    .orEmpty()
            } catch (error: Throwable) {
                throw Mdbx2ErrorMapper.credentialUnavailable(error)
            }
            val vault = try {
                openVault(
                    path = file.absolutePath,
                    password = normalizePassword(password),
                    deviceId = deviceId
                )
            } catch (error: Throwable) {
                val mapped = Mdbx2ErrorMapper.openFailure(error)
                MdbxDiagLogger.append(
                    "[MDBX2][open] failed databaseId=$databaseId kind=${mapped.kind.name} cause=${error::class.java.simpleName}"
                )
                throw mapped
            }
            try {
                block(database, vault)
            } finally {
                vault.close()
            }
        }
    }

    private suspend fun requireDatabase(databaseId: Long): LocalMdbxDatabase {
        val database = databaseDao.getDatabaseById(databaseId)
            ?: throw Mdbx2ErrorMapper.databaseNotFound()
        if (database.engineTypeEnum != MdbxEngineType.RUST_MDBX2) {
            throw Mdbx2ErrorMapper.unsupportedSource()
        }
        if (database.sourceTypeEnum !in LOCAL_SOURCE_TYPES) {
            throw Mdbx2ErrorMapper.unsupportedSource()
        }
        return database
    }

    private fun resolveLocalFile(database: LocalMdbxDatabase): File {
        val rawPath = database.resolvedActiveFilePath().takeIf { it.isNotBlank() }
            ?: throw Mdbx2ErrorMapper.fileMissing()
        return File(rawPath).let { file ->
            if (file.isAbsolute) file else File(appContext.filesDir, rawPath)
        }
    }

    private fun ownedVaultDirectory(): File =
        File(appContext.filesDir, MDBX2_DIRECTORY).canonicalFile

    companion object {
        private const val PREFERENCES_NAME = "mdbx2_vault_sessions"
        private const val DEVICE_ID_KEY = "device_id"
        private const val MDBX2_DIRECTORY = "mdbx2"
        internal const val ROOT_PROJECT_TITLE = ".monica-root"
        private val LOCAL_SOURCE_TYPES = setOf(
            MdbxSourceType.LOCAL_INTERNAL,
            MdbxSourceType.LOCAL_EXTERNAL
        )

        internal fun rootProjectId(vaultId: String): String =
            UUID.nameUUIDFromBytes("monica-root:$vaultId".toByteArray(Charsets.UTF_8)).toString()

        internal fun normalizePassword(password: String): String =
            Normalizer.normalize(password, Normalizer.Form.NFC)
    }
}

private fun MdbxTigaMode.toRustMode(): RustTigaMode = when (this) {
    MdbxTigaMode.SKY -> RustTigaMode.SKY
    MdbxTigaMode.MULTI -> RustTigaMode.MULTI
    MdbxTigaMode.POWER -> RustTigaMode.POWER
}
