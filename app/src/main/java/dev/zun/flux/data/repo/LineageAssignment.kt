package dev.zun.flux.data.repo

import dev.zun.flux.data.local.JobEntity

/**
 * Which lineage group a newly-hashed job (whether by its source or result)
 * belongs to. Inherits [match]'s existing root, falls back to [match]'s own
 * id if it predates lineage tracking, or starts a new root at [newJobId].
 */
fun assignLineageRoot(newJobId: String, match: JobEntity?): String = match?.lineageRootId ?: match?.id ?: newJobId
