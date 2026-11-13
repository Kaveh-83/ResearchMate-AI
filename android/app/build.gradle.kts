plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace="ai.researchmate.mobile"
 compileSdk=36
 defaultConfig {
  val api=providers.gradleProperty("API_BASE_URL").orElse("").get().replace("\\","\\\\").replace("\"","\\\"")
  buildConfigField("String","API_BASE_URL","\"$api\"")
  applicationId="ai.researchmate.mobile"
  minSdk=26
  targetSdk=36
  versionCode=7
  versionName="0.7.0"
 }
 buildTypes { release { isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 buildFeatures { compose=true; buildConfig=true }
}
dependencies {
 val composeBom=platform("androidx.compose:compose-bom:2026.06.00")
 implementation(composeBom)
 androidTestImplementation(composeBom)
 implementation("androidx.activity:activity-compose:1.13.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
