package takagi.ru.monica.rustcore

import takagi.ru.monica.data.PasswordEntry

/**
 * Single-batch JNI facade for secret-free password-list metadata.
 *
 * The native boundary deliberately excludes PasswordEntry.password and every other
 * sensitive credential value. If the native library is unavailable or rejects a
 * batch, callers get null and must keep the Kotlin/Room fallback path.
 */
object RustPasswordListCore {
    @Volatile
    private var loadAttempted = false

    @Volatile
    private var nativeAvailable = false

    private fun ensureLoaded(): Boolean {
        if (!loadAttempted) {
            synchronized(this) {
                if (!loadAttempted) {
                    nativeAvailable = runCatching {
                        System.loadLibrary("monica_rust_jni")
                        nativeSelfTest()
                    }.getOrDefault(false)
                    loadAttempted = true
                }
            }
        }
        return nativeAvailable
    }

    fun filterEntries(entries: List<PasswordEntry>, query: String): List<PasswordEntry>? {
        if (!ensureLoaded()) return null
        if (entries.isEmpty()) return emptyList()

        val selectedIds = runCatching {
            nativeFilterIds(
                ids = entries.map { it.id }.toLongArray(),
                titles = entries.map { it.title }.toTypedArray(),
                usernames = entries.map { it.username }.toTypedArray(),
                websites = entries.map { it.website }.toTypedArray(),
                appNames = entries.map { it.appName }.toTypedArray(),
                appPackageNames = entries.map { it.appPackageName }.toTypedArray(),
                query = query,
            )
        }.getOrNull() ?: return null

        val entriesById = entries.associateBy { it.id }
        return selectedIds.mapNotNull(entriesById::get)
    }

    fun diagnosticLabel(): String = if (!ensureLoaded()) {
        "Rust core unavailable"
    } else {
        runCatching { nativeVersion() }.getOrDefault("Rust core loaded")
    }

    @JvmStatic
    private external fun nativeVersion(): String

    @JvmStatic
    private external fun nativeSelfTest(): Boolean

    @JvmStatic
    private external fun nativeFilterIds(
        ids: LongArray,
        titles: Array<String>,
        usernames: Array<String>,
        websites: Array<String>,
        appNames: Array<String>,
        appPackageNames: Array<String>,
        query: String,
    ): LongArray?
}
