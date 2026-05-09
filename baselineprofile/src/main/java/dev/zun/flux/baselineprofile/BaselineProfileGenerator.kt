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
 * To regenerate (the standard gradle task `:app:generateBaselineProfile`
 * is broken on Samsung devices via UTP — pre-test "device not found" /
 * "closed" ADB errors that don't reproduce with direct instrumentation;
 * works fine on emulators):
 *
 *   ./gradlew :baselineprofile:assembleNonMinifiedRelease
 *   ./gradlew :app:assembleNonMinifiedRelease
 *   adb install -r app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
 *   adb install -r baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk
 *   adb shell am instrument -w -e class \
 *     dev.zun.flux.baselineprofile.BaselineProfileGenerator \
 *     dev.zun.flux.baselineprofile/androidx.test.runner.AndroidJUnitRunner
 *   adb pull "/storage/emulated/0/Android/media/dev.zun.flux.baselineprofile/BaselineProfileGenerator_generate-baseline-prof.txt" \
 *     app/src/main/baseline-prof.txt
 *
 * The macrobenchmark runs the app several times (default 3 iterations) and
 * unions the classes/methods touched on those runs. Keep the path stable —
 * adding flaky network calls here makes the resulting profile noisy.
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

// `.bp` matches the applicationIdSuffix on the nonMinifiedRelease build
// type in app/build.gradle.kts. We must record the profile against the
// unobfuscated variant — recording against the R8-minified production
// install would produce a profile of obfuscated names that don't match
// any future build's R8 output.
private const val PACKAGE_NAME = "dev.zun.flux.bp"
