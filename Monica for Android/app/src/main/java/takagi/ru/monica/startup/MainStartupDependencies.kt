package takagi.ru.monica.startup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.attachments.repository.AttachmentRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.MdbxRepository
import takagi.ru.monica.repository.MdbxRepositoryFactory
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.SettingsManager

/**
 * Dependency graph used by the main activity.
 *
 * Construction is intentionally kept off the UI thread. No plaintext secrets
 * cross this layer; cryptographic unlock state remains lazy in SecurityManager.
 */
data class MainStartupDependencies(
    val database: PasswordDatabase,
    val securityManager: SecurityManager,
    val mdbxRepository: MdbxRepository,
    val repository: PasswordRepository,
    val secureItemRepository: SecureItemRepository,
    val settingsManager: SettingsManager,
) {
    companion object {
        suspend fun create(context: Context): MainStartupDependencies = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val database = PasswordDatabase.getDatabase(appContext)
            val securityManager = SecurityManager(appContext)
            val mdbxRepository = MdbxRepositoryFactory.create(appContext, database, securityManager)
            val repository = PasswordRepository(
                database.passwordEntryDao(),
                database.categoryDao(),
                database.bitwardenFolderDao(),
                database.secureItemDao(),
                database.passkeyDao(),
                database.passwordArchiveSyncMetaDao(),
                database.passwordHistoryDao(),
                mdbxRepository = mdbxRepository,
            )
            val secureItemRepository = SecureItemRepository(
                database.secureItemDao(),
                mdbxRepository,
                securityManager::decryptDataIfMonicaCiphertext,
                AttachmentRepository(database.attachmentDao()),
            )
            MainStartupDependencies(
                database,
                securityManager,
                mdbxRepository,
                repository,
                secureItemRepository,
                SettingsManager(appContext),
            )
        }
    }
}
