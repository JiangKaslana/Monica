package takagi.ru.monica.utils

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

const val KEEPASS_KEY_FILE_PERMISSION_FLAGS: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION

/** SAF access state for an externally referenced KDBX file. */
enum class KeePassUriPermissionState {
    READ_WRITE,
    READ_ONLY,
    MISSING
}

fun ContentResolver.keePassUriPermissionState(uri: Uri): KeePassUriPermissionState {
    // The provider may grant a usable descriptor without exposing a persisted
    // grant (for example immediately after the picker returns). Prefer the
    // actual capability so the UI does not remain stuck on "missing" after a
    // successful repair.
    val writable = runCatching {
        openFileDescriptor(uri, "rw")?.use { true } ?: false
    }.getOrDefault(false)
    if (writable) return KeePassUriPermissionState.READ_WRITE

    val persisted = persistedUriPermissions.firstOrNull { it.uri == uri }
    val hasRead = persisted?.isReadPermission == true
    val hasWrite = persisted?.isWritePermission == true
    if (!hasRead && !hasWrite) return KeePassUriPermissionState.MISSING
    if (!hasWrite) return KeePassUriPermissionState.READ_ONLY
    return KeePassUriPermissionState.READ_ONLY
}

/** Key files are read-only credentials. Requiring write access breaks providers that correctly grant read only. */
fun ContentResolver.persistKeePassKeyFileReadPermission(uri: Uri) {
    runCatching {
        takePersistableUriPermission(uri, KEEPASS_KEY_FILE_PERMISSION_FLAGS)
    }
}

fun ContentResolver.readKeePassKeyFileBytes(uri: Uri, unavailableMessage: String): ByteArray {
    persistKeePassKeyFileReadPermission(uri)
    return try {
        openInputStream(uri)?.use { input ->
            input.readKeePassKeyFileBytesLimited(KeePassKeyFileStore.MAX_KEY_FILE_BYTES)
        }
            ?: throw FileNotFoundException("KeePass key file is unavailable")
    } catch (error: Exception) {
        throw KeePassOperationException(
            code = KeePassErrorCode.KEY_FILE_UNAVAILABLE,
            message = if (error is IllegalArgumentException) {
                error.message ?: unavailableMessage
            } else {
                unavailableMessage
            },
            cause = error
        )
    }
}

/** Reads at most [maxBytes] without first allocating an unbounded provider stream in memory. */
internal fun InputStream.readKeePassKeyFileBytesLimited(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "密钥文件大小限制无效" }
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_KEY_FILE_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_KEY_FILE_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        require(total <= maxBytes) { "密钥文件超过 ${maxBytes / 1024} KB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private const val DEFAULT_KEY_FILE_BUFFER_SIZE = 8 * 1024
