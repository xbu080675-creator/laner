plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val lolEsportsApiKey = providers.environmentVariable("LOL_ESPORTS_API_KEY")
    .orElse(providers.gradleProperty("lolEsportsApiKey"))
    .getOrElse("")
val escapedLolEsportsApiKey = lolEsportsApiKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val lplTjstatsAuth = providers.environmentVariable("LPL_TJSTATS_AUTH")
    .orElse(providers.gradleProperty("lplTjstatsAuth"))
    .getOrElse("")
val escapedLplTjstatsAuth = lplTjstatsAuth
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.laner.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riftlab.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0-dev.2"
        buildConfigField("String", "LOL_ESPORTS_API_KEY", "\"$escapedLolEsportsApiKey\"")
        buildConfigField("String", "LPL_TJSTATS_AUTH", "\"$escapedLplTjstatsAuth\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
