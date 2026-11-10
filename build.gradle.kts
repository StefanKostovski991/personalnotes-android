import com.google.firebase.appdistribution.gradle.firebaseAppDistributionDefault

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}

/**
 * Firebase App Distribution is declared as a plain buildscript dependency rather than through
 * the `plugins { }` block above.
 *
 * Reason: the artifact publishes no Gradle plugin marker (only
 * `META-INF/gradle-plugins/com.google.firebase.appdistribution.properties`), so plugin-request
 * resolution cannot find it by id. The classpath version still comes from the version catalog,
 * so there is a single place to bump it.
 */
buildscript {
    dependencies {
        classpath(
            "com.google.firebase:firebase-appdistribution-gradle:${libs.versions.firebaseAppDistribution.get()}",
        )
    }
}

/**
 * Path of the Firebase Android config for this project.
 *
 * The location can be overridden with GOOGLE_SERVICES_JSON, which is how CI supplies the file
 * from a secret; the variable is read through the provider API so it is a tracked
 * configuration-cache input.
 */
fun Project.firebaseConfigFile(): File {
    val candidate = providers.environmentVariable("GOOGLE_SERVICES_JSON").orNull
        ?.takeIf { it.isNotBlank() }
        ?: "app/google-services.json"
    return rootProject.file(candidate)
}

subprojects {
    if (path != ":app") return@subprojects

    val firebaseConfigFile = project.firebaseConfigFile()
    val firebaseConfigured = firebaseConfigFile.isFile

    /**
     * Fails early with an actionable message when the Firebase config is missing.
     *
     * The google-services plugin would otherwise report its own, much less helpful error. The
     * task is wired into preBuild, so it runs for every variant.
     *
     * The existence check is wrapped in a provider so the task carries no project reference into
     * execution, which keeps it compatible with the configuration cache.
     */
    val firebaseConfigExists = providers.provider { firebaseConfigFile.isFile }
    tasks.register("checkFirebaseConfig") {
        description = "Checks that app/google-services.json is present."
        onlyIf { !firebaseConfigExists.get() }
        doLast {
            throw GradleException(
                """
                Missing app/google-services.json.

                It comes from your Firebase project and is required to build:
                  1. firebase login                 (once, if not already signed in)
                  2. create or pick a project:      firebase projects:list
                  3. register this app:             firebase apps:create ANDROID "Personal Notes" \
                        --package-name com.kostovskiapps.personalnotes --project <PROJECT_ID>
                  4. download the config:           firebase apps:sdkconfig ANDROID <APP_ID> \
                        --project <PROJECT_ID> > app/google-services.json

                app/google-services.template.json shows the expected shape.
                Until Firebase is set up you can build the app by dropping the google-services
                plugin from app/build.gradle.kts and the Firebase wiring from this file.
                """.trimIndent(),
            )
        }
    }

    plugins.withId("com.android.application") {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn("checkFirebaseConfig")
        }
    }

    // The plugin is applied from here, not from :app's own plugins block: it publishes no
    // plugin marker, and a build script cannot configure an extension that has not been applied
    // yet - which is exactly the ordering a subprojects block gives us.
    if (firebaseConfigured && !plugins.hasPlugin("com.google.firebase.appdistribution")) {
        pluginManager.apply("com.google.firebase.appdistribution")

        // Configured after evaluation: the extension is registered while the plugin is applied,
        // and this keeps the ordering explicit rather than relying on it.
        //
        // `firebaseAppDistributionDefault` is the plugin's own non-deprecated DSL entry point
        // for project-wide defaults (the same-named block form is deprecated).
        afterEvaluate {
            firebaseAppDistributionDefault {
                // Tester groups and/or individual addresses:
                //   ./gradlew :app:appDistributionUploadRelease -Pgroups=qa-team
                //   ./gradlew :app:appDistributionUploadRelease -Ptesters=a@b.com,c@d.com
                (findProperty("groups") as String?)?.let { groups = it }
                (findProperty("testers") as String?)?.let { testers = it }

                // Credentials are never committed. Either sign the CLI in once
                // (`firebase login` / `firebase login:ci`) or pass a service account file:
                //   -PserviceCredentialsFile=/secure/firebase-service-account.json
                //   FIREBASE_SERVICE_CREDENTIALS=/secure/firebase-service-account.json
                val credentials = (findProperty("serviceCredentialsFile") as String?)
                    ?: providers.environmentVariable("FIREBASE_SERVICE_CREDENTIALS").orNull
                if (!credentials.isNullOrBlank()) serviceCredentialsFile = credentials

                // Release notes default to the commit being built;
                // -PreleaseNotes="..." overrides.
                releaseNotes = (findProperty("releaseNotes") as String?)
                    ?: providers.exec {
                        commandLine("git", "log", "-1", "--pretty=format:%h %s")
                        isIgnoreExitValue = true
                    }.standardOutput.asText.getOrElse("").trim().ifBlank { "Personal Notes" }
            }
        }
    }
}
