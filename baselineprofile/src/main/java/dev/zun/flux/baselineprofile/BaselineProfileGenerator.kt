package dev.zun.flux.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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

        // Best-effort journey: the app is biometric-locked, so the gallery is
        // only reachable when a run happens inside the unlock grace window
        // (unlock the .bp app manually right before running). When locked,
        // the icon isn't found and the profile still covers cold start.
        val gallery = device.wait(Until.findObject(By.desc("Gallery")), 3_000)
        if (gallery != null) {
            gallery.click()
            device.wait(Until.hasObject(By.text("Gallery")), 5_000)
            // Fling the grid a few times to profile paging + tile composition.
            val center = device.displayWidth / 2
            repeat(3) {
                device.swipe(center, device.displayHeight * 3 / 4, center, device.displayHeight / 4, 10)
                device.waitForIdle()
            }
            device.pressBack()
            device.waitForIdle()
        }
    }
}

// `.bp` matches the applicationIdSuffix on the nonMinifiedRelease build
// type in app/build.gradle.kts. We must record the profile against the
// unobfuscated variant — recording against the R8-minified production
// install would produce a profile of obfuscated names that don't match
// any future build's R8 output.
private const val PACKAGE_NAME = "dev.zun.flux.bp"
