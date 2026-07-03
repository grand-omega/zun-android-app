package dev.zun.flux.data.api

/**
 * Workflow names shared with the server. The server only accepts names it
 * has enabled (see zun-rust-server `ENABLED_WORKFLOWS`); `/capabilities`
 * reports what's live, and UI affordances gate on it.
 */
object Workflows {
    const val DEFAULT_EDIT = "flux2_klein_edit"
    const val TRY_HARDER_EDIT = "flux2_klein_9b_kv_edit"
}
