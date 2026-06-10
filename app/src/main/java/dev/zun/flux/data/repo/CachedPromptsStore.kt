package dev.zun.flux.data.repo

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dev.zun.flux.data.api.PromptDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Last successfully fetched prompt list, persisted as JSON so the prompt
 * picker isn't empty after process death while the server is unreachable.
 *
 * Plain SharedPreferences, same trade-off as Room's promptText column:
 * prompt labels/text are user content but not secrets.
 */
class CachedPromptsStore(context: Context) {
    private val prefs = context.getSharedPreferences("cached_prompts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<PromptDto> {
        val raw = prefs.getString(KEY_PROMPTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PromptDto.serializer()), raw)
        }.getOrElse {
            Log.w(TAG, "Failed to decode cached prompts", it)
            emptyList()
        }
    }

    fun save(prompts: List<PromptDto>) {
        val raw = runCatching {
            json.encodeToString(ListSerializer(PromptDto.serializer()), prompts)
        }.getOrNull() ?: return
        prefs.edit { putString(KEY_PROMPTS, raw) }
    }

    private companion object {
        const val TAG = "CachedPromptsStore"
        const val KEY_PROMPTS = "prompts"
    }
}
