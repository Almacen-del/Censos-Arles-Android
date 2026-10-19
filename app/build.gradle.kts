import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

// Configure each developer checkout through the ignored local.properties file.
val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
fun configLiteral(name: String, fallback: String): String = "\"" +
    localConfig.getProperty(name, fallback).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.conteoinventarioplantacionarlessas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.conteoinventarioplantacionarlessas"
        minSdk = 24
        targetSdk = 37
        versionCode = 8
        versionName = "1.4.3-orden-lineas"
        buildConfigField("String", "SUPABASE_URL", configLiteral("SUPABASE_URL", "https://your-project.supabase.co"))
        buildConfigField("String", "SUPABASE_KEY", configLiteral("SUPABASE_PUBLISHABLE_KEY", "your-publishable-key"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Instrumented tests must run in a separate installed application.
            if (providers.gradleProperty("verificationBuild").orNull == "true") {
                applicationIdSuffix = ".verification"
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Custom added
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    kapt(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// The device test runner removes its target app after testing. Protect the field app.
val isolatedVerification = providers.gradleProperty("verificationBuild").orNull == "true"
tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        doFirst {
            check(isolatedVerification) {
                "Run device tests with -PverificationBuild=true to protect the installed field app."
            }
        }
    }
}
