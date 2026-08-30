package takagi.ru.monica.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveLazyListScrollbarTest {
    @Test
    fun targetIndex_mapsTrackEndsAndMidpointToScrollableRange() {
        assertEquals(0, expressiveScrollbarTargetIndex(0f, totalItems = 100, visibleItems = 10))
        assertEquals(45, expressiveScrollbarTargetIndex(0.5f, totalItems = 100, visibleItems = 10))
        assertEquals(90, expressiveScrollbarTargetIndex(1f, totalItems = 100, visibleItems = 10))
    }

    @Test
    fun targetIndex_clampsProgressAndShortLists() {
        assertEquals(0, expressiveScrollbarTargetIndex(-1f, totalItems = 1, visibleItems = 1))
        assertEquals(0, expressiveScrollbarTargetIndex(2f, totalItems = 1, visibleItems = 1))
    }

    @Test
    fun dragUsesPixelCalibratedMetricsAndSnapsTheHandleToTheFinger() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/ExpressiveLazyListScrollbar.kt"
        ).readText()

        assertTrue(source.contains("ExpressiveScrollbarAxisTracker"))
        assertTrue(source.contains("snapshotFlow { pendingTargetIndex }"))
        assertTrue(source.contains("displayedProgress.snapTo(dragProgress)"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
