import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Werte kommen aus Gradle-Properties oder Umgebungsvariablen (GitHub-Secrets).
// Ohne Angabe gelten die Test-IDs von Google, damit nie versehentlich echte Werbung klickbar ist.
fun setting(name: String, fallback: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: fallback

val keystoreFile = setting("KEYSTORE_FILE", "")

android {
    namespace = "de.autoknigge.solarprognose"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.autoknigge.solarprognose"
        minSdk = 26
        targetSdk = 36
        versionCode = setting("VERSION_CODE", "1").toInt()
        versionName = setting("VERSION_NAME", "1.0")

        val admobApp = setting("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
        val admobBanner = setting("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/9214589741")
        val admobOpen = setting("ADMOB_APPOPEN_ID", "ca-app-pub-3940256099942544/9257395921")
        val apiBase = setting("API_BASE_URL", "")
        val appToken = setting("APP_TOKEN", "")

        manifestPlaceholders["admobAppId"] = admobApp
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBanner\"")
        buildConfigField("String", "ADMOB_APPOPEN_ID", "\"$admobOpen\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
        buildConfigField("String", "APP_TOKEN", "\"$appToken\"")
    }

    signingConfigs {
        if (keystoreFile.isNotBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = setting("KEYSTORE_PASSWORD", "")
                keyAlias = setting("KEY_ALIAS", "")
                keyPassword = setting("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile.isNotBlank()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.work:work-runtime-ktx:2.10.2")

    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
}
