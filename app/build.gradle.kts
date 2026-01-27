plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

import java.util.Properties

// Firebase configuration is provided via google-services.json.
// Keep the project buildable without it (prep work), and enable the plugin only when the file exists.
val hasGoogleServices =
    file("google-services.json").exists() ||
        file("src/debug/google-services.json").exists() ||
        file("src/release/google-services.json").exists()

if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("google-services.json missing; Google Services plugin not applied (Firebase disabled).")
}

android {
    namespace = "com.trimsytrack"
    compileSdk = 35

    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { input -> this.load(input) }
        }
    }

    defaultConfig {
        applicationId = "com.trimsytrack"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Multi-app isolation: a stable app_id (compiled into the APK).
        // Backend uses this to prevent cross-app data leakage.
        val fixedAppId = (providers.gradleProperty("APP_ID").orNull ?: "trimsytrack").trim()
        buildConfigField("String", "APP_ID", "\"$fixedAppId\"")

        // BACKENDTRIMSY HTTP API base (must include trailing slash).
        // Overridable for dev via Gradle property, local.properties, or env var.
        val backendApiBaseRaw = (
            providers.gradleProperty("BACKEND_API_BASE").orNull
                ?: localProperties.getProperty("BACKEND_API_BASE")
                ?: providers.environmentVariable("BACKEND_API_BASE").orNull
                ?: "https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1/"
            ).trim()

        val backendApiBase = if (backendApiBaseRaw.endsWith("/")) backendApiBaseRaw else "$backendApiBaseRaw/"
        buildConfigField("String", "BACKEND_API_BASE", "\"$backendApiBase\"")

        // BACKENDTRIMSY Firebase Functions region (used for Callable functions).
        // Overridable for dev via Gradle property, local.properties, or env var.
        val backendFunctionsRegion = (
            providers.gradleProperty("BACKEND_FUNCTIONS_REGION").orNull
                ?: localProperties.getProperty("BACKEND_FUNCTIONS_REGION")
                ?: providers.environmentVariable("BACKEND_FUNCTIONS_REGION").orNull
                ?: "europe-north1"
            ).trim()

        buildConfigField("String", "BACKEND_FUNCTIONS_REGION", "\"$backendFunctionsRegion\"")

        // Google Maps / Places API key (used via AndroidManifest meta-data).
        // Provide via Gradle property, local.properties, or env var.
        val mapsApiKey = (
            providers.gradleProperty("MAPS_API_KEY").orNull
                ?: localProperties.getProperty("MAPS_API_KEY")
                ?: providers.environmentVariable("MAPS_API_KEY").orNull
                ?: ""
            ).trim()

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        debug {
            // Defensive: debug should never be minified/shrunk; a stripped Application class crashes on launch.
            isMinifyEnabled = false
            isShrinkResources = false
        }

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
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.google.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.mlkit.document.scanner)
    implementation(libs.google.places)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)

    implementation(libs.coil.compose)

    // Firebase (email auth + verification/reset emails)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.functions.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")

    // JVM "ghost" tests with in-memory Room DB.
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.room:room-testing:2.6.1")
}
