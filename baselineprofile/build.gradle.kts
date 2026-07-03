import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "dev.zun.flux.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // The baseline-profile plugin runs the generator on a "release-style"
    // (non-minified) variant by default; we keep that.
    testOptions.managedDevices.allDevices {
        // No managed virtual device — generator runs on the connected
        // physical device (Z Fold 7 in this project's case). To run on an
        // emulator instead, register one here as a ManagedVirtualDevice.
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    // Run on a real device, not a managed virtual device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
