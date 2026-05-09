package dev.zun.flux.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile by exercising the cold-start path of the app.
 * Output ends up at app/src/main/baseline-prof.txt and is automatically
 * compiled by ProfileInstaller on the first install of a release build.
 *
 * To regenerate: connect a device, run
 *   ./gradlew :app:generateBaselineProfile
 *
 * The macrobenchmark runs the app several times (default 3 iterations) and
 * unions the classes/methods touched on those runs. Keep the path stable —
 * adding flaky network calls here makes the resulting profile noisy.
 *
 * Note: AGP installs a `nonMinifiedRelease` variant of dev.zun.flux signed
 * with the debug key. If a release-signed dev.zun.flux is already on the
 * device, that install fails with a signature mismatch — uninstall the
 * production app first (or run on an emulator).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME) {
        // Cold start: kill the app, start it from scratch, wait for first
        // frame. This alone covers the hot Compose / view system / AppNav
        // path that dominates cold-start time.
        pressHome()
        startActivityAndWait()
    }
}

private const val PACKAGE_NAME = "dev.zun.flux"
