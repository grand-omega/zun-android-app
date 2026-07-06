package dev.zun.flux.data.repo

import dev.zun.flux.data.local.JobEntity

/**
 * Distinct recent input photo ids, newest-first, capped at [limit].
 * Re-picking the same photo uploads it again under a new server-assigned
 * inputId, so deduping by inputId alone wouldn't catch a repeat — dedupe by
 * content hash first, falling back to inputId for older jobs that predate
 * hash tracking. [entities] must already be ordered newest-first.
 */
fun dedupeRecentInputIds(entities: List<JobEntity>, limit: Int): List<Int> =
    entities.asSequence()
        .filter { it.inputId != null }
        .distinctBy { it.sourceSha256 ?: "input:${it.inputId}" }
        .mapNotNull { it.inputId }
        .take(limit)
        .toList()
