import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.sentry.android)
}

val keystorePropsFile = rootProject.file("keystore.properties")

private val localProps: Properties = rootProject.file("local.properties").let { f ->
    Properties().apply { if (f.exists()) f.inputStream().use { load(it) } }
}

/**
 * SENTRY_DSN — Sentry project ingest endpoint. DSNs are not secret in
 * Sentry's threat model, but routing it through local.properties (dev) /
 * GitHub Actions secret (CI) keeps project-specific endpoints out of
 * source control. If absent at build time, the app still compiles and
 * runs — `SentryAndroid.init` silently no-ops on a blank DSN, so the
 * binary just doesn't report crashes.
 */
val sentryDsn: String =
    localProps.getProperty("SENTRY_DSN", "")
        .ifBlank { System.getenv("SENTRY_DSN") ?: "" }

/**
 * SENTRY_AUTH_TOKEN — write-authority API token for the Sentry Gradle
 * plugin's ProGuard mapping upload + source-context bundling. Sourced from
 * local.properties for dev builds and from the SENTRY_AUTH_TOKEN env var in
 * CI (set via the GitHub Actions secret of the same name in the release
 * job). Mapping upload silently disables itself if no token is found, so
 * CI's unsigned R8-verify build keeps working without the secret.
 */
val sentryAuthToken: String =
    localProps.getProperty("SENTRY_AUTH_TOKEN", "")
        .ifBlank { System.getenv("SENTRY_AUTH_TOKEN") ?: "" }

/**
 * Run a command and return its trimmed stdout, or null on any failure
 * (missing binary, non-zero exit, etc.). Used for git-derived version
 * metadata so a missing .git dir or shallow clone doesn't break the build —
 * callers fall back to a hardcoded default.
 *
 * Uses [providers.exec] so the call is configuration-cache safe under
 * Gradle 9+ (ProcessBuilder at configuration time is forbidden).
 */
fun execOrNull(vararg args: String): String? = runCatching {
    val out = providers.exec {
        commandLine(*args)
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }
    if (out.result.get().exitValue != 0) return@runCatching null
    out.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
}.getOrNull()

/**
 * versionCode = number of commits in HEAD's history. Monotonically increases,
 * never resets, satisfies Play Store's "must be a positive integer that
 * increases each release" rule. Override with VERSION_CODE_OVERRIDE env var
 * for CI tag builds where the working copy might be a shallow checkout.
 */
val resolvedVersionCode: Int = (
    System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull()
        ?: execOrNull("git", "rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: 1
    )

/**
 * versionName from `git describe --tags --dirty --always`. Examples:
 *   v1.0.0                     (clean tag)
 *   v1.0.0-3-gabc1234          (3 commits past v1.0.0)
 *   v1.0.0-3-gabc1234-dirty    (uncommitted changes)
 *   abc1234                    (no tags yet, falls back to short SHA)
 */
val resolvedVersionName: String = (
    System.getenv("VERSION_NAME_OVERRIDE")
        ?: execOrNull("git", "describe", "--tags", "--dirty", "--always")
        ?: "1.0.0-dev"
    )

android {
    namespace = "dev.zun.flux"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.zun.flux"
        minSdk = 36
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug installs as dev.zun.flux.debug so it can coexist on-device with
        // the release-signed dev.zun.flux. easylauncher (configured below) adds
        // a "DEV" ribbon to the launcher icon, and the debug-only string
        // override changes the launcher label to "FluxEdit Dev". Together they
        // make the two builds unmistakable on the home screen.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // arm64-only is fine for the sideloaded device build, but debug must
            // keep x86_64 native libs so instrumented tests run on CI emulators.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

// The baseline-profile plugin lazily registers `nonMinifiedRelease` and
// `benchmarkRelease` build types from `release`. Both inherit the production
// applicationId, which would collide on-device with the release-signed
// dev.zun.flux. Override the applicationId on those variants with the .bp
// suffix so the generator and benchmark APKs install side-by-side.
//
// onVariants runs after variant creation and lets us set `applicationId`
// directly (the field is on Variant, not VariantBuilder). Configuration-
// cache friendly, no afterEvaluate needed.
androidComponents {
    listOf("nonMinifiedRelease", "benchmarkRelease").forEach { buildType ->
        onVariants(selector().withBuildType(buildType)) { variant ->
            variant.applicationId.set("dev.zun.flux.bp")
        }
    }
}

gradle.taskGraph.whenReady {
    val requestsReleaseBuild = allTasks.any { task ->
        task.path in setOf(":app:assembleRelease", ":app:bundleRelease", ":app:packageRelease")
    }
    val isCi = System.getenv("CI").equals("true", ignoreCase = true)
    if (requestsReleaseBuild && !keystorePropsFile.exists() && !isCi) {
        error("Release signing requires keystore.properties. Copy keystore.properties.example and configure a real release key.")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

easylauncher {
    buildTypes {
        register("debug") {
            // Chrome-style ribbon across the icon's bottom-left to bottom-right
            // — readable on both adaptive (round/squircle) and legacy launchers.
            filters(chromeLike(label = "DEV", ribbonColor = "#7B2D8E"))
        }
    }
}

sentry {
    org.set("yanwen-xu")
    projectName.set("android")

    // Upload + source bundling are gated on having an auth token. Local dev
    // with the token in local.properties → uploads. CI release-tag job with
    // SENTRY_AUTH_TOKEN secret → uploads. CI's PR/push job (no secret) →
    // silently skips so the R8 verify build still passes.
    val haveToken = sentryAuthToken.isNotBlank()
    autoUploadProguardMapping.set(haveToken)
    includeSourceContext.set(haveToken)
    if (haveToken) {
        authToken.set(sentryAuthToken)
    }

    autoInstallation.enabled.set(false)
    // Bytecode-level OkHttp/Room instrumentation is free regardless and adds
    // useful breadcrumbs (HTTP requests, DB queries) to crash reports.
    tracingInstrumentation {
        enabled.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    implementation(libs.androidx.window)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.telephoto.zoomable.image.coil3)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.guava)
    implementation(libs.androidx.compose.material.icons.extended)

    // ProfileInstaller compiles the baseline profile on first install. The
    // baselineProfile dependency below pulls the generated profile from the
    // :baselineprofile module into app/src/<variant>/generated/baselineProfiles
    // automatically when the producer plugin runs.
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    implementation(libs.sentry.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
