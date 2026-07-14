import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync
import java.util.Properties

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun runtimeConfig(name: String, default: String = ""): String =
    providers.environmentVariable(name).orNull ?: localProperties.getProperty(name) ?: default

val generatedRoleAssets = layout.buildDirectory.dir("generated/role-assets/res")
val syncRoleAssets by tasks.registering(Sync::class) {
    into(generatedRoleAssets)
    from(rootProject.file("../ios/MedicationApp/Assets.xcassets/RolePatient.imageset/role-patient.png")) {
        into("drawable-nodpi")
        rename { "role_patient.png" }
    }
    from(rootProject.file("../ios/MedicationApp/Assets.xcassets/RoleFamily.imageset/role-family.png")) {
        into("drawable-nodpi")
        rename { "role_family.png" }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.afterlifearchive.medmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.afterlifearchive.medmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", runtimeConfig("API_BASE_URL", "https://www.okusuri-mimamori.com/").asBuildConfigString())
        buildConfigField("String", "SUPABASE_URL", runtimeConfig("SUPABASE_URL").asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", runtimeConfig("SUPABASE_ANON_KEY").asBuildConfigString())
        buildConfigField("boolean", "BILLING_ENABLED", runtimeConfig("BILLING_ENABLED", "false"))
        buildConfigField(
            "String",
            "EMAIL_CONFIRMATION_REDIRECT_URL",
            runtimeConfig("EMAIL_CONFIRMATION_REDIRECT_URL", "https://www.okusuri-mimamori.com/auth/confirmed").asBuildConfigString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    sourceSets["main"].res.srcDir(generatedRoleAssets)
}

tasks.named("preBuild").configure { dependsOn(syncRoleAssets) }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
