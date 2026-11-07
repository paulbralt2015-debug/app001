plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.twins.videoplayer.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.twins.videoplayer.tv"
        minSdk = 21          // Android 5.0 : couvre les TV Coocaa sous Android 5 à 8
        targetSdk = 34
        versionCode = 1
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
