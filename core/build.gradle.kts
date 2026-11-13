import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Reads `secrets.defaults.properties` (committed placeholders), then
 * `secrets.properties` (git-ignored real values) on top of it.
 */
fun loadSecrets(): Properties {
    val props = Properties()
    listOf("secrets.defaults.properties", "secrets.properties").forEach { name ->
        val file = rootProject.file(name)
        if (file.exists()) file.inputStream().use { props.load(it) }
    }
    return props
}

val secrets = loadSecrets()
fun secret(key: String): String = "\"${secrets.getProperty(key, "").trim()}\""

android {
    namespace = "com.wibechat.demo.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "WIBECHAT_FUNCTIONS_BASE_URL", secret("WIBECHAT_FUNCTIONS_BASE_URL"))
        buildConfigField("String", "FIRESTORE_DATABASE_ID", secret("FIRESTORE_DATABASE_ID"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", secret("GOOGLE_WEB_CLIENT_ID"))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.firebase.firestore)
    api(libs.firebase.messaging)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
