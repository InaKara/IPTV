import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

// Read literal KEY=value pairs without evaluating shell syntax or logging values.
// A public checkout without .env builds with empty login defaults.
fun parseLocalEnvironment(text: String): Map<String, String> {
    val localEnv = linkedMapOf<String, String>()
    text.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (line.isNotEmpty() && !line.startsWith("#")) {
            val separator = line.indexOf('=')
            require(separator > 0) { "Invalid .env entry at line ${index + 1}" }
            val key = line.substring(0, separator).trim()
            require(key.matches(Regex("[A-Z_][A-Z0-9_]*"))) { "Use uppercase .env keys at line ${index + 1}" }
            require(!localEnv.containsKey(key)) { "Duplicate .env key at line ${index + 1}" }
            var value = line.substring(separator + 1).trim()
            if (value.length >= 2 && ((value.first() == '"' && value.last() == '"') ||
                    (value.first() == '\'' && value.last() == '\''))) {
                value = value.substring(1, value.length - 1)
            }
            localEnv[key] = value
        }
    }
    return localEnv
}

val localEnv = parseLocalEnvironment(providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText.orElse("").get())

tasks.register("verifyLocalEnvironment") {
    inputs.file(rootProject.file("scripts/env-fixtures.json"))
    doLast {
        val fixtures = groovy.json.JsonSlurper().parse(rootProject.file("scripts/env-fixtures.json")) as List<*>
        val results = fixtures.map { item ->
            val fixture = item as Map<*, *>
            val result = runCatching { parseLocalEnvironment(fixture["text"] as String) }
            val passed = if (fixture["invalid"] == true) result.isFailure
                else result.isSuccess && result.getOrNull() == fixture["expected"]
            mapOf("key" to "env-gradle#${fixture["name"]}", "status" to if (passed) "Passed" else "Failed")
        }
        providers.gradleProperty("smokeReportDirectory").orNull?.let { path ->
            file(path).resolve("environment-gradle.json").writeText(groovy.json.JsonOutput.toJson(results))
        }
        check(results.isNotEmpty() && results.all { it["status"] == "Passed" }) { "Literal environment fixtures failed" }
        logger.lifecycle("Gradle literal environment fixtures passed (${fixtures.size})")
    }
}

// Only smoke runs force fresh test execution; normal compilation remains incremental.
afterEvaluate {
    tasks.withType<Test>().configureEach {
        providers.gradleProperty("smokeReportDirectory").orNull?.let { path ->
            if (name == "testDebugUnitTest") {
                reports.junitXml.outputLocation.set(file(path).resolve("app-tests"))
                outputs.upToDateWhen { false }
            }
        }
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
        applicationId = "com.inakara.aypitivi"
        minSdk = 26 // Fire TV Stick 4K Max supports Android 9 (API 28) but safe to target 26+
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.2"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro",
                rootProject.file("third_party/media3-ffmpeg/proguard-rules.txt"))
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
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("main") {
        java.srcDir(rootProject.file("third_party/media3-ffmpeg/java"))
        jniLibs.srcDir(layout.buildDirectory.dir("generated/ffmpeg/jniLibs"))
    }
}

// Native generation is explicit, not a network download during Gradle configuration.
// Refuse a silently missing/stale fallback after a clean checkout or native source edit.
val verifyFfmpegNative by tasks.registering {
    doLast {
        val manifest = layout.buildDirectory.file("generated/ffmpeg/manifest.json").get().asFile
        check(manifest.isFile) { "Prepare AU-1 native libraries first; see docs/operations/FFMPEG_BUILD.md" }
        val data = groovy.json.JsonSlurper().parse(manifest) as Map<*, *>
        val inputs = data["inputs"] as Map<*, *>
        val requiredInputs = listOf("scripts/ffmpeg-sources.json", "scripts/build-ffmpeg.sh",
            "scripts/prepare-ffmpeg.py", "scripts/verify-ffmpeg.py") +
            rootProject.file("third_party/media3-ffmpeg").walkTopDown().filter { it.isFile }
                .map { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }.toList()
        check(inputs.keys == requiredInputs.toSet()) { "Native source list changed; rebuild AU-1" }
        fun digest(file: File) = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        inputs.forEach { (path, expected) ->
            check(digest(rootProject.file(path.toString())) == expected) { "Native input changed: $path; rebuild AU-1" }
        }
        val libraries = data["libraries"] as Map<*, *>
        check(libraries.keys == setOf("armeabi-v7a", "arm64-v8a")) { "Required ARM decoder ABIs missing" }
        libraries.forEach { (abi, expected) ->
            val library = layout.buildDirectory.file("generated/ffmpeg/jniLibs/$abi/libffmpegJNI.so").get().asFile
            check(library.isFile && digest(library) == expected) { "Decoder missing/changed for $abi; rebuild AU-1" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyFfmpegNative) }

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose (BOM)
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    
    // Dependency Injection (Hilt)
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Networking (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Video Player (Media3 ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    // Official Media3 1.2.1 extension's compile-only nullness annotations.
    compileOnly("org.checkerframework:checker-qual:3.13.0")
    
    // Image Loading (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")
    
    // JSON (Gson)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // 4.12 adds native graphics support on Windows; required for real image/pixel checks.
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}
