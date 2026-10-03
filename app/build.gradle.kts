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
val releaseKeystore = releaseStoreFile.takeIf(String::isNotBlank)?.let(rootProject::file)
val releaseSigningReady = listOf(releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all(String::isNotBlank) &&
    releaseKeystore?.isFile == true

android {
    namespace = "com.example.tgclient"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // Debug builds and unit tests must not depend on a release signing
            // file. Release packaging below performs a strict validation so a
            // release APK can never silently become unsigned.
            if (releaseSigningReady) {
                storeFile = requireNotNull(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.tgclient"
        minSdk = 29
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 23
        versionName = "0.1.22"

        buildConfigField("int", "TELEGRAM_API_ID", secret("telegram.apiId").ifBlank { "0" })
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${secret("telegram.apiHash").replace("\"", "\\\"")}\"")
        buildConfigField("String", "TDLIB_VERSION", "\"1.8.56-RC9\"")
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            // Production APKs must never be pointed at Telegram's disposable test DCs.
            buildConfigField("boolean", "TELEGRAM_USE_TEST_DC", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // Enable only for explicit QA runs: ./gradlew ... -Ptelegram.useTestDc=true
            val useTestDc = providers.gradleProperty("telegram.useTestDc").orNull.equals("true", ignoreCase = true)
            buildConfigField("boolean", "TELEGRAM_USE_TEST_DC", useTestDc.toString())
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            // A universal APK contains every TDLib native library and is almost
            // three times larger. Publish one signed APK per supported ABI so
            // devices download only the native library they can execute.
            isUniversalApk = false
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
        // Compress TDLib's native libraries inside the APK. Android extracts
        // them during installation, trading a small install-time cost for a
        // substantially smaller download.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.configureEach {
    if (name == "packageRelease" || name == "signReleaseBundle") {
        if (!releaseSigningReady) {
            // A stale APK from a previous signed build must not make an
            // unconfigured release assemble appear successful.
            outputs.upToDateWhen { false }
        }
        doFirst {
            if (!releaseSigningReady) {
                throw GradleException(
                    "Release signing is required. Check release.storeFile, release.storePassword, " +
                        "release.keyAlias, and release.keyPassword in local.properties or CI secrets.",
                )
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // TDLib's native Android artifact and Kotlin coroutine helpers.
    implementation("io.github.tdlibx:td-android:1.8.56-RC9")
    implementation("io.github.tdlibx:td-ktx-android:1.8.56-RC9")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
