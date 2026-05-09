import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val keystorePropsFile = rootProject.file("keystore.properties")

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
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "dev.zun.flux"
        minSdk = 36
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        ndk { abiFilters += "arm64-v8a" }
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

dependencies {
    implementation(libs.androidx.core.ktx)
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
