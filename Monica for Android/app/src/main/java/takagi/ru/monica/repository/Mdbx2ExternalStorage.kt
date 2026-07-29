package takagi.ru.monica.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.LocalMdbxDatabase
import uniffi.mdbx_ffi.createPortableBackup

internal data class Mdbx2ExternalDocument(
    val fileUri: Uri,
    val treeUri: Uri,
    val displayName: String
)

/**
 * Bridges MDBX2's native file requirement with Android document storage.
 *
 * Rust always reads and writes an app-owned working copy. Publication uses a
 * verified portable backup so committed WAL pages are included before the
 * selected SAF document is replaced. External attachment blobs are mirrored
 * into a sibling `<vault-name>.blobs` directory when a persisted tree URI is
 * available.
 */
internal class Mdbx2ExternalStorage(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun createDocument(
        treeUri: Uri,
        requestedName: String,
        workingCopy: File
    ): Mdbx2ExternalDocument = withContext(Dispatchers.IO) {
        val directory = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: throw IllegalArgumentException("Cannot access selected directory")
        check(directory.canWrite()) { "Selected directory is read-only" }

        val displayName = availableDisplayName(directory, requestedName.asMdbxFileName())
        val target = directory.createFile(MDBX_MIME_TYPE, displayName)
            ?: throw IllegalStateException("Failed to create external MDBX2 file")
        try {
            publishMainFile(target.uri, workingCopy)
            mirrorLocalSidecar(
                source = sidecarFor(workingCopy),
                targetDirectory = directory,
                sidecarName = "${target.name ?: displayName}$SIDECAR_SUFFIX"
            )
        } catch (error: Throwable) {
            runCatching { target.delete() }
            runCatching { directory.findFile("$displayName$SIDECAR_SUFFIX")?.delete() }
            throw error
        }
        Mdbx2ExternalDocument(
            fileUri = target.uri,
            treeUri = treeUri,
            displayName = target.name ?: displayName
        )
    }

    suspend fun publish(database: LocalMdbxDatabase, workingCopy: File) =
        withContext(Dispatchers.IO) {
            require(workingCopy.isFile) {
                "MDBX2 working copy is missing: ${workingCopy.absolutePath}"
            }
            val targetUri = Uri.parse(database.filePath)
            publishMainFile(targetUri, workingCopy)

            database.externalTreeUri
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?.let { treeUri ->
                    val directory = DocumentFile.fromTreeUri(appContext, treeUri)
                        ?: throw IllegalStateException("External MDBX2 directory permission is unavailable")
                    val targetName = queryDisplayName(targetUri)
                        ?: database.name.asMdbxFileName()
                    mirrorLocalSidecar(
                        source = sidecarFor(workingCopy),
                        targetDirectory = directory,
                        sidecarName = "$targetName$SIDECAR_SUFFIX"
                    )
                }
        }

    suspend fun copyDocumentToOwnedFile(
        sourceUri: Uri,
        targetFile: File,
        sourceTreeUri: Uri? = null
    ) = withContext(Dispatchers.IO) {
        check(!targetFile.exists()) { "MDBX2 import target already exists" }
        targetFile.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) {
                "Cannot create MDBX2 working-copy directory"
            }
        }
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("Unable to read selected MDBX2 file")
            check(targetFile.isFile && targetFile.length() > 0L) {
                "Selected MDBX2 file is empty"
            }
            sourceTreeUri?.let { treeUri ->
                val directory = DocumentFile.fromTreeUri(appContext, treeUri)
                    ?: throw IllegalArgumentException("Cannot access selected MDBX2 directory")
                val sourceName = queryDisplayName(sourceUri)
                    ?: throw IllegalArgumentException("Cannot determine selected MDBX2 file name")
                directory.findFile("$sourceName$SIDECAR_SUFFIX")
                    ?.takeIf(DocumentFile::isDirectory)
                    ?.let { sourceSidecar ->
                        copyDocumentDirectoryToFile(sourceSidecar, sidecarFor(targetFile))
                    }
            }
        } catch (error: Throwable) {
            runCatching { targetFile.delete() }
            runCatching { sidecarFor(targetFile).deleteRecursively() }
            throw error
        }
    }

    suspend fun replaceWorkingCopyFromDocument(
        sourceUri: Uri,
        sourceTreeUri: Uri?,
        workingCopy: File,
        validate: (File) -> Unit
    ) = withContext(Dispatchers.IO) {
        val staged = File(
            workingCopy.parentFile,
            ".${workingCopy.nameWithoutExtension}-${UUID.randomUUID()}.mdbx"
        )
        copyDocumentToOwnedFile(sourceUri, staged, sourceTreeUri)
        try {
            validate(staged)
            replaceFile(staged, workingCopy)
            val stagedSidecar = sidecarFor(staged)
            if (stagedSidecar.exists()) {
                val targetSidecar = sidecarFor(workingCopy)
                targetSidecar.deleteRecursively()
                check(stagedSidecar.renameTo(targetSidecar)) {
                    "Unable to activate refreshed MDBX2 attachment directory"
                }
            } else {
                // A source without a sidecar is authoritative.  Do not leave
                // stale local attachment blobs from an older revision attached
                // to the newly refreshed vault.
                sidecarFor(workingCopy).deleteRecursively()
            }
        } finally {
            staged.delete()
            sidecarFor(staged).deleteRecursively()
        }
    }

    suspend fun deleteCreatedDocument(document: Mdbx2ExternalDocument) =
        withContext(Dispatchers.IO) {
            runCatching { DocumentFile.fromSingleUri(appContext, document.fileUri)?.delete() }
            runCatching {
                DocumentFile.fromTreeUri(appContext, document.treeUri)
                    ?.findFile("${document.displayName}$SIDECAR_SUFFIX")
                    ?.delete()
            }
        }

    private fun publishMainFile(targetUri: Uri, workingCopy: File) {
        val backupDirectory = File(appContext.cacheDir, "mdbx2-external-publish").also { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "Cannot create MDBX2 publication directory"
            }
        }
        val portableBackup = File(backupDirectory, "${UUID.randomUUID()}.mdbx")
        try {
            createPortableBackup(
                sourcePath = workingCopy.absolutePath,
                destination = portableBackup.absolutePath
            )
            val expectedLength = portableBackup.length()
            val expectedDigest = portableBackup.inputStream().buffered().use(::sha256)
            resolver.openOutputStream(targetUri, "rwt")?.use { output ->
                portableBackup.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot open external MDBX2 file for writing")
            val actualLength = queryLength(targetUri)
            check(actualLength == null || actualLength == expectedLength) {
                "External MDBX2 file length verification failed"
            }
            val actualDigest = resolver.openInputStream(targetUri)?.buffered()?.use(::sha256)
                ?: throw IllegalStateException("Cannot verify external MDBX2 file")
            check(MessageDigest.isEqual(expectedDigest, actualDigest)) {
                "External MDBX2 file digest verification failed"
            }
        } finally {
            portableBackup.delete()
        }
    }

    private fun mirrorLocalSidecar(
        source: File,
        targetDirectory: DocumentFile,
        sidecarName: String
    ) {
        val existing = targetDirectory.findFile(sidecarName)
        if (!source.isDirectory || source.listFiles().isNullOrEmpty()) {
            existing?.delete()
            return
        }
        val target = when {
            existing == null -> targetDirectory.createDirectory(sidecarName)
            existing.isDirectory -> existing
            else -> {
                check(existing.delete()) { "Cannot replace external MDBX2 sidecar" }
                targetDirectory.createDirectory(sidecarName)
            }
        } ?: throw IllegalStateException("Cannot create external MDBX2 sidecar")
        mirrorLocalDirectory(source, target)
    }

    private fun mirrorLocalDirectory(source: File, target: DocumentFile) {
        val localChildren = source.listFiles().orEmpty().associateBy(File::getName)
        target.listFiles().forEach { remote ->
            val name = remote.name
            if (name == null || name !in localChildren) {
                check(remote.delete()) { "Cannot remove stale external MDBX2 blob" }
            }
        }
        localChildren.values.forEach { local ->
            if (local.isDirectory) {
                val current = target.findFile(local.name)
                val child = when {
                    current == null -> target.createDirectory(local.name)
                    current.isDirectory -> current
                    else -> {
                        check(current.delete()) { "Cannot replace external MDBX2 blob directory" }
                        target.createDirectory(local.name)
                    }
                } ?: throw IllegalStateException("Cannot create external MDBX2 blob directory")
                mirrorLocalDirectory(local, child)
            } else {
                val current = target.findFile(local.name)
                val child = when {
                    current == null -> target.createFile(BLOB_MIME_TYPE, local.name)
                    current.isFile -> current
                    else -> {
                        check(current.delete()) { "Cannot replace external MDBX2 blob file" }
                        target.createFile(BLOB_MIME_TYPE, local.name)
                    }
                } ?: throw IllegalStateException("Cannot create external MDBX2 blob file")
                copyAndVerify(local, child.uri)
            }
        }
    }

    private fun copyDocumentDirectoryToFile(source: DocumentFile, target: File) {
        check(target.exists() || target.mkdirs()) { "Cannot create MDBX2 sidecar working copy" }
        source.listFiles().forEach { child ->
            val name = child.name?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("External MDBX2 sidecar contains an unnamed item")
            val local = File(target, name)
            check(local.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                "External MDBX2 sidecar contains an invalid path"
            }
            when {
                child.isDirectory -> copyDocumentDirectoryToFile(child, local)
                child.isFile -> resolver.openInputStream(child.uri)?.use { input ->
                    local.outputStream().buffered().use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException("Cannot read external MDBX2 blob")
            }
        }
    }

    private fun copyAndVerify(source: File, targetUri: Uri) {
        val expectedDigest = source.inputStream().buffered().use(::sha256)
        resolver.openOutputStream(targetUri, "rwt")?.use { output ->
            source.inputStream().buffered().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot write external MDBX2 blob")
        val actualDigest = resolver.openInputStream(targetUri)?.buffered()?.use(::sha256)
            ?: throw IllegalStateException("Cannot verify external MDBX2 blob")
        check(MessageDigest.isEqual(expectedDigest, actualDigest)) {
            "External MDBX2 blob digest verification failed"
        }
    }

    private fun replaceFile(staged: File, target: File) {
        listOf(File("${target.absolutePath}-wal"), File("${target.absolutePath}-shm")).forEach(File::delete)
        val backup = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.bak")
        if (target.exists()) {
            check(target.renameTo(backup)) { "Unable to stage current MDBX2 working copy" }
        }
        try {
            check(staged.renameTo(target)) { "Unable to activate refreshed MDBX2 working copy" }
            backup.delete()
        } catch (error: Throwable) {
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    private fun availableDisplayName(directory: DocumentFile, requestedName: String): String {
        if (directory.findFile(requestedName) == null) return requestedName
        val base = requestedName.removeSuffix(".mdbx")
        var index = 2
        while (true) {
            val candidate = "$base ($index).mdbx"
            if (directory.findFile(candidate) == null) return candidate
            index += 1
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }

    private fun queryLength(uri: Uri): Long? =
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
            }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    private fun sidecarFor(file: File): File = File("${file.absolutePath}$SIDECAR_SUFFIX")

    private fun String.asMdbxFileName(): String =
        trim().ifBlank { "Monica" }.let { value ->
            if (value.endsWith(".mdbx", ignoreCase = true)) value else "$value.mdbx"
        }

    companion object {
        private const val MDBX_MIME_TYPE = "application/octet-stream"
        private const val BLOB_MIME_TYPE = "application/octet-stream"
        private const val SIDECAR_SUFFIX = ".blobs"
        private const val COPY_BUFFER_BYTES = 128 * 1024
    }
}
