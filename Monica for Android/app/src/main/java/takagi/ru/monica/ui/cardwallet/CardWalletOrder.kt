package takagi.ru.monica.ui.cardwallet

internal fun mergeVisibleWalletOrder(
    allItemIds: List<Long>,
    reorderedVisibleItemIds: List<Long>
): List<Long> {
    if (allItemIds.isEmpty() || reorderedVisibleItemIds.isEmpty()) return allItemIds
    val visibleIds = reorderedVisibleItemIds.toSet()
    val reordered = reorderedVisibleItemIds.iterator()
    return allItemIds.map { itemId ->
        if (itemId in visibleIds && reordered.hasNext()) reordered.next() else itemId
    }
}
