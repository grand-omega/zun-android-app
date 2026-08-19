package dev.zun.flux.util

import dev.zun.flux.data.repo.RECENT_INPUT_CACHE_PREFIX
import dev.zun.flux.data.repo.UPLOAD_STAGED_CACHE_PREFIX
import dev.zun.flux.ui.capture.CAPTURE_CACHE_PREFIX

/**
 * Cache-file prefixes that are re-read rather than written once and forgotten. Checked before
 * [SWEEPABLE_CACHE_PREFIXES] because a protected name can also match a sweepable prefix:
 * `input_recent_` starts with [LOCAL_INPUT_CACHE_PREFIX] (`input_`), so ordering these two checks
 * the wrong way round would delete RecentInputCache's live store.
 */
private val PROTECTED_CACHE_PREFIXES = listOf(RECENT_INPUT_CACHE_PREFIX)

/**
 * Every one-shot cache-file prefix the app leaves behind. Taken from the writers themselves
 * rather than re-spelled here — the sweep previously hardcoded a single literal and silently
 * missed [UPLOAD_STAGED_CACHE_PREFIX], the very orphan its own documentation described.
 */
private val SWEEPABLE_CACHE_PREFIXES = listOf(
    UPLOAD_PREPROCESSED_CACHE_PREFIX,
    UPLOAD_STAGED_CACHE_PREFIX,
    SHARE_CACHE_PREFIX,
    REVEAL_EXPORT_CACHE_PREFIX,
    CAPTURE_CACHE_PREFIX,
    LOCAL_INPUT_CACHE_PREFIX,
)

/**
 * Whether a cacheDir entry named [name], last written at [lastModified], is a one-shot file old
 * enough (older than [cutoff]) to be safely deleted at app start.
 *
 * Composer inputs (`capture_`, `input_`, `upload_preprocessed_`) are included because
 * `HomeViewModel` holds them in a plain `MutableStateFlow` with no `SavedStateHandle` or other
 * persistence — the state referencing them cannot outlive the process, so past the cutoff they
 * are unreachable by construction.
 */
internal fun isSweepableCacheFile(name: String, lastModified: Long, cutoff: Long): Boolean {
    if (PROTECTED_CACHE_PREFIXES.any { name.startsWith(it) }) return false
    if (lastModified >= cutoff) return false
    return SWEEPABLE_CACHE_PREFIXES.any { name.startsWith(it) }
}
