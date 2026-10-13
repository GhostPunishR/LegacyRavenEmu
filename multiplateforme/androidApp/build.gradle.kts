plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ravenemu.v3.android"
    compileSdk = 37
    defaultConfig {
        // Distinct preview ID: cannot overwrite the existing RavenEmu Android app.
        applicationId = "com.ravenemu.v3.preview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-preview"
    }
    buildFeatures { compose = true }
    externalNativeBuild {
        cmake { path = file("../native/CMakeLists.txt") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
