package takagi.ru.monica.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal object MdbxMigrationVerifier {
    private val json = Json { ignoreUnknownKeys = true }

    fun entryErrors(
        plan: MdbxMigrationPlan,
        targetFolderIds: Map<String, String>,
        actualEntries: List<MdbxStoredVaultEntry>
    ): List<String> {
        val expected = plan.entries.associate { entryPlan ->
            val targetFolderId = entryPlan.sourceFolderId?.let(targetFolderIds::get)
            val entry = MdbxMigrationEntryMapper.rewrite(entryPlan, targetFolderId)
            entry.entryId to ComparableEntry.from(entry, json)
        }
        val actual = actualEntries.associate { entry ->
            entry.entryId to ComparableEntry.from(entry, json)
        }
        return buildList {
            (expected.keys - actual.keys).sorted().forEach { add("missing entry:$it") }
            (actual.keys - expected.keys).sorted().forEach { add("unexpected entry:$it") }
            (expected.keys intersect actual.keys).sorted().forEach { entryId ->
                if (expected[entryId] != actual[entryId]) add("entry mismatch:$entryId")
            }
        }
    }

    fun folderErrors(
        plan: MdbxMigrationPlan,
        targetFolderIds: Map<String, String>,
        actualFolders: List<MdbxStoredFolderEntry>
    ): List<String> {
        val expected = plan.folders.associate { folder ->
            targetFolderIds.getValue(folder.sourceFolderId) to folder.targetDisplayName
        }
        val actual = actualFolders.associate { it.folderId to it.name }
        return buildList {
            (expected.keys - actual.keys).sorted().forEach { add("missing folder:$it") }
            (actual.keys - expected.keys).sorted().forEach { add("unexpected folder:$it") }
            (expected.keys intersect actual.keys).sorted().forEach { folderId ->
                if (expected[folderId] != actual[folderId]) add("folder mismatch:$folderId")
            }
        }
    }

    private data class ComparableEntry(
        val type: String,
        val title: String,
        val payload: JsonElement?,
        val deleted: Boolean
    ) {
        companion object {
            fun from(entry: MdbxStoredVaultEntry, json: Json): ComparableEntry = ComparableEntry(
                type = entry.entryType,
                title = entry.title,
                payload = runCatching { json.parseToJsonElement(entry.payloadJson) }.getOrNull(),
                deleted = entry.deleted
            )
        }
    }
}
