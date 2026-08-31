plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zango.pokertracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zango.pokertracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "Beta 1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Material 3 still marks Scaffold's top bar and the segmented buttons experimental.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Everything the IDE would flag is treated as a build concern, so warnings cannot
        // accumulate unnoticed between sessions.
        checkAllWarnings = true
        warningsAsErrors = true
        disable += setOf(
            // Guards against an extension being shadowed by a member added later in a
            // dependency. It only bites a library published against an older runtime; this app
            // compiles as one unit, and the calls it flags are plain Long and Map members.
            "MemberExtensionConflict",
            // Two screens saying "Add" in English is not two screens saying the same thing.
            // A translator needs each one separately: the word that fits "Add a rebuy" is not
            // always the word that fits "Add a player", and merging them here to satisfy a
            // heuristic would take that choice away before anyone has made it.
            "DuplicateStrings",
            // Dependency currency is a deliberate decision, not something a build should fail on.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
        )
    }

    bundle {
        language {
            // Both languages ship in every install. By default an App Bundle only delivers the
            // resources for the device's own language, which would leave the in-app switcher
            // pointing at a translation that is not on the phone.
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Room schema export: keeps a JSON schema per version under app/schemas for migration testing.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.text.google.fonts)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
