plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.caesartv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.caesartv"
        minSdk = 21
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.8"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
        
    }

    dependencies {
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
        implementation("androidx.core:core-ktx:1.12.0")
    }
}

dependencies {

    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.socket.io.client)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.gson)
    implementation(libs.okhttp)

    implementation(libs.androidx.media)

//    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

}