package takagi.ru.monica.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MdbxDashboardDrillDownUiTest {

    @Test
    fun databaseDashboardRoutesAllFourTilesToDetails() {
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()
        val detailPageBody = managerSource
            .substringAfter("private fun MdbxVaultDetailPage(")
            .substringBefore("@Composable\nprivate fun MdbxDetailActionList(")

        assertTrue(managerSource.contains("data class Health("))
        assertTrue(managerSource.contains("data class Attachments("))
        assertTrue(detailPageBody.contains("onClick = if (supportsConflicts) onShowConflicts else null"))
        assertTrue(detailPageBody.contains("onClick = onShowHealth"))
        assertTrue(detailPageBody.contains("onClick = if (supportsHistory) onShowCommitHistory else null"))
        assertTrue(detailPageBody.contains("onClick = onShowAttachments"))
        assertTrue(managerSource.contains("onClick: (() -> Unit)? = null"))
        assertTrue(managerSource.contains("Icons.AutoMirrored.Filled.KeyboardArrowRight"))
    }

    @Test
    fun healthDetailExplainsEveryFieldBehindTheIssueCount() {
        val detailSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxDashboardDetailPages.kt"
        ).readText()

        assertTrue(detailSource.contains("internal fun MdbxHealthDetailPage("))
        assertTrue(detailSource.contains("isReadable"))
        assertTrue(detailSource.contains("integrityOk"))
        assertTrue(detailSource.contains("danglingParentCount"))
        assertTrue(detailSource.contains("danglingBranchHeadCount"))
        assertTrue(detailSource.contains("danglingDeviceHeadCount"))
        assertTrue(detailSource.contains("attachmentChunkMismatchCount"))
        assertTrue(detailSource.contains("onRefreshDiagnostics"))
    }

    @Test
    fun attachmentDetailShowsStorageAndIntegrityInformation() {
        val detailSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxDashboardDetailPages.kt"
        ).readText()

        assertTrue(detailSource.contains("internal fun MdbxAttachmentDetailPage("))
        assertTrue(detailSource.contains("diagnostics.attachmentCount"))
        assertTrue(detailSource.contains("diagnostics.externalAttachmentCount"))
        assertTrue(detailSource.contains("diagnostics.originalAttachmentBytes"))
        assertTrue(detailSource.contains("diagnostics.storedAttachmentBytes"))
        assertTrue(detailSource.contains("diagnostics.attachmentChunkMismatchCount"))
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            candidates += File(directory, relativePath)
            directory = directory.parentFile
        }
        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath")
    }
}
