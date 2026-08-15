package takagi.ru.monica.utils

import android.content.ContentResolver
import android.net.Uri

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
