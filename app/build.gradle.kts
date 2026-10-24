plugins {
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mendelev.mpos"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.mendelev.mpos"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.environmentVariable("MPOS_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("MPOS_VERSION_NAME").orElse("0.1.0-dev").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val keystorePath = providers.environmentVariable("MPOS_KEYSTORE_PATH").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("mposRelease") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("MPOS_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("MPOS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("MPOS_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("mposRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    defaultConfig {
        buildConfigField("String", "UPDATE_CERT_SHA256", "\"d484cd0166df7f043f65c25e058aba4d3a2b59ea058e6f39948e3e5684b12da4\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// CI reports retain the generated schema even before the first schema is committed.
tasks.matching { it.name == "kspDebugKotlin" }.configureEach {
    doLast { copy { from("$projectDir/schemas"); into("$buildDir/reports/room-schemas") } }
}
