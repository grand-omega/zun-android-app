package dev.zun.flux.data.repo

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-pinned prompt IDs. Pinned prompts surface at the top of the prompt picker.
 *
 * Stored in plain SharedPreferences (not the encrypted bag) — IDs aren't sensitive
 * and avoiding the AES round-trip keeps the prompt sheet snappy.
 */
class PinnedPromptsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pinned_prompts", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(loadIds())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun toggle(promptId: Long) {
        val next = _ids.value.toMutableSet().apply {
            if (!add(promptId)) remove(promptId)
        }
        _ids.value = next
        prefs.edit {
            putStringSet(KEY_IDS, next.map { it.toString() }.toSet())
        }
    }

    private fun loadIds(): Set<Long> = prefs.getStringSet(KEY_IDS, emptySet())
        .orEmpty()
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    private companion object {
        const val KEY_IDS = "ids"
    }
}
