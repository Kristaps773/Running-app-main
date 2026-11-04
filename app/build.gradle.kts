import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry.android)
    alias(libs.plugins.compose.preview)
}

// Robolectric NATIVE graphics on Windows: SDK 36 aborts; SDK 35 access-violates in
// libandroid_runtime.dll. Pin 34 (same as many Compose Preview Windows setups).
composePreview {
    sdkVersion.set(34)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String, default: String = ""): String =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "com.orienteer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orienteer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "1.3.24"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SENTRY_DSN", "\"${localProp("SENTRY_DSN")}\"")
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"${localProp(
                "PRIVACY_POLICY_URL",
                "https://kristaps773.github.io/Running-app-main/privacy-policy.html"
            )}\""
        )
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProp("STORE_FILE")
            if (storeFilePath.isNotEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = localProp("STORE_PASSWORD")
                keyAlias = localProp("KEY_ALIAS")
                keyPassword = localProp("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isDebuggable = true
            // Side-by-side with Play Store install (different signing key).
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "OrienteerRun Debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            val matrixFlag = System.getProperty("orienteer.routeMatrix")
                ?: System.getenv("ORIENTEER_ROUTE_MATRIX")
                ?: ""
            it.systemProperty("orienteer.routeMatrix", matrixFlag)
        }
    }
}

sentry {
    // Avoid double-adding the SDK; we declare sentry-android explicitly.
    autoInstallation.enabled.set(false)
    includeProguardMapping.set(true)
    // Upload only when an auth token is present (release builds).
    autoUploadProguardMapping.set(localProp("SENTRY_AUTH_TOKEN").isNotEmpty())
    localProp("SENTRY_ORG").takeIf { it.isNotEmpty() }?.let { org.set(it) }
    localProp("SENTRY_PROJECT").takeIf { it.isNotEmpty() }?.let { projectName.set(it) }
    localProp("SENTRY_AUTH_TOKEN").takeIf { it.isNotEmpty() }?.let { authToken.set(it) }
    // Crash capture still works via the SDK init in OrienteerApp.
    tracingInstrumentation {
        enabled.set(false)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.media)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Location
    implementation(libs.play.services.location)

    // Maps
    implementation(libs.osmdroid)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Crash reporting
    implementation(libs.sentry.android)

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
