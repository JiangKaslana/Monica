package takagi.ru.monica.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerPersistencePolicyTest {

    @Test
    fun neverExpireSurvivesAppProcessRestartWithinTheSameBoot() {
        assertTrue(
            isPersistedSessionWithinTimeout(
                autoLockMinutes = -1,
                unlockElapsedRealtime = 900_000L,
                nowElapsedRealtime = 910_000L,
                unlockWallTime = 1_000_000L,
                nowWallTime = 1_010_000L,
            )
        )
    }

    @Test
    fun neverExpireFailsClosedAfterDeviceRestart() {
        assertFalse(
            isPersistedSessionWithinTimeout(
                autoLockMinutes = -1,
                unlockElapsedRealtime = 900_000L,
                nowElapsedRealtime = 10_000L,
                unlockWallTime = 1_000_000L,
                nowWallTime = 2_000_000L,
            )
        )
    }

    @Test
    fun finiteSessionFailsClosedAfterDeviceRestartOrClockRollback() {
        assertFalse(
            isPersistedSessionWithinTimeout(
                autoLockMinutes = 5,
                unlockElapsedRealtime = 900_000L,
                nowElapsedRealtime = 10_000L,
                unlockWallTime = 1_000_000L,
                nowWallTime = 1_030_000L,
            )
        )
        assertFalse(
            isPersistedSessionWithinTimeout(
                autoLockMinutes = 5,
                unlockElapsedRealtime = 100_000L,
                nowElapsedRealtime = 130_000L,
                unlockWallTime = 2_000_000L,
                nowWallTime = 1_000_000L,
            )
        )
    }

    @Test
    fun finiteSessionUsesTheMoreConservativeClockDelta() {
        assertTrue(
            isPersistedSessionWithinTimeout(
                autoLockMinutes = 5,
                unlockElapsedRealtime = 100_000L,
                nowElapsedRealtime = 220_000L,
                unlockWallTime = 1_000_000L,
                nowWallTime = 1_120_000L,
            )
        )
        assertFalse(
            isPersistedSessionWithinTimeout(
                autoLockMinutes = 5,
                unlockElapsedRealtime = 100_000L,
                nowElapsedRealtime = 220_000L,
                unlockWallTime = 1_000_000L,
                nowWallTime = 1_400_000L,
            )
        )
    }
}
