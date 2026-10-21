plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

// Read literal KEY=value pairs without evaluating shell syntax or logging values.
// A public checkout without .env builds with empty login defaults.
val localEnv = linkedMapOf<String, String>()
val localEnvText = providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText.orElse("").get()
localEnvText.lineSequence().forEachIndexed { index, rawLine ->
    val line = rawLine.trim().removePrefix("\uFEFF")
    if (line.isNotEmpty() && !line.startsWith("#")) {
        val separator = line.indexOf('=')
        require(separator > 0) { "Invalid .env entry at line ${index + 1}" }
        val key = line.substring(0, separator).trim()
        require(key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid .env key at line ${index + 1}" }
        require(!localEnv.containsKey(key)) { "Duplicate .env key at line ${index + 1}" }
        var value = line.substring(separator + 1).trim()
        if (value.length >= 2 && ((value.first() == '"' && value.last() == '"') ||
                (value.first() == '\'' && value.last() == '\''))) {
            value = value.substring(1, value.length - 1)
        }
        localEnv[key] = value
    }
}

fun quotedBuildValue(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

val localLoginDefaults = mapOf(
    "DEFAULT_SERVER_URL" to localEnv["IPTV_SERVER_URL"].orEmpty().trim().trimEnd('/'),
    "DEFAULT_USERNAME" to localEnv["IPTV_USERNAME"].orEmpty(),
    "DEFAULT_PASSWORD" to localEnv["IPTV_PASSWORD"].orEmpty(),
    "LEGACY_SERVER_URLS" to localEnv["IPTV_LEGACY_SERVER_URLS"].orEmpty()
)

android {
    namespace = "com.example.iptv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.iptv"
        minSdk = 26 // Fire TV Stick 4K Max supports Android 9 (API 28) but safe to target 26+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        localLoginDefaults.keys.forEach { name ->
            buildConfigField("String", name, quotedBuildValue(""))
        }
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            localLoginDefaults.forEach { (name, value) ->
                buildConfigField("String", name, quotedBuildValue(value))
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose (BOM)
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation:1.5.4")
    implementation("androidx.compose.animation:animation-core:1.5.4")
    
    // Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Dependency Injection (Hilt)
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Networking (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Video Player (Media3 ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    
    // Image Loading (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // JSON (Gson)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}
