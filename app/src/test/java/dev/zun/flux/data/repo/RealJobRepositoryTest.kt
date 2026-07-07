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
}
