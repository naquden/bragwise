plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.googleGmsGoogleServices) apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
    id("com.google.firebase.firebase-perf") version "2.0.2" apply false
}

tasks.register("generateXcconfig") {
    val versionName = providers.gradleProperty("app.versionName")
    val xcconfig = layout.projectDirectory.file("iosApp/Configuration/Config.xcconfig")
    inputs.property("versionName", versionName)
    outputs.file(xcconfig)
    doLast {
        val v = versionName.get()
        val updated = xcconfig.asFile.readLines().map { line ->
            when {
                line.startsWith("CURRENT_PROJECT_VERSION=") -> "CURRENT_PROJECT_VERSION=$v"
                line.startsWith("MARKETING_VERSION=") -> "MARKETING_VERSION=$v"
                else -> line
            }
        }
        xcconfig.asFile.writeText(updated.joinToString("\n") + "\n")
    }
}