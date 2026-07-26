package takagi.ru.monica.security

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Returns the conservative age of a persisted session, or `null` when either
 * clock moved backwards or the persisted timestamps are incomplete.
 *
 * `elapsedRealtime` detects device reboot while wall time lets the session
 * survive a normal app-process restart. Using the larger delta fails closed if
 * the wall clock jumps forward.
 */
internal fun persistedSessionAgeMillisOrNull(
    unlockElapsedRealtime: Long,
    nowElapsedRealtime: Long,
    unlockWallTime: Long,
    nowWallTime: Long,
): Long? {
    if (unlockElapsedRealtime <= 0L || unlockWallTime <= 0L) return null
    val elapsedDelta = nowElapsedRealtime - unlockElapsedRealtime
    val wallDelta = nowWallTime - unlockWallTime
    if (elapsedDelta < 0L || wallDelta < 0L) return null
    return maxOf(elapsedDelta, wallDelta)
}

internal fun isPersistedSessionWithinTimeout(
    autoLockMinutes: Int,
    unlockElapsedRealtime: Long,
    nowElapsedRealtime: Long,
    unlockWallTime: Long,
    nowWallTime: Long,
): Boolean {
    val ageMillis = persistedSessionAgeMillisOrNull(
        unlockElapsedRealtime = unlockElapsedRealtime,
        nowElapsedRealtime = nowElapsedRealtime,
        unlockWallTime = unlockWallTime,
        nowWallTime = nowWallTime,
    ) ?: return false
    // Product semantics: "never expire" survives an app-process restart in
    // the same boot, but a device reboot or invalid clock continuity fails closed.
    if (autoLockMinutes == -1) return true
    if (autoLockMinutes <= 0) return false
    return ageMillis < autoLockMinutes.toLong() * MILLIS_PER_MINUTE
}

/**
 * 会话管理器 - 统一管理应用解锁状态。
 *
 * “永不过期”会话允许在同一次开机期间的应用进程重启后恢复，直到用户主动锁定
 * Monica；设备重启、时钟回拨或时间戳损坏时均按过期处理。
 */
object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREFS_NAME = "monica_session_state"
    private const val KEY_UNLOCKED = "unlocked"
    private const val KEY_UNLOCK_ELAPSED_TS = "unlock_timestamp"
    private const val KEY_UNLOCK_WALL_TS = "unlock_wall_timestamp"
    private const val KEY_AUTO_LOCK = "auto_lock_minutes"

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var unlockElapsedTimestamp: Long = 0L
    private var unlockWallTimestamp: Long = 0L
    private var autoLockMinutes: Int = 5
    private val processId: Int = android.os.Process.myPid()

    private var appContext: Context? = null
    private val prefs: SharedPreferences?
        get() = appContext?.applicationContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Inject the application context so the session can survive process restart. */
    fun attachAppContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun persistAll() {
        val committed = prefs?.edit()?.apply {
            putBoolean(KEY_UNLOCKED, _isUnlocked.value)
            putLong(KEY_UNLOCK_ELAPSED_TS, unlockElapsedTimestamp)
            putLong(KEY_UNLOCK_WALL_TS, unlockWallTimestamp)
            putInt(KEY_AUTO_LOCK, autoLockMinutes)
        }?.commit()
        if (committed == false) {
            android.util.Log.w(TAG, "Failed to persist session state")
        }
    }

    private fun restorePersistedState() {
        prefs?.let { stored ->
            val persistedUnlocked = stored.getBoolean(KEY_UNLOCKED, false)
            _isUnlocked.value = persistedUnlocked
            unlockElapsedTimestamp = if (persistedUnlocked) {
                stored.getLong(KEY_UNLOCK_ELAPSED_TS, 0L)
            } else {
                0L
            }
            unlockWallTimestamp = if (persistedUnlocked) {
                stored.getLong(KEY_UNLOCK_WALL_TS, 0L)
            } else {
                0L
            }
            autoLockMinutes = stored.getInt(KEY_AUTO_LOCK, autoLockMinutes)
        }
    }

    fun markUnlocked() {
        _isUnlocked.value = true
        unlockElapsedTimestamp = SystemClock.elapsedRealtime()
        unlockWallTimestamp = System.currentTimeMillis()
        persistAll()
        android.util.Log.d(
            TAG,
            "Session unlocked at elapsed=$unlockElapsedTimestamp, PID=$processId",
        )
    }

    fun markLocked(clearSecondarySession: Boolean = true) {
        _isUnlocked.value = false
        unlockElapsedTimestamp = 0L
        unlockWallTimestamp = 0L
        // Persist synchronously before clearing in-memory key material so an
        // immediate process death cannot resurrect an explicitly locked session.
        persistAll()
        SecurityManager.clearRuntimeUnlockCache()
        if (clearSecondarySession) {
            SecondarySessionManager.markLocked(clearRuntimeUnlockCache = false)
        }
        android.util.Log.d(TAG, "Session locked, PID=$processId")
    }

    fun updateAutoLockTimeout(minutes: Int) {
        autoLockMinutes = minutes
        prefs?.edit()?.putInt(KEY_AUTO_LOCK, minutes)?.commit()
        android.util.Log.d(TAG, "Auto-lock timeout updated to $minutes minutes")
    }

    fun canSkipVerification(context: Context): Boolean {
        appContext = context.applicationContext
        restorePersistedState()

        if (!_isUnlocked.value) {
            android.util.Log.d(TAG, "canSkipVerification: false (not unlocked)")
            return false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        if (!isPersistedSessionWithinTimeout(
                autoLockMinutes = autoLockMinutes,
                unlockElapsedRealtime = unlockElapsedTimestamp,
                nowElapsedRealtime = nowElapsed,
                unlockWallTime = unlockWallTimestamp,
                nowWallTime = nowWall,
            )
        ) {
            android.util.Log.d(TAG, "canSkipVerification: false (session expired or clock reset)")
            markLocked(clearSecondarySession = false)
            return false
        }

        // Do not clear the session merely because the screen is temporarily locked.
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            android.util.Log.d(TAG, "canSkipVerification: false (device locked)")
            return false
        }

        android.util.Log.d(TAG, "canSkipVerification: true (active session, screen unlocked)")
        return true
    }

    fun refreshSession() {
        if (_isUnlocked.value) {
            unlockElapsedTimestamp = SystemClock.elapsedRealtime()
            unlockWallTimestamp = System.currentTimeMillis()
            persistAll()
            android.util.Log.d(TAG, "Session refreshed at elapsed=$unlockElapsedTimestamp")
        }
    }

    fun isSessionExpired(): Boolean {
        if (!_isUnlocked.value) return true
        return !isPersistedSessionWithinTimeout(
            autoLockMinutes = autoLockMinutes,
            unlockElapsedRealtime = unlockElapsedTimestamp,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            unlockWallTime = unlockWallTimestamp,
            nowWallTime = System.currentTimeMillis(),
        )
    }

    fun getRemainingMinutes(): Int {
        if (!_isUnlocked.value) return 0
        if (autoLockMinutes == -1) return -1
        val ageMillis = persistedSessionAgeMillisOrNull(
            unlockElapsedRealtime = unlockElapsedTimestamp,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            unlockWallTime = unlockWallTimestamp,
            nowWallTime = System.currentTimeMillis(),
        ) ?: return 0
        val elapsedMinutes = ageMillis / MILLIS_PER_MINUTE
        return maxOf(0, autoLockMinutes - elapsedMinutes.toInt())
    }
}
