plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ameerscreens.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ameerscreens.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-car-projection"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.car.app:app:1.4.0")
}