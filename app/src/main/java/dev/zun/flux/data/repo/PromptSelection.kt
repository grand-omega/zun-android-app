package dev.zun.flux.data.repo

/**
 * What the user picked when submitting a job. Encodes the "exactly one of
 * promptId or promptText" invariant in the type system so callers can't
 * accidentally pass both or neither.
 */
sealed interface PromptSelection {
    /** Server-side saved prompt. The server uses the prompt's stored workflow
     *  unless an override is passed alongside on the submit call. */
    data class Saved(val promptId: Long) : PromptSelection

    /** Free-text prompt entered by the user. Workflow must be specified
     *  alongside on the submit call (the server has no default). */
    data class Custom(val text: String) : PromptSelection
}
