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

    /** Soft-deletes a server-side prompt and refreshes [promptsState]. */
    suspend fun deletePrompt(promptId: Long)
}
