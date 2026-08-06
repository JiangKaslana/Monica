package takagi.ru.monica.ui.password

import androidx.lifecycle.ViewModel
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.ui.PasswordGroupingConfig
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordAggregateSnapshotKey(
    val displayedContentTypes: Set<PasswordPageContentType>,
    val searchQuery: String,
    val categoryFilter: CategoryFilter,
)

internal data class PasswordAggregateSnapshotSeed(
    val items: List<PasswordAggregateListItemUi>,
    val hasSnapshot: Boolean,
)

internal data class PasswordGroupingSnapshotKey(
    val sourceEntries: List<PasswordEntry>,
    val config: PasswordGroupingConfig,
)

internal data class PasswordGroupingSnapshotSeed(
    val groups: Map<String, List<PasswordEntry>>,
    val hasSnapshot: Boolean,
)

internal class PasswordAggregateRetainedState {
    private var snapshotKey: PasswordAggregateSnapshotKey? = null
    private var snapshotItems: List<PasswordAggregateListItemUi> = emptyList()
    private var groupingSnapshotKey: PasswordGroupingSnapshotKey? = null
    private var groupingSnapshotGroups: Map<String, List<PasswordEntry>> = emptyMap()
    private var generation: Long = 0L

    fun currentGeneration(): Long = generation

    fun seed(key: PasswordAggregateSnapshotKey): PasswordAggregateSnapshotSeed {
        val matches = snapshotKey == key
        return PasswordAggregateSnapshotSeed(
            items = if (matches) snapshotItems else emptyList(),
            hasSnapshot = matches,
        )
    }

    fun updateIfCurrent(
        expectedGeneration: Long,
        key: PasswordAggregateSnapshotKey,
        items: List<PasswordAggregateListItemUi>,
    ): Boolean {
        if (generation != expectedGeneration) return false
        snapshotKey = key
        snapshotItems = items
        return true
    }

    fun groupingSeed(key: PasswordGroupingSnapshotKey): PasswordGroupingSnapshotSeed {
        val matches = groupingSnapshotKey == key
        return PasswordGroupingSnapshotSeed(
            groups = if (matches) groupingSnapshotGroups else emptyMap(),
            hasSnapshot = matches,
        )
    }

    fun updateGroupingIfCurrent(
        expectedGeneration: Long,
        key: PasswordGroupingSnapshotKey,
        groups: Map<String, List<PasswordEntry>>,
    ): Boolean {
        if (generation != expectedGeneration) return false
        groupingSnapshotKey = key
        groupingSnapshotGroups = groups
        return true
    }

    fun clear() {
        generation += 1L
        snapshotKey = null
        snapshotItems = emptyList()
        groupingSnapshotKey = null
        groupingSnapshotGroups = emptyMap()
    }
}

internal class PasswordAggregateRetainedStateViewModel : ViewModel() {
    val retainedState = PasswordAggregateRetainedState()

    override fun onCleared() {
        retainedState.clear()
    }
}
