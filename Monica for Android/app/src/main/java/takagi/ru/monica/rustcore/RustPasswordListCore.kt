package takagi.ru.monica.rustcore

/**
 * Small runtime probe used by the Rust APK test build.
 *
 * The native library is optional so the application still starts on an ABI
 * for which this experimental test library was not packaged.
 */
class RustPasswordListCore private constructor() {
    companion object {
        private val loadResult: Result<Unit> = runCatching {
            System.loadLibrary("monica_rust_jni")
        }

        @JvmStatic
        private external fun nativeVersion(): String

        @JvmStatic
        private external fun nativeSelfTest(): Boolean

        fun diagnosticLabel(): String {
            val loadError = loadResult.exceptionOrNull()
            if (loadError != null) {
                return "Rust core unavailable: ${loadError.javaClass.simpleName}: ${loadError.message.orEmpty()}"
            }

            return runCatching {
                val version = nativeVersion()
                val selfTestPassed = nativeSelfTest()
                "Rust core loaded: $version; selfTest=$selfTestPassed"
            }.getOrElse { error ->
                "Rust core JNI error: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            }
        }
    }
}
