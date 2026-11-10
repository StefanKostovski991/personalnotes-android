/**
 * Whether a Firebase Android config is available for this module.
 *
 * The path can be overridden with GOOGLE_SERVICES_JSON, which is how CI supplies the file from
 * a secret. Only used after the plugins block: the plugins block cannot call script functions.
 */
fun firebaseConfigPresent(): Boolean {
    val fromEnvironment = System.getenv("GOOGLE_SERVICES_JSON")?.takeIf { it.isNotBlank() }
    return file(fromEnvironment ?: "google-services.json").isFile
}

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin support), so the standalone
    // org.jetbrains.kotlin.android plugin must NOT be applied here: it would register a
    // second 'kotlin' extension and fail configuration.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)

    // Firebase App Distribution.
    //
    // google-services is applied normally; the App Distribution plugin has no Gradle plugin
    // marker, so the root build puts it on the buildscript classpath and applies it to this
    // module, and only when a Firebase config exists.
    //
    // Note: this plugin needs app/google-services.json (see README, "Firebase App
    // Distribution"). It is a required file, not an optional one - it also supplies the
    // placeholder values that later Firebase features read from the manifest.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.kostovskiapps.personalnotes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kostovskiapps.personalnotes"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Kept in one place so the share/export layer and the deep link layer agree.
        buildConfigField("String", "NOTE_FILE_EXTENSION", "\"pnote\"")
        buildConfigField("String", "NOTE_LINK_HOST", "\"personalnotes.app\"")
        buildConfigField("String", "NOTE_LINK_SCHEME", "\"personalnotes\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }

            // Testers install the build from App Distribution, so it has to be signed. Until a
            // real upload key is configured (see README), fall back to the debug keystore: it
            // is generated per machine and is never suitable for a store release, but it keeps
            // "assembleRelease + upload" working for internal testing.
            val debugKeystore = rootProject.file("app/debug.keystore")
                .takeIf { it.isFile }
                ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
                    .takeIf { it.isFile }

            if (debugKeystore != null) {
                signingConfig = signingConfigs.create("appDistributionDebug") {
                    storeFile = debugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Firebase App Distribution is applied *and configured* from the root build script's
// `subprojects { }` block: this module cannot configure an extension that has not been applied
// yet, and the plugin is applied there precisely because it publishes no Gradle plugin marker.
// See the root build.gradle.kts for the tester groups, release notes and credentials options.
