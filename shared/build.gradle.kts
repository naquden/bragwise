import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    androidLibrary {
       namespace = "se.atte.bragwise.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_17
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // GitLive's firebase-auth-android depends on com.google.firebase:firebase-auth
            // and firebase-common with NO version, expecting the Firebase BoM to provide them.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
        commonMain.dependencies {
            api(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.navigationevent.compose)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.composables.icons.lucide)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.functions)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
    }
}

sqldelight {
    databases {
        create("BragwiseDatabase") {
            packageName.set("se.atte.bragwise.db")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

abstract class GenerateScoringFixturesTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val fixtureFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        val json = fixtureFile.get().asFile.readText()
        val escaped = json
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\${'$'}")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        val source = "package se.atte.bragwise.domain.scoring\n\n" +
            "internal val SCORING_FIXTURES_JSON: String = \"$escaped\"\n"
        outDir.resolve("ScoringFixtures.kt").writeText(source)
    }
}

val generatedScoringFixturesDir =
    layout.buildDirectory.dir("generated/scoringFixtures/commonTest/kotlin")

val generateScoringFixtures = tasks.register<GenerateScoringFixturesTask>("generateScoringFixtures") {
    fixtureFile.set(rootProject.layout.projectDirectory.file("functions/test/fixtures/scoring/cases.json"))
    outputDir.set(generatedScoringFixturesDir)
}

kotlin.sourceSets.named("commonTest") {
    kotlin.srcDir(generateScoringFixtures.map { it.outputDir })
}