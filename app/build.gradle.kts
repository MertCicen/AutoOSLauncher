plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.autoos.launcher"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }


    defaultConfig {
        applicationId = "com.autoos.launcher"
        minSdk = 23
        targetSdk = 36
        versionCode = 28
        versionName = "1.27"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)


    // lifecycleScope için (Thread → coroutine geçişi)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Coroutine desteği
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
