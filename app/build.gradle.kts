import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "app.gallerydl"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.gallerydl"
        minSdk = 29 // scoped storage: MediaStore RELATIVE_PATH works without storage permissions
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Chaquopy bundles a Python runtime per ABI, so each build keeps only the ones its devices use.
    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        // For Android TV: a launcher entry with a banner for the TV home screen, and no touchscreen required (see
        // src/tv/AndroidManifest.xml). Many TVs and projectors run 32-bit software, so this build adds 32-bit ARM.
        create("tv") {
            dimension = "device"
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
        }
    }

    signingConfigs {
        // Credentials live in keystore.properties (git-ignored); without it the release build stays unsigned.
        val props = Properties().apply {
            rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        if (props.isNotEmpty()) {
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            install("gallery-dl==1.32.18")
        }
    }
    productFlavors {
        // Chaquopy has no 32-bit build of Python 3.12, so the TV build uses 3.11 (needs python3.11 on the build machine).
        getByName("tv") { version = "3.11" }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)
}
