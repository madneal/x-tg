import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun secret(name: String): String =
    providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: ""

val releaseStoreFile = secret("release.storeFile")
val releaseStorePassword = secret("release.storePassword")
val releaseKeyAlias = secret("release.keyAlias")
val releaseKeyPassword = secret("release.keyPassword")

android {
    namespace = "com.example.tgclient"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val missing = listOf(
                "release.storeFile" to releaseStoreFile,
                "release.storePassword" to releaseStorePassword,
                "release.keyAlias" to releaseKeyAlias,
                "release.keyPassword" to releaseKeyPassword,
            ).filter { it.second.isBlank() }.map { it.first }
            if (missing.isNotEmpty()) {
                throw GradleException("Release signing is required. Missing: ${missing.joinToString()}")
            }
            val keystore = rootProject.file(releaseStoreFile)
            if (!keystore.isFile) {
                throw GradleException("Release keystore does not exist: ${keystore.absolutePath}")
            }
            storeFile = keystore
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.example.tgclient"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "0.1.9"

        buildConfigField("int", "TELEGRAM_API_ID", secret("telegram.apiId").ifBlank { "0" })
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${secret("telegram.apiHash").replace("\"", "\\\"")}\"")
        buildConfigField("String", "TDLIB_VERSION", "\"1.8.56-RC9\"")
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // TDLib's native Android artifact and Kotlin coroutine helpers.
    implementation("io.github.tdlibx:td-android:1.8.56-RC9")
    implementation("io.github.tdlibx:td-ktx-android:1.8.56-RC9")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
