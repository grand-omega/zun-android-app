package dev.zun.flux.ui.progress

import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.zun.flux.data.api.JobStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dimmed source thumbnail on batch progress is shown for every non-done state, so its
 * description has to name the state the job is actually in. It previously said "in progress"
 * unconditionally, which told a screen-reader user the opposite of what happened to a failed
 * generation — and failed generations now have their own entry point on Home, so that branch is
 * reached in a terminal state routinely rather than rarely.
 *
 * Every state is resolved inside one setContent: the rule allows only a single call per test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-normal-port")
class SourceImageContentDescriptionTest {

    @get:Rule
    val rule = createComposeRule()

    private val runningDto = JobStatusDto(id = "job-1", status = "running", created_at = 0L)

    private val states = mapOf(
        "failed" to PollState.Failed("boom", confirmedGone = true),
        "cancelled" to PollState.Cancelled,
        "deleted" to PollState.Deleted,
        "starting" to PollState.Starting,
        "running" to PollState.Running(runningDto),
    )

    private fun describeAll(): Map<String, String> {
        val resolved = mutableMapOf<String, String>()
        rule.setContent {
            states.forEach { (key, state) -> resolved[key] = sourceImageContentDescription(state) }
        }
        rule.waitForIdle()
        return resolved
    }

    @Test
    fun `each state gets the description that matches it`() {
        val d = describeAll()

        assertEquals("Source image for a failed generation", d["failed"])
        assertEquals("Source image for a cancelled generation", d["cancelled"])
        assertEquals("Source image for a deleted generation", d["deleted"])
        assertEquals("Source image in progress", d["starting"])
        assertEquals("Source image in progress", d["running"])
    }

    @Test
    fun `no terminal state is announced as in progress`() {
        val d = describeAll()
        val inProgress = "Source image in progress"

        // The regression itself: all three terminal states used to return this.
        listOf("failed", "cancelled", "deleted").forEach { key ->
            assertNotEquals("$key must not be announced as in progress", inProgress, d[key])
        }
    }
}
