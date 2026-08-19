package dev.zun.flux.data.repo

import dev.zun.flux.data.api.PromptDto
import kotlinx.coroutines.flow.StateFlow

interface PromptRepository {
    /** List prompts from the server, updates [promptsState] as a side effect. */
    suspend fun listPrompts(): List<PromptDto>

    /** Last known prompts list (seeded by [listPrompts] / job sync). */
    val promptsState: StateFlow<List<PromptDto>>

    /** Creates a new server-side prompt and refreshes [promptsState]. Returns the new row. */
    suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto

    /** Updates an existing server-side prompt and refreshes [promptsState]. Returns the updated row. */
    suspend fun updatePrompt(promptId: Long, label: String, text: String): PromptDto

    /** Soft-deletes a server-side prompt and refreshes [promptsState]. */
    suspend fun deletePrompt(promptId: Long)

    /**
     * Rewrites a rough custom prompt into a structured one via the server's
     * self-hosted rewrite endpoint. Throws on failure (unreachable, timed
     * out, or unparsable) — callers treat that as "polishing unavailable
     * right now", never as a reason to fall back to a third-party service.
     */
    suspend fun polishPrompt(text: String): String
}
