import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ctify.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ctify.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "3.1.0"
        vectorDrawables { useSupportLibrary = true }
        // Last.fm key for genres and creators: local.properties, else the environment; blank hides those parts
        val lastFmKey = rootProject.file("local.properties").takeIf { it.exists() }
            ?.let { f -> Properties().apply { f.inputStream().use(::load) }.getProperty("LASTFM_API_KEY") }
            ?: System.getenv("LASTFM_API_KEY").orEmpty()
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastFmKey.trim().filter(Char::isLetterOrDigit)}\"")
    }

    signingConfigs {
        // one stable key from the environment (CI); without it release builds stay unsigned
        System.getenv("CTIFY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("CTIFY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CTIFY_KEY_ALIAS")
                keyPassword = System.getenv("CTIFY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.materialkolor.material3)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.hls)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.levyra.extractor)
    implementation(libs.work.runtime.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    testImplementation(libs.junit)
    // real org.json for parsing tests; the android.jar copy is a stub
    testImplementation(libs.org.json)
}
