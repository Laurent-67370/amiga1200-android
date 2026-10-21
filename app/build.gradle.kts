import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version : celui de la compilation GitHub Actions (1 en local)
val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "io.github.laurent67370.amiga1200"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.laurent67370.amiga1200"
        minSdk = 28 // le cœur Amiberry vise Android 9 (API 28)
        targetSdk = 35
        versionCode = build
        versionName = "0.1.$build"
        ndk { abiFilters += "arm64-v8a" }
    }
    // Clé de test publique (debug.keystore, mots de passe « android ») : chaque nouvel APK s'installe par-dessus le précédent
    signingConfigs {
        getByName("debug") { storeFile = file("debug.keystore") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = false } }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
