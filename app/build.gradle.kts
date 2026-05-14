import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "com.atlyn.subterranea"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atlyn.subterranea"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Phase O-3: Azure Application Insights connection string is read from
        // the APPINSIGHTS_CONNECTION_STRING env var (loaded by the AKV helper)
        // and exposed via BuildConfig so the Telemetry initialiser can read it
        // without depending on env vars at runtime. Empty string disables
        // telemetry — useful for unsigned local builds.
        buildConfigField(
            "String",
            "APPINSIGHTS_CONNECTION_STRING",
            "\"${System.getenv("APPINSIGHTS_CONNECTION_STRING") ?: ""}\""
        )
    }

    signingConfigs {
        create("release") {
            // Keystore path is configurable via KEYSTORE_PATH env var; defaults to
            // ../keystore (which is .gitignored). The keystore file is NOT committed
            // to the repository — see SECURITY.md for the rotation procedure.
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "../keystore"
            storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "key0"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
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
            // Sign release builds when keystore is configured. The keystore file
            // is intentionally not committed; place it at ../keystore (or set
            // KEYSTORE_PATH) and provide KEYSTORE_PASSWORD / KEY_PASSWORD env vars.
            // Local builds without those env vars will silently produce an unsigned
            // release; CI will use real values.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
