plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionCode = providers.gradleProperty("releaseVersionCode").orElse("2").get().toInt()
val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("1.1.0").get()
require(releaseVersionCode > 1) { "releaseVersionCode must be greater than 1" }
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?").matches(releaseVersionName)) {
    "releaseVersionName must be a semantic version"
}
val signingValues = listOf("BATTERYFOX_KEYSTORE_PATH", "BATTERYFOX_STORE_PASSWORD", "BATTERYFOX_KEY_ALIAS", "BATTERYFOX_KEY_PASSWORD")
    .associateWith { System.getenv(it).orEmpty() }
val hasReleaseSigning = signingValues.values.all { it.isNotBlank() }
require(signingValues.values.all { it.isBlank() } || hasReleaseSigning) { "Release signing requires all four environment variables" }
if (providers.gradleProperty("requireReleaseSigning").orNull == "true") {
    require(hasReleaseSigning) { "Release signing secrets are missing" }
}

android {
    namespace = "com.batteryfox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.batteryfox.app"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingValues.getValue("BATTERYFOX_KEYSTORE_PATH"))
                storePassword = signingValues.getValue("BATTERYFOX_STORE_PASSWORD")
                keyAlias = signingValues.getValue("BATTERYFOX_KEY_ALIAS")
                keyPassword = signingValues.getValue("BATTERYFOX_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")

    // Jetpack Compose & Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
