package dev.zun.flux.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/**
 * Formats a Unix timestamp in SECONDS to a human-readable string.
 * Standardizing on seconds to match the Rust server contract.
 */
fun formatTimestamp(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val then = Instant.ofEpochSecond(timestampSeconds).atZone(zone).toLocalDate()

    return when {
        then == today -> "Today"
        then == today.minusDays(1) -> "Yesterday"
        then.weekOfYear() == today.weekOfYear() -> "Earlier this week"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}

private fun LocalDate.weekOfYear(): Pair<Int, Int> {
    val weekFields = WeekFields.of(Locale.getDefault())
    return get(weekFields.weekBasedYear()) to get(weekFields.weekOfWeekBasedYear())
}
