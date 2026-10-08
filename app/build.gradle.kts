plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.mendelev.mpos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mendelev.mpos"
        minSdk = 28
        targetSdk = 35
        versionCode = providers.environmentVariable("MPOS_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("MPOS_VERSION_NAME").orElse("0.1.0").get()
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        buildConfig = true
    }

    val releaseKeystore = providers.environmentVariable("MPOS_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("mposRelease") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("MPOS_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("MPOS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("MPOS_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("mposRelease")
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
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    kapt("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
