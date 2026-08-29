from pathlib import Path

PATH = Path("app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import takagi.ru.monica.security.SessionManager\n",
    "import takagi.ru.monica.security.SessionManager\n"
    "import takagi.ru.monica.rustcore.RustPasswordListCore\n",
    "rust import",
)

replace_once(
    '''    init {\n        restoreLastCategoryFilter()\n        observeInvalidCustomCategoryFilter()\n        viewModelScope.launch(Dispatchers.IO) {\n            runCatching {\n                repairLegacyDetachedKeePassEntries()\n                repairLegacyOwnershipConflicts()\n            }.onFailure { error ->\n                Log.w("PasswordViewModel", "Password startup maintenance failed", error)\n            }\n        }\n        viewModelScope.launch(Dispatchers.Default) {\n            warmupBitwardenOfflineSecretCache()\n        }\n    }\n''',
    '''    init {\n        restoreLastCategoryFilter()\n        observeInvalidCustomCategoryFilter()\n    }\n''',
    "remove eager startup maintenance",
)

replace_once(
    "                val searchFlow = repository.searchPasswordEntries(query).map { baseResults ->\n",
    "                val searchFlow = rawAllPasswordsSource.map { allEntries ->\n"
    "                    val baseResults = RustPasswordListCore.filterEntries(allEntries, query)\n"
    "                        ?: allEntries.filter { matchesSearchQuery(it, query) }\n",
    "rust metadata search",
)

replace_once(
    '''                val decrypted = exactDeduped.map { entry ->\n                    entry.copy(password = inspectSecretState(entry).plainValueOrEmpty())\n                }\n                if (shouldKeepRawDisplay) {\n                    decrypted\n                } else {\n                    filterGhostEntriesForDisplay(decrypted)\n                }\n''',
    '''                // Keep the stored ciphertext intact for explicit copy/move operations, but\n                // do not decrypt every row merely to render the password list.\n                if (shouldKeepRawDisplay) {\n                    exactDeduped\n                } else {\n                    filterGhostEntriesForDisplay(exactDeduped)\n                }\n''',
    "remove bulk list decrypt",
)

replace_once(
    '''    private fun filterGhostEntriesForDisplay(entries: List<PasswordEntry>): List<PasswordEntry> {\n        if (entries.size <= 1) return entries\n\n        val groups = entries.groupBy { buildGhostGroupKey(it) }\n        val ghostIds = mutableSetOf<Long>()\n\n        groups.values.forEach { group ->\n            if (group.size <= 1) return@forEach\n            if (!group.any { it.password.isNotBlank() }) return@forEach\n\n            group.forEach { entry ->\n                val isPasswordMode = entry.loginType.equals("PASSWORD", ignoreCase = true)\n                val shouldFilterGhost = !entry.isLocalOnlyEntry() || entry.hasOwnershipConflict()\n                if (isPasswordMode && entry.password.isBlank() && shouldFilterGhost) {\n                    ghostIds += entry.id\n                }\n            }\n        }\n\n        if (ghostIds.isEmpty()) return entries\n        return entries.filterNot { it.id in ghostIds }\n    }\n''',
    '''    private fun filterGhostEntriesForDisplay(entries: List<PasswordEntry>): List<PasswordEntry> {\n        val ghostIds = findGhostDisplayIds(\n            entries = entries,\n            idOf = PasswordEntry::id,\n            groupKeyOf = ::buildGhostGroupKey,\n            isPasswordMode = { entry ->\n                entry.loginType.equals("PASSWORD", ignoreCase = true)\n            },\n            shouldFilterGhost = { entry ->\n                !entry.isLocalOnlyEntry() || entry.hasOwnershipConflict()\n            },\n            resolveSecret = { entry -> inspectSecretState(entry).plainValueOrEmpty() },\n        )\n        if (ghostIds.isEmpty()) return entries\n        return entries.filterNot { it.id in ghostIds }\n    }\n''',
    "candidate-only ghost filtering",
)

ready_anchor = '''    val allPasswordsForUi: StateFlow<List<PasswordEntry>> = sharedAllPasswordsForUiSource\n        .stateIn(\n            scope = viewModelScope,\n            started = SharingStarted.WhileSubscribed(5000),\n            initialValue = emptyList()\n        )\n'''
replace_once(
    ready_anchor,
    ready_anchor
    + '''\n    // Startup repair and offline-secret warming are important, but neither is required\n    // to produce the first password-list frame. Defer them until the primary list\n    // metadata and categories have emitted once so they cannot race initial rendering.\n    init {\n        viewModelScope.launch {\n            combine(\n                passwordEntriesReady,\n                allPasswordsForUiReady,\n                categoriesReady,\n            ) { entriesReady, lookupReady, categoryListReady ->\n                entriesReady && lookupReady && categoryListReady\n            }\n                .filter { it }\n                .first()\n\n            viewModelScope.launch(Dispatchers.IO) {\n                runCatching {\n                    repairLegacyDetachedKeePassEntries()\n                    repairLegacyOwnershipConflicts()\n                }.onFailure { error ->\n                    Log.w("PasswordViewModel", "Deferred password maintenance failed", error)\n                }\n            }\n            viewModelScope.launch(Dispatchers.Default) {\n                runCatching { warmupBitwardenOfflineSecretCache() }\n                    .onFailure { error ->\n                        Log.w("PasswordViewModel", "Deferred Bitwarden cache warmup failed", error)\n                    }\n            }\n        }\n    }\n''',
    "deferred maintenance init",
)

PATH.write_text(text, encoding="utf-8")
