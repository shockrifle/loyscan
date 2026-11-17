plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android.plugin)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.bdaniel.loyscan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bdaniel.loyscan"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        release {
            // R8 (#73): shrinks, optimizes and obfuscates; keep rules only where a library doesn't ship its own.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The release build, R8 and all, but signed with the debug key and installed beside the real app (its own
        // applicationId), so the instrumented smoke test can run against it on any device (#73). Not shipped.
        // wiki/decisions/2026-11-05-r8-smoke-test-on-a-device.md
        create("smoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".smoke"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            proguardFile("proguard-smoke-rules.pro")
            testProguardFiles("proguard-smoke-test-rules.pro")
        }
    }
    // androidTest (the smoke test) runs against the minified build, not debug.
    testBuildType = "smoke"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric (Room, SavedStateHandle, Compose UI tests on the JVM) needs the merged resources.
        unitTests.isIncludeAndroidResources = true
        // Robolectric on SDK 36 reaches into FileDescriptor internals; without this every Robolectric test fails
        // with "Failed to interact with raw FileDescriptor internals".
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            // FrozenSchemaTest and DatabaseMigrationTest read the exported Room schemas from disk (#12): an edited
            // schema JSON must rerun the tests, not leave them UP-TO-DATE.
            it.inputs.dir("$projectDir/schemas").withPropertyName("roomSchemas").withPathSensitivity(PathSensitivity.RELATIVE)
            // RouteKeepRulesTest reads the app's R8 rules (#73).
            it.inputs.file("$projectDir/proguard-rules.pro").withPropertyName("r8Rules").withPathSensitivity(PathSensitivity.RELATIVE)
            // DesignValuesTest reads the app's sources for design values used directly (#108).
            it.inputs.dir("$projectDir/src/main/java").withPropertyName("mainSources").withPathSensitivity(PathSensitivity.RELATIVE)
            // GitIgnoreTest asks git about the repo's ignore rules (#74).
            it.inputs.file("$rootDir/.gitignore").withPropertyName("gitIgnore").withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

// JVM tests run on the debug variant only: Compose UI tests need ui-test-manifest, which is debug-only. The other
// variants' test tasks exist (AGP 9's Compose Preview tasks need unit tests enabled on every variant, and
// gradle.properties turns them on beyond testBuildType) but are switched off. wiki/workflows/build-and-run.md
tasks.withType<Test>().configureEach {
    if (name != "testDebugUnitTest") enabled = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // User preferences (the card list's sort)
    implementation(libs.androidx.datastore.preferences)

    // JSON (card-number rules)
    implementation(libs.kotlinx.serialization.json)

    // Card photos' EXIF orientation (PNG and WebP too on API 29, which the platform reader skips)
    implementation(libs.androidx.exifinterface)

    // Home-screen widget (#11)
    implementation(libs.androidx.glance.appwidget)

    // Barcode drawing (encoding only; pure Java, offline)
    implementation(libs.zxing.core)

    // Barcode scanning (CameraX + zxing-cpp; native, offline, no Play Services)
    implementation(libs.zxing.cpp)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)

    // Timber
    implementation(libs.timber)

    // Coil
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // The smoke test drives the minified app from outside, through UiAutomator only: Compose's test APIs would need
    // Compose internals that R8 has stripped from the app (#73).
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
// Coverage: ./gradlew koverHtmlReportDebug / koverVerifyDebug. Targets, exclusions and the ratchet rule:
// wiki/decisions/2026-10-09-coverage-gate-kover.md
kover {
    reports {
        filters {
            excludes {
                // No logic to test: theme values and hand-rolled icon vectors.
                packages("com.bdaniel.loyscan.ui.theme")
                // Glance/AppWidget glue that runs only on a real home screen: the GlanceAppWidget's provideGlance
                // wiring (repositories in via a Hilt EntryPoint, out to provideContent), the manifest receiver, and the
                // refresher's updateAll/getGlanceIds calls. The logic they drive is tested — toWidgetTiles,
                // CardWidgetContent and CardWidgetUpdater, all in com.bdaniel.loyscan.widget (#11).
                packages("com.bdaniel.loyscan.widget.glance")
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
                classes(
                    // Generated by Hilt / Dagger.
                    // (Patterns match fully-qualified names; module providers are named Module_ProvideXFactory.)
                    "*_Factory", "*_Factory\$*", "*Module_*Factory", "*Module_*Factory\$*", "*_MembersInjector",
                    "*_HiltModules*", "*_GeneratedInjector", "*_ComponentTreeDeps", "*_HiltComponents*", "*.Hilt_*",
                    "dagger.hilt.internal.*", "hilt_aggregated_deps.*",
                    // Generated by Room.
                    "*_Impl", "*_Impl\$*",
                    // Generated by kotlinx.serialization (route serializers).
                    "*\$\$serializer",
                    "*.BuildConfig",
                )
            }
        }
        verify {
            rule("Line coverage") {
                // Ratchet: raise to the new measured floor whenever coverage grows; never lower it.
                minBound(97)
            }
        }
    }
}
