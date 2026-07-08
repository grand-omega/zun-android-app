package dev.zun.flux.data.repo

import androidx.work.NetworkType
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RealJobRepositoryTest {
    @Test
    fun withoutHiddenJobs_filtersHiddenItemsAndPreservesCursor() {
        val response = JobListResponse(
            items = listOf(
                job("visible-1"),
                job("hidden"),
                job("visible-2"),
            ),
            next_cursor = "cursor-2",
        )

        val filtered = response.withoutHiddenJobs(setOf("hidden", "missing"))

        assertEquals(listOf("visible-1", "visible-2"), filtered.items.map { it.id })
        assertEquals("cursor-2", filtered.next_cursor)
    }

    @Test
    fun withoutHiddenJobs_returnsSameResponseWhenThereAreNoHiddenIds() {
        val response = JobListResponse(items = listOf(job("job-1")), next_cursor = null)

        val filtered = response.withoutHiddenJobs(emptySet())

        assertSame(response, filtered)
    }

    @Test
    fun withoutHiddenJobs_canReturnEmptyPageWhileKeepingPaginationCursor() {
        val response = JobListResponse(
            items = listOf(job("hidden-1"), job("hidden-2")),
            next_cursor = "next-page",
        )

        val filtered = response.withoutHiddenJobs(setOf("hidden-1", "hidden-2"))

        assertEquals(emptyList<JobSummaryDto>(), filtered.items)
        assertEquals("next-page", filtered.next_cursor)
    }

    private fun job(id: String): JobSummaryDto = JobSummaryDto(id = id, created_at = 100L)

    @Test
    fun networkTypeFor_requiresUnmeteredByDefault_connectedWhenCellularAllowed() {
        assertEquals(NetworkType.UNMETERED, networkTypeFor(allowCellularData = false))
        assertEquals(NetworkType.CONNECTED, networkTypeFor(allowCellularData = true))
    }

    @Test
    fun canPrefetchGivenNetwork_alwaysTrueWhenCellularIsAllowed() {
        assertTrue(canPrefetchGivenNetwork(allowCellularData = true, isCurrentNetworkUnmetered = false))
        assertTrue(canPrefetchGivenNetwork(allowCellularData = true, isCurrentNetworkUnmetered = null))
    }

    @Test
    fun canPrefetchGivenNetwork_wifiOnlyFollowsActualConnectionWhenKnown() {
        assertTrue(canPrefetchGivenNetwork(allowCellularData = false, isCurrentNetworkUnmetered = true))
        assertFalse(canPrefetchGivenNetwork(allowCellularData = false, isCurrentNetworkUnmetered = false))
    }

    @Test
    fun canPrefetchGivenNetwork_wifiOnlyDoesNotBlockWhenConnectivityStateIsUnknown() {
        assertTrue(canPrefetchGivenNetwork(allowCellularData = false, isCurrentNetworkUnmetered = null))
    }

    @Test
    fun hasConnectivity_trueOnlyWhenInternetCapabilityIsAffirmativelyKnown() {
        assertTrue(hasConnectivity(hasInternetCapability = true))
        assertFalse("no active network must block, not fail open", hasConnectivity(hasInternetCapability = false))
        assertFalse("unknown capabilities must block (fail-safe), unlike prefetch's fail-open", hasConnectivity(hasInternetCapability = null))
    }

    // Feature 015 — local composite gallery entries. previewModel/resultModel/thumbModel/
    // offlineAvailability/deleteJob's actual branching lives on RealJobRepository instance methods
    // (needing context/dao this file's existing tests never construct), so those are covered by
    // quickstart.md's manual walkthrough instead; what's genuinely unit-testable in isolation is
    // the reserved-prefix convention and the pure file-path format both branches rely on.

    @Test
    fun localCompositeIdPrefix_isDistinctFromAnyRealisticServerIssuedId() {
        // Server ids observed elsewhere in this codebase's tests/fixtures are short opaque
        // tokens/UUIDs with no fixed human-readable prefix — asserting the reserved prefix itself
        // is non-blank and not something trivially produced by chance is the meaningful invariant.
        assertTrue(LOCAL_COMPOSITE_ID_PREFIX.isNotBlank())
        assertTrue("local-composite-${java.util.UUID.randomUUID()}".startsWith(LOCAL_COMPOSITE_ID_PREFIX))
        assertFalse("job-abc123".startsWith(LOCAL_COMPOSITE_ID_PREFIX))
    }

    @Test
    fun localCompositeRelativePath_embedsTheFullJobIdAndAConsistentFileName() {
        val id = "$LOCAL_COMPOSITE_ID_PREFIX${java.util.UUID.randomUUID()}"

        val path = localCompositeRelativePath(id)

        assertTrue(path.startsWith("local_composites/"))
        assertTrue(path.contains(id))
        assertTrue(path.endsWith("composite.jpg"))
    }

    @Test
    fun localCompositeRelativePath_isDeterministicForTheSameId() {
        val id = "$LOCAL_COMPOSITE_ID_PREFIX${java.util.UUID.randomUUID()}"

        assertEquals(localCompositeRelativePath(id), localCompositeRelativePath(id))
    }
}
