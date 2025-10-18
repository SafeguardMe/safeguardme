import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.safeguardme.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safeguardme.app"
        minSdk = 26  // Increased for biometric support
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Security: Prevent screenshots and screen recording
        manifestPlaceholders["allowBackup"] = "false"
        manifestPlaceholders["allowClearUserData"] = "false"

        // ProGuard configuration for release builds
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        // OpenAI configuration (values pulled from gradle.properties/local.properties at build time)
        val localProps = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                file.inputStream().use { load(it) }
            }
        }

        fun sanitize(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        }

        val openAiKey = (project.findProperty("OPENAI_API_KEY") as? String)
            ?: localProps.getProperty("OPENAI_API_KEY")
            ?: ""
        buildConfigField("String", "OPENAI_API_KEY", "\"${sanitize(openAiKey)}\"")

        val openAiModel = (project.findProperty("OPENAI_MODEL") as? String)
            ?: localProps.getProperty("OPENAI_MODEL")
            ?: "gpt-4o-mini"
        buildConfigField("String", "OPENAI_MODEL", "\"${sanitize(openAiModel)}\"")

        val openAiBaseUrl = (project.findProperty("OPENAI_BASE_URL") as? String)
            ?: localProps.getProperty("OPENAI_BASE_URL")
            ?: "https://api.openai.com/v1"
        buildConfigField("String", "OPENAI_BASE_URL", "\"${sanitize(openAiBaseUrl)}\"")
    }

    signingConfigs {
        create("release") {
            // TODO: Configure proper release signing
            // For now, using debug config for development
            storeFile = signingConfigs.getByName("debug").storeFile
            storePassword = signingConfigs.getByName("debug").storePassword
            keyAlias = signingConfigs.getByName("debug").keyAlias
            keyPassword = signingConfigs.getByName("debug").keyPassword
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            versionNameSuffix = "-debug"
            //applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "SafeguardMe Debug"
        }

        release {
            isMinifyEnabled = false
            isDebuggable = false
            isShrinkResources = false

            // Security: Enable obfuscation and optimization
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appName"] = "SafeguardMe"

            // Additional security for release builds
            buildConfigField("boolean", "IS_PRODUCTION", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "**/kotlin/**",
                "**/*.kotlin_metadata",
                "META-INF/versions/**"
            )
        }
    }

    // Security: Additional configuration for sensitive apps
    buildFeatures {
        viewBinding = false
        dataBinding = false
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material.icons.extended)

    // Compose BOM and UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt for Dependency Injection
    implementation(libs.hilt.android){
        exclude(group = "com.intellij", module = "annotations")
    }
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.room.compiler)
    kapt(libs.hilt.compiler){
        exclude(group = "com.intellij", module = "annotations")
    }
    // Apply to other dependencies

    // Firebase BOM and services
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Security Libraries
    implementation(libs.biometric)
    implementation(libs.security.crypto)

    // WorkManager (for background security tasks)
    implementation(libs.work.runtime.ktx)

    // Image loading
    implementation(libs.coil.compose)

    // Permissions handling
    implementation(libs.accompanist.permissions)

    // Future: Camera integration
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // DataStore for settings
    implementation(libs.datastore.preferences)

    // UUID Generation (Fixed: Using proper multiplatform UUID library)
    implementation(libs.uuid)

    // JSON processing (for API communication)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.exifinterface)

    implementation(libs.google.play.services.location) // Or libs.google.playservices.location if your alias was google-playservices-location

    implementation(libs.google.gson) // This uses the alias from libs.versions.toml
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.compiler)

    // Android Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)

    // Debug implementations
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Hilt configuration
kapt {
    correctErrorTypes = true
    useBuildCache = true
}

// Additional configurations for security and performance
configurations.all {
    resolutionStrategy {
        // Force single annotations library
        force("org.jetbrains:annotations:23.0.0")

        // Exclude conflicting annotations
        exclude(group = "com.intellij", module = "annotations")

        // Force consistent versions for security
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}")

        // Exclude potential security vulnerabilities
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }
}
