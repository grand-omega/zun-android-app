package dev.zun.flux

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized tuning knobs. When you find yourself reaching for a magic number
 * in code, add it here instead so the trade-offs live in one file.
 */
object Tuning {

    // --- Networking -----------------------------------------------------------

    /** OkHttp connect timeout. Long enough to traverse Tailscale wakeup latency. */
    const val HTTP_CONNECT_TIMEOUT_SECONDS = 30L

    /** OkHttp read timeout. Generation jobs themselves use polling, not long-poll. */
    const val HTTP_READ_TIMEOUT_SECONDS = 60L

    /** TCP probe timeout used by [data.net.NetworkResolver] when picking LAN vs Tailscale.
     *  Too low → LAN false-negatives on slow home routers; too high → user waits on every
     *  network change. 400ms is the empirical sweet spot. */
    const val NETWORK_PROBE_TIMEOUT_MS = 400

    /** How long a NetworkResolver result is considered fresh. Avoids re-probing on every
     *  ConnectivityManager callback when WiFi flaps. */
    const val NETWORK_RESOLVE_CACHE_MS = 30_000L

    /** Max IOException retries before [data.worker.JobUploadWorker] gives up. */
    const val MAX_UPLOAD_RETRIES = 4

    /** Age after which orphaned staged-upload files in cacheDir are swept at app
     *  start. Active uploads are awaited at most ~60s before being cancelled, so
     *  anything this old was leaked by a crash or failed cancellation cleanup. */
    const val STAGED_UPLOAD_MAX_AGE_MS = 24L * 60L * 60L * 1000L

    // --- Caching --------------------------------------------------------------

    /** Disk budget for the offline image cache (thumb/preview/result). 1GB strikes
     *  a balance between offline gallery completeness and storage politeness. */
    const val OFFLINE_IMAGE_CACHE_MAX_BYTES = 1_000L * 1024L * 1024L

    /** How many image prefetches can run concurrently. Higher = faster cache warm-up,
     *  lower = less competition with foreground network requests. */
    const val OFFLINE_PREFETCH_CONCURRENCY = 3

    /** Coil in-memory cache size in bytes. Sized to comfortably hold a few full-resolution
     *  bitmaps (one 4K ARGB_8888 bitmap is ~32–64 MB) so that scroll-back and zoom revisits
     *  don't re-decode and re-downsample. */
    const val COIL_MEMORY_CACHE_BYTES = 256L * 1024L * 1024L

    // --- Paging ---------------------------------------------------------------

    /** Page size for the gallery grid. Larger = fewer round trips, smaller = faster initial
     *  paint and lower memory churn during fast scrolls. */
    const val GALLERY_PAGE_SIZE = 50

    // --- UI -------------------------------------------------------------------

    /** Max content width for primary single-column screens (progress, settings).
     *  Caps line length on tablets / unfolded foldables. */
    val MAX_CONTENT_WIDTH: Dp = 500.dp
}
