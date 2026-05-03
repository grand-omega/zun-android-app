package dev.zun.flux.util

import dev.zun.flux.data.api.PromptDto

/**
 * Resolves a human-readable label for a job whose backing prompt may be a
 * server-defined preset (by `source_prompt_id`) or free-text (`prompt_text`).
 * Falls back to a truncated snippet or a generic placeholder.
 */
fun resolvePromptLabel(
    prompts: List<PromptDto>,
    promptId: Long?,
    promptText: String?,
): String {
    if (promptId != null) {
        prompts.firstOrNull { it.id == promptId }?.let { return it.label }
        return "Prompt #$promptId"
    }
    if (!promptText.isNullOrBlank()) {
        return if (promptText.length <= 40) promptText else promptText.take(37) + "…"
    }
    return "Unknown prompt"
}
