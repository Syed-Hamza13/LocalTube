import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * -Pslim=true  -> no FFmpeg bundle (~35 MB smaller, no 1080p merging, no mp3 encode)
 * Release signing is read from env vars / keystore.properties; when absent the release
 * build is signed with the debug key so CI always produces an installable APK.
 */
val slimBuild: Boolean = (findProperty("slim") as? String)?.toBoolean() == true

val keystoreProps = file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}
val releaseKeystore = System.getenv("LOCALTUBE_KEYSTORE")?.let(::File)
    ?: keystoreProps?.getProperty("storeFile")?.let(::File)

android {
    namespace = "dev.hamza.localtube"
    // AGP 8.13.2's supported/recommended platform. The dependency line in libs.versions.toml is
    // pinned so nothing demands API 37; raising this without AGP 9 only moves the failure.
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.hamza.localtube"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Single ABI for the target device (Realme 11x 5G). Never ship other ABIs here.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("boolean", "WITH_FFMPEG", (!slimBuild).toString())

        // Room schema export is disabled on purpose: keeps the APK free of JSON assets.
    }

    signingConfigs {
        if (releaseKeystore?.exists() == true) {
            create("cloud") {
                storeFile = releaseKeystore
                storePassword = System.getenv("LOCALTUBE_KEY_PASSWORD")
                    ?: keystoreProps?.getProperty("storePassword")
                keyAlias = System.getenv("LOCALTUBE_KEY_ALIAS")
                    ?: keystoreProps?.getProperty("keyAlias")
                keyPassword = System.getenv("LOCALTUBE_KEY_PASSWORD")
                    ?: keystoreProps?.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("cloud") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Drops the Play "dependency metadata" blob (~50 KB) that personal builds do not need.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCY_METADATA",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "**/*.proto",
            )
        }
        jniLibs {
            // youtubedl-android requires the bundled libpython/libffmpeg to be extracted at install.
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.coil.compose)

    // yt-dlp + Python 3 runtime are bundled inside this AAR (arm64 slice = 14.5 MB).
    implementation(libs.ytdlp.library)
    if (!slimBuild) {
        // FFmpeg bundle (arm64 slice = 34.5 MB): needed for merging + mp3/m4a extraction.
        implementation(libs.ytdlp.ffmpeg)
    }
}
