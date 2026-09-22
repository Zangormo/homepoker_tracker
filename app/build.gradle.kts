import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing secrets come from local.properties (git-ignored) or, failing that, from
// environment variables of the same name, so nothing secret lives in version control.
// See local.properties.example for the keys.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigning(key: String): String? =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigning("RELEASE_STORE_FILE")

// AdMob IDs come from the same places as the signing keys. Each falls back to Google's public test
// ID, so a checkout without local.properties builds and can only ever request test ads. Real IDs
// are added later, in local.properties or CI - never in this file.
fun adsSetting(key: String, fallback: String): String =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: fallback

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdaptiveBannerUnitId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialUnitId = "ca-app-pub-3940256099942544/1033173712"

fun String.quoted(): String = "\"$this\""

// Versioning, changed by hand for every release uploaded to Play Console.
//
// appVersionCode: +1 for every upload, whatever the change. Play rejects a code it has already seen,
//   and it is what decides which build is newer, so it only ever goes up.
// appVersionName: semantic versioning, MAJOR.MINOR.PATCH, as shown to users.
//   PATCH - bug fixes only, nothing new for the user to learn.
//   MINOR - a new feature or screen; existing data and results are untouched.
//   MAJOR - a breaking change to stored data or to how money, reconciliation or settlement are
//           calculated, so the same game could now come out differently.
// Codes 1 and earlier were the unpublished betas ("1.0 Beta" to "Beta 1.2").
val appVersionCode = 4
val appVersionName = "1.2.0"

android {
    namespace = "com.zango.pokertracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zango.pokertracker"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["admobAppId"] = adsSetting("ADMOB_APP_ID", testAdMobAppId)
        // One unit per placement, so AdMob reports each placement separately.
        buildConfigField(
            "String",
            "ADMOB_BANNER_CREATE_GAME_UNIT_ID",
            adsSetting("ADMOB_BANNER_CREATE_GAME_UNIT_ID", testAdaptiveBannerUnitId).quoted(),
        )
        buildConfigField(
            "String",
            "ADMOB_BANNER_HISTORY_UNIT_ID",
            adsSetting("ADMOB_BANNER_HISTORY_UNIT_ID", testAdaptiveBannerUnitId).quoted(),
        )
        buildConfigField(
            "String",
            "ADMOB_BANNER_LIVE_GAME_UNIT_ID",
            adsSetting("ADMOB_BANNER_LIVE_GAME_UNIT_ID", testAdaptiveBannerUnitId).quoted(),
        )
        // Google's current test IDs have no separate video interstitial: the one interstitial test
        // unit serves both. A real unit's formats, video included, are set in the AdMob console.
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_BEFORE_SETTLEMENT_UNIT_ID",
            adsSetting("ADMOB_INTERSTITIAL_BEFORE_SETTLEMENT_UNIT_ID", testInterstitialUnitId).quoted(),
        )
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_PAID_UP_UNIT_ID",
            adsSetting("ADMOB_INTERSTITIAL_PAID_UP_UNIT_ID", testInterstitialUnitId).quoted(),
        )
        // Debug builds only: the hashed id UMP logs for this phone. When set, the consent form is
        // requested as if the phone were in the EEA, so the flow can be tested from anywhere.
        buildConfigField(
            "String",
            "UMP_TEST_DEVICE_HASHED_ID",
            adsSetting("UMP_TEST_DEVICE_HASHED_ID", "").quoted(),
        )
    }

    signingConfigs {
        // Only declared when a keystore is configured. The release build type looks it up with
        // getByName, so without the keys Gradle fails at configuration instead of producing an
        // unsigned release.
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseSigning("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A package of its own, so a debug build installs next to the release from Play instead
            // of on top of it - the two are signed with different keys, and Android would otherwise
            // refuse the install until the release was removed. It also keeps its own database.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
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

// One command before every Play upload: the unit tests and lint against the release variant, then
// the minified, signed bundle. Unit tests run on the JVM against the unminified release classes;
// R8's output is dex and can only be exercised on a device, so this does not replace installing
// the release build and using it.
tasks.register("verifyRelease") {
    group = "verification"
    description = "Runs release unit tests and lint, then builds the minified release bundle."
    dependsOn("testReleaseUnitTest", "lintRelease", "bundleRelease")
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

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.billing)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)

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
