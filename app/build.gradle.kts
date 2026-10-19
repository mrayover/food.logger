import java.util.Properties
plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
 id("com.google.devtools.ksp")
}
val secrets = Properties().apply { rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android {
 namespace = "com.foodlogger"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.foodlogger"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "1.0.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  val key = (secrets.getProperty("USDA_API_KEY") ?: System.getenv("USDA_API_KEY") ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
  buildConfigField("String", "USDA_API_KEY", "\"$key\"")
 }
 buildTypes { debug { applicationIdSuffix = ".debug" }; release { isMinifyEnabled = false } }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
 testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 ksp("androidx.room:room-compiler:2.6.1")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.journeyapps:zxing-android-embedded:4.3.0")
 implementation("com.google.mlkit:text-recognition:16.0.1")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.14.1")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
 testImplementation("org.json:json:20240303")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.room:room-testing:2.6.1")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
