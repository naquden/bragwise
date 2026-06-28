package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val KEY_SEEN = "bragwise.guest.seen"
private const val KEY_ARCHIVED = "bragwise.guest.archived"

/**
 * localStorage-backed [ResultsSeenStore] for the web guest flow.
 *
 * Both sets are held in MutableStateFlow so callers observe changes
 * synchronously after each mutation. Storage format: JSON-encoded List<String>.
 */
class WebResultsSeenStore : ResultsSeenStore {

    private val json = Json

    private val _seenIds = MutableStateFlow(loadSet(KEY_SEEN))
    private val _archivedIds = MutableStateFlow(loadSet(KEY_ARCHIVED))

    override val seenIds: Flow<Set<String>> = _seenIds.asStateFlow()
    override val archivedIds: Flow<Set<String>> = _archivedIds.asStateFlow()

    override fun markSeen(challengeId: String) {
        val updated = _seenIds.value + challengeId
        persistSet(KEY_SEEN, updated)
        _seenIds.value = updated
    }

    override fun markUnseen(challengeId: String) {
        val updated = _seenIds.value - challengeId
        persistSet(KEY_SEEN, updated)
        _seenIds.value = updated
    }

    override fun archive(challengeId: String) {
        val updated = _archivedIds.value + challengeId
        persistSet(KEY_ARCHIVED, updated)
        _archivedIds.value = updated
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadSet(key: String): Set<String> {
        val raw = lsGet(key) ?: return emptySet()
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw).toSet() }.getOrDefault(emptySet())
    }

    private fun persistSet(key: String, set: Set<String>) {
        lsSet(key, json.encodeToString(ListSerializer(String.serializer()), set.toList()))
    }
}
