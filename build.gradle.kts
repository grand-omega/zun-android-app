plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

tasks.register<Exec>("installGitHooks") {
    description = "Point git at the tracked .githooks directory."
    group = "build setup"
    workingDir = rootDir
    commandLine("git", "config", "core.hooksPath", ".githooks")
    isIgnoreExitValue = true
}

subprojects {
    afterEvaluate {
        tasks.findByName("preBuild")?.dependsOn(rootProject.tasks.named("installGitHooks"))
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_standard_function-naming" to "disabled"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_standard_function-naming" to "disabled"))
    }
}
