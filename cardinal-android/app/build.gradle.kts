/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.android.build.api.dsl.ApkSigningConfig
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hidden.secrets)
    kotlin("plugin.serialization") version "2.2.21"
    jacoco
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val jacocoUnitFileFilter = listOf(
    // Android generated
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",

    // Android generated
    "**/*_ViewBinding*.*",
    "**/*Binding.class",
    "**/*BindingImpl.class",

    // Dagger / Hilt generated
    "**/*_Factory.class",
    "**/*_MembersInjector.class",
    "**/*_HiltModules.class",
    "**/hilt_aggregated_deps/**",

    // Kotlin generated
    "**/*\$Companion.class",
    "**/*\$serializer.class",
    "**/*Serializer.class",
    "**/*\$inlined\$*.class",

    // Compose generated
    "**/*ComposableSingletons*.class",

    // Tests
    "**/*Test.class",
    "**/*Test$*.class",
    "**/*Tests.class",
    "**/*Tests$*.class",

    // UniFFI generated
    "**/uniffi/**",

    // Generated/framework classes that inflate the unit-test denominator.
    "**/*_Impl.class",
    "**/*_Impl$*.class",
    "**/*_Factory$*.class",
    "**/*_HiltModules*.*",
    "**/*_Provide*Factory*.*",
    "**/*_LazyMapKey*.*",
    "**/*_GeneratedInjector*.*",
    "**/Dagger*.*",
    "**/*_HiltComponents*.*",
    "dagger/hilt/**",
    "**/dagger/hilt/**",
    "**/GeneratedCountryBounds.class",
    "**/GeneratedCountryBounds$*.class"
)

val collectJacocoClassDirectories: (
    String,
    List<String>
) -> FileTree = { variantName, fileFilter ->
    // Android ASM-transformed classes are the final classes JaCoCo should analyze.
    fileTree(
        layout.buildDirectory
            .dir(
                "intermediates/classes/$variantName/transform${variantName.replaceFirstChar { it.uppercase() }}ClassesWithAsm/dirs"
            )
            .get()
            .asFile
    ) {
        exclude(fileFilter)
    }
}

val jacocoSourceDirectories = listOf(
    "$projectDir/src/main/java",
    "$projectDir/src/main/kotlin"
)

val collectJacocoExecutionData: (
    String
) -> FileTree = { testTaskName ->
    fileTree(
        layout.buildDirectory.get().asFile
    ) {
        include(
            "jacoco/$testTaskName.exec",
            "outputs/unit_test_code_coverage/**/$testTaskName.exec",
            "outputs/unit_test_code_coverage/**/*.ec"
        )
    }
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

abstract class GenerateUniFFIBindingsTask : Exec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

abstract class GenerateCountryBoundsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val countryBounds = parseCountryBounds()
            .sortedWith(
                compareBy<CountryBoundSpec> { it.countryCode }
                    .thenBy { it.south }
                    .thenBy { it.west }
            )

        val outputFile = outputDir.get().asFile
            .resolve("earth/maps/cardinal/data/GeneratedCountryBounds.kt")

        outputFile.parentFile.mkdirs()
        outputFile.writeText(buildSource(countryBounds))
    }

    private fun parseCountryBounds(): List<CountryBoundSpec> {
        val json = JsonSlurper().parse(inputFile.get().asFile)
        val entries = json as? List<*> ?: error("country_bounds.json must contain a JSON array.")

        return entries.mapIndexed { index, entry ->
            val map = entry as? Map<*, *>
                ?: error("country_bounds.json entry $index must be a JSON object.")

            val countryCode = map.stringField("countryCode", index)
            require(countryCode.matches(COUNTRY_CODE_PATTERN)) {
                "country_bounds.json entry $index has invalid countryCode: $countryCode"
            }

            CountryBoundSpec(
                countryCode = countryCode,
                south = map.numberField("south", index),
                north = map.numberField("north", index),
                west = map.numberField("west", index),
                east = map.numberField("east", index)
            )
        }
    }

    private fun Map<*, *>.stringField(field: String, index: Int): String {
        return this[field] as? String
            ?: error("country_bounds.json entry $index is missing string field: $field")
    }

    private fun Map<*, *>.numberField(field: String, index: Int): String {
        return (this[field] as? Number)?.toString()
            ?: error("country_bounds.json entry $index is missing number field: $field")
    }

    private fun buildSource(countryBounds: List<CountryBoundSpec>): String = buildString {
        appendLine("@file:Suppress(\"MagicNumber\")")
        appendLine()
        appendLine("package earth.maps.cardinal.data")
        appendLine()
        appendLine("// Generated by the generateCountryBounds Gradle task. Do not edit manually.")
        appendLine("internal object GeneratedCountryBounds {")
        appendLine("    val countryBounds = listOf(")
        countryBounds.forEach { bound ->
            appendLine(
                "        CountryBounds(" +
                    "\"${bound.countryCode}\", " +
                    "${bound.south}, " +
                    "${bound.north}, " +
                    "${bound.west}, " +
                    "${bound.east}" +
                    "),"
            )
        }
        appendLine("    )")
        appendLine("}")
        appendLine()
        appendLine("internal data class CountryBounds(")
        appendLine("    val countryCode: String,")
        appendLine("    val south: Double,")
        appendLine("    val north: Double,")
        appendLine("    val west: Double,")
        appendLine("    val east: Double")
        appendLine(") {")
        appendLine("    fun contains(latLng: LatLng): Boolean {")
        appendLine("        return latLng.latitude in south..north &&")
        appendLine("            latLng.longitude in west..east")
        appendLine("    }")
        appendLine("}")
    }

    private companion object {
        val COUNTRY_CODE_PATTERN = Regex("[A-Z]{2}")
    }
}

private data class CountryBoundSpec(
    val countryCode: String,
    val south: String,
    val north: String,
    val west: String,
    val east: String
)

val secretsPackageName = "earth.maps.cardinal"
val androidNdkVersion = providers.environmentVariable("ANDROID_NDK_VERSION").orNull ?: "29.0.14206865"
val androidCmakeVersion = providers.environmentVariable("ANDROID_CMAKE_VERSION").orNull ?: "3.22.1"
val hiddenSecretsCmakeFile = file("src/main/cpp/CMakeLists.txt")
val hasHiddenSecretsCmakeFile = hiddenSecretsCmakeFile.isFile

val defaultPeliasEndpoint = "https://api.stadiamaps.com/geocoding/v1"
val defaultNearbyEndpoint = "https://maps.earth/pelias/v1"
val defaultValhallaEndpoint = "https://api.stadiamaps.com/route/v1"

val sentryDSN = System.getenv("SENTRY_DSN") ?: "placeholdertoto"

val fieldContent = "\"$sentryDSN\""
val cargoNdkWorkspaceDir = file("../..")
val cargoNdkOutputDir = layout.projectDirectory.dir("src/main/jniLibs")
val cargoNdkBuildTasks = mapOf(
    "Arm64" to "arm64-v8a",
    "X86_64" to "x86_64"
).map { (taskNameSuffix, target) ->
    tasks.register<Exec>("buildCargoNdk${taskNameSuffix}Release") {
        workingDir = cargoNdkWorkspaceDir
        commandLine(
            "cargo",
            "ndk",
            "-t",
            target,
            "-o",
            "cardinal-android/app/src/main/jniLibs",
            "build",
            "--release",
            "-p",
            "cardinal-geocoder"
        )

        inputs.dir(file("../../cardinal-geocoder/src"))
        inputs.dir(file("../../cardinal-geocoder/dictionaries"))
        inputs.file(file("../../Cargo.lock"))
        inputs.file(file("../../Cargo.toml"))
        inputs.file(file("../../cardinal-geocoder/Cargo.toml"))
        outputs.file(cargoNdkOutputDir.file("$target/libcardinal_geocoder.so"))
    }
}
val generateUniFFIBindings = tasks.register<GenerateUniFFIBindingsTask>("generateUniFFIBindings") {
    workingDir = cargoNdkWorkspaceDir
    outputDir.set(layout.buildDirectory.dir("generated/source/uniffi/java"))
    doFirst {
        commandLine = listOf(
            "cargo",
            "run",
            "--locked",
            "--bin",
            "uniffi-bindgen",
            "-p",
            "cardinal-geocoder",
            "generate",
            "--library",
            "cardinal-android/app/src/main/jniLibs/arm64-v8a/libcardinal_geocoder.so",
            "--language",
            "kotlin",
            "--out-dir",
            outputDir.get().asFile.absolutePath
        )
    }

    dependsOn(cargoNdkBuildTasks)
    inputs.dir(file("../../cardinal-geocoder/bin"))
    inputs.file(file("../../Cargo.lock"))
    inputs.file(file("../../Cargo.toml"))
    inputs.file(file("../../cardinal-geocoder/Cargo.toml"))
    inputs.file(cargoNdkOutputDir.file("arm64-v8a/libcardinal_geocoder.so"))
}

val generateCountryBounds = tasks.register<GenerateCountryBoundsTask>("generateCountryBounds") {
    inputFile.set(layout.projectDirectory.file("src/main/data/country_bounds.json"))
    outputDir.set(layout.buildDirectory.dir("generated/source/countryBounds/kotlin"))
}

tasks.matching { it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(cargoNdkBuildTasks)
}

android {
    namespace = "earth.maps.cardinal"
    compileSdk = 37
    ndkVersion = androidNdkVersion

    defaultConfig {
        applicationId = "com.murena.maps"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "debug"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SENTRY_DSN", fieldContent)
        buildConfigField("String", "SECRETS_PACKAGE_NAME", "\"$secretsPackageName\"")

        if (hasHiddenSecretsCmakeFile) {
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
                    )
                }
            }
        }
    }

    flavorDimensions += "architecture"
    productFlavors {
        create("arm64") {
            dimension = "architecture"
            ndk {
                abiFilters += "arm64-v8a"
            }
            versionNameSuffix = "-arm64"
        }
        create("x86_64") {
            dimension = "architecture"
            ndk {
                abiFilters += "x86_64"
            }
            versionNameSuffix = "-x86_64"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEYSTORE_ALIAS")
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"] as ApkSigningConfig
            manifestPlaceholders["icon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["round_icon"] = "@mipmap/ic_launcher_round"
            resValue("string", "default_pelias_endpoint", defaultPeliasEndpoint)
            resValue("string", "default_valhalla_endpoint", defaultValhallaEndpoint)
            resValue("string", "default_nearby_endpoint", defaultNearbyEndpoint)
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["icon"] = "@mipmap/ic_launcher_debug"
            manifestPlaceholders["round_icon"] = "@mipmap/ic_launcher_round_debug"
            resValue("string", "default_pelias_endpoint", defaultPeliasEndpoint)
            resValue("string", "default_valhalla_endpoint", defaultValhallaEndpoint)
            resValue("string", "default_nearby_endpoint", defaultNearbyEndpoint)
        }
    }

    bundle {
        language {
            // Disable language splits for now to keep bundles simpler
            enableSplit = false
        }
        density {
            // Enable density splits for smaller downloads
            enableSplit = true
        }
        abi {
            // Enable ABI splits - this works with our product flavors
            enableSplit = true
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    if (hasHiddenSecretsCmakeFile) {
        externalNativeBuild {
            cmake {
                path = hiddenSecretsCmakeFile
                version = androidCmakeVersion
            }
        }
    }

}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            generateUniFFIBindings,
            GenerateUniFFIBindingsTask::outputDir
        )
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            generateCountryBounds,
            GenerateCountryBoundsTask::outputDir
        )
        // JaCoCo
        val variantCap = variant.name.replaceFirstChar { it.uppercase() }

        val appTestTaskName = "test${variantCap}UnitTest"
        val unitReportTaskName = "jacoco${variantCap}UnitReport"

        fun registerJacocoUnitReportTask(taskName: String) {
            tasks.register<JacocoReport>(taskName) {

                dependsOn(appTestTaskName)

                group = "verification"

                description = "Generates unit-focused JaCoCo coverage report for the ${variant.name} build."

                reports {
                    xml.required.set(true)
                    xml.outputLocation.set(
                        layout.buildDirectory.file(
                            "reports/jacoco/$taskName/$taskName.xml"
                        )
                    )

                    html.required.set(true)
                    html.outputLocation.set(
                        layout.buildDirectory.dir(
                            "reports/jacoco/$taskName/html"
                        )
                    )
                }

                classDirectories.setFrom(
                    collectJacocoClassDirectories(
                        variant.name,
                        jacocoUnitFileFilter
                    )
                )

                sourceDirectories.setFrom(
                    jacocoSourceDirectories
                )

                executionData.setFrom(
                    collectJacocoExecutionData(appTestTaskName)
                )
            }
        }

        registerJacocoUnitReportTask(unitReportTaskName)
    }
}

detekt {
    parallel = true
    config.setFrom("detekt.yml")
    allRules = true
}

dependencies {
    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.compose.material3)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.valhalla.mobile)
    implementation(libs.valhalla.models)
    implementation(libs.valhalla.config)
    implementation(libs.ferrostar.core)
    implementation(libs.ferrostar.maplibreui)
    implementation(libs.ferrostar.composeui)
    implementation(libs.okhttp3)
    implementation(libs.logging.interceptor)
    implementation(libs.androidaddressformatter)
    implementation(libs.eos.telemetry)

    // TODO: Migrate version to TOML (doesn't work). Likely related issue: https://github.com/gradle/gradle/issues/21267
    //noinspection UseTomlInstead
    implementation("net.java.dev.jna:jna:5.17.0@aar")

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.openinghoursparser)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.gson)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
