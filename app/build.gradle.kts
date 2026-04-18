plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.k.selena"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.k.selena"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "HOTWORD", "\"Selena\"")

        // Picovoice access key — obtain a free key at https://console.picovoice.ai/
        // Set in gradle.properties as: PICOVOICE_ACCESS_KEY=<your_key>
        val picovoiceKey = project.findProperty("PICOVOICE_ACCESS_KEY") as String? ?: ""
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceKey\"")

        // Porcupine keyword sensitivity [0, 1] — higher = more sensitive but more false positives
        buildConfigField("float", "HOTWORD_SENSITIVITY", "0.5f")

        // Vosk model directory name inside assets (e.g. "vosk-model-small-en-us-0.22")
        buildConfigField("String", "VOSK_MODEL_NAME", "\"vosk-model-small-en-us-0.22\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Offline hotword detection — Porcupine (Picovoice)
    // Requires a free access key: https://console.picovoice.ai/
    implementation("ai.picovoice:porcupine-android:3.0.1")

    // Offline speech recognition — Vosk
    // Download a small model and place it in app/src/main/assets/<VOSK_MODEL_NAME>/
    // Models: https://alphacephei.com/vosk/models
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
