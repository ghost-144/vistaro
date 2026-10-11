import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vistaro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vistaro.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val localProperties = Properties().apply {
        val file = rootProject.file("secrets.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }
    val tmdbAccessToken = localProperties.getProperty("TMDB_ACCESS_TOKEN")
        ?: System.getenv("TMDB_ACCESS_TOKEN")
        ?: ""
    val escapedTmdbAccessToken = tmdbAccessToken.replace("\\", "\\\\").replace("\"", "\\\"")
    val mongoUri = localProperties.getProperty("MONGODB_URI")
        ?: System.getenv("MONGODB_URI")
        ?: ""
    val escapedMongoUri = mongoUri.replace("\\", "\\\\").replace("\"", "\\\"")
    defaultConfig {
        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"$escapedTmdbAccessToken\"")
        buildConfigField("String", "MONGODB_URI", "\"$escapedMongoUri\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/native-image/**"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/services/io.micrometer.**"
            excludes += "/META-INF/services/reactor.**"
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.mongodb.driver.kotlin)
    implementation(libs.mongodb.bson.kotlinx)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
