import java.util.Properties

// The installable app: screens, navigation and the manual dependency container.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from keystore.properties (local, never committed) or from CI environment variables.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "FITOS_KEYSTORE_PATH")

android {
    namespace = "app.fitos.personal"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.fitos.personal"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "FITOS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "FITOS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "FITOS_KEY_PASSWORD")
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
        buildConfig = true
    }
    androidResources {
        // Persian only: drops the other languages that libraries ship.
        localeFilters += "fa"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        // Library upgrades are planned per stage, not driven by lint's "newer version" notices.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// UI tests need the debug-only test manifest, so unit tests run on the debug variant only.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.hostTests.forEach { (_, test) -> test.enable = false } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
