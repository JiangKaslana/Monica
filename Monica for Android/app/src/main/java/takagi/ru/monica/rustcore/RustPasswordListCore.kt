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
        if (entries.isEmpty()) return emptyList()

        // The initial password screen normally has no search term. Returning the
        // existing list here deliberately avoids loading the JNI library during
        // cold start; native code is loaded only once the user actually searches.
        if (query.isBlank()) return entries
        if (!ensureLoaded()) return null

        val size = entries.size
        val titles = Array(size) { "" }
        val usernames = Array(size) { "" }
        val websites = Array(size) { "" }
        val appNames = Array(size) { "" }
        val appPackageNames = Array(size) { "" }

        // Fill all JNI columns in one traversal rather than allocating five
        // temporary List instances through map().
        for (index in entries.indices) {
            val entry = entries[index]
            titles[index] = entry.title
            usernames[index] = entry.username
            websites[index] = entry.website
            appNames[index] = entry.appName
            appPackageNames[index] = entry.appPackageName
        }

        val selectedIndices = runCatching {
            nativeFilterIndices(
                titles = titles,
                usernames = usernames,
                websites = websites,
                appNames = appNames,
                appPackageNames = appPackageNames,
                query = query,
            )
        }.getOrNull() ?: return null

        return buildList(selectedIndices.size) {
            for (index in selectedIndices) {
                if (index in entries.indices) {
                    add(entries[index])
                }
            }
        }
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
    private external fun nativeFilterIndices(
        titles: Array<String>,
        usernames: Array<String>,
        websites: Array<String>,
        appNames: Array<String>,
        appPackageNames: Array<String>,
        query: String,
    ): IntArray?
}
