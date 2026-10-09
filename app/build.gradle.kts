plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.setpocket"
    compileSdk = 35
    defaultConfig { applicationId = "dev.setpocket"; minSdk = 26; targetSdk = 35; versionCode = 6; versionName = "0.5.0" }
    if (rootProject.file("dev-signing/debug.keystore").exists()) signingConfigs { getByName("debug") {
        storeFile = rootProject.file("dev-signing/debug.keystore")
        storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
