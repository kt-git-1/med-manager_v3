import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync
import java.net.URI
import java.util.Properties
import javax.imageio.ImageIO

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun runtimeConfig(name: String, default: String = ""): String =
    providers.environmentVariable(name).orNull ?: localProperties.getProperty(name) ?: default

val generatedRoleAssets = layout.buildDirectory.dir("generated/role-assets/res")
val releaseStoreFilePath = runtimeConfig("RELEASE_STORE_FILE")
val releaseStorePassword = runtimeConfig("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = runtimeConfig("RELEASE_KEY_ALIAS")
val releaseKeyPassword = runtimeConfig("RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all(String::isNotBlank)
val productionApiBaseUrl = runtimeConfig("API_BASE_URL", "https://www.okusuri-mimamori.com/")
val productionSupabaseUrl = runtimeConfig("SUPABASE_URL")
val productionSupabaseAnonKey = runtimeConfig("SUPABASE_ANON_KEY")
val productionFirebaseAppId = runtimeConfig("FIREBASE_APP_ID")
val productionFirebaseApiKey = runtimeConfig("FIREBASE_API_KEY")
val productionFirebaseProjectId = runtimeConfig("FIREBASE_PROJECT_ID")
val productionFirebaseSenderId = runtimeConfig("FIREBASE_SENDER_ID")
val productionEmailRedirectUrl = runtimeConfig(
    "EMAIL_CONFIRMATION_REDIRECT_URL",
    "https://www.okusuri-mimamori.com/auth/confirmed",
)
val productionBillingEnabled = runtimeConfig("BILLING_ENABLED", "false")
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

        buildConfigField("String", "API_BASE_URL", productionApiBaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_URL", productionSupabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", productionSupabaseAnonKey.asBuildConfigString())
        buildConfigField("boolean", "BILLING_ENABLED", productionBillingEnabled)
        buildConfigField("String", "FIREBASE_APP_ID", productionFirebaseAppId.asBuildConfigString())
        buildConfigField("String", "FIREBASE_API_KEY", productionFirebaseApiKey.asBuildConfigString())
        buildConfigField("String", "FIREBASE_PROJECT_ID", productionFirebaseProjectId.asBuildConfigString())
        buildConfigField("String", "FIREBASE_SENDER_ID", productionFirebaseSenderId.asBuildConfigString())
        buildConfigField(
            "String",
            "EMAIL_CONFIRMATION_REDIRECT_URL",
            productionEmailRedirectUrl.asBuildConfigString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
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

val verifyProductionSigning by tasks.registering {
    group = "verification"
    description = "Fails unless all production upload-signing values and the keystore are available."
    doLast {
        require(releaseSigningConfigured) {
            "Set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD in Git-ignored local.properties or CI secrets."
        }
        require(rootProject.file(releaseStoreFilePath).isFile) {
            "RELEASE_STORE_FILE does not exist: ${rootProject.file(releaseStoreFilePath)}"
        }
    }
}

val verifyProductionRuntime by tasks.registering {
    group = "verification"
    description = "Fails unless the Play artifact has complete, structurally valid production runtime configuration."
    doLast {
        fun httpsUri(value: String): URI? = runCatching { URI(value) }.getOrNull()
            ?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }

        val failures = buildList {
            val apiUri = httpsUri(productionApiBaseUrl)
            if (apiUri?.host != "www.okusuri-mimamori.com") add("API_BASE_URL must use the production HTTPS host")
            if (httpsUri(productionSupabaseUrl) == null) add("SUPABASE_URL is missing or is not HTTPS")
            if (productionSupabaseAnonKey.length < 20) add("SUPABASE_ANON_KEY is missing or malformed")
            if (!productionFirebaseAppId.matches(Regex("^1:[0-9]+:android:[A-Za-z0-9]+$"))) {
                add("FIREBASE_APP_ID is missing or malformed")
            }
            if (!productionFirebaseApiKey.startsWith("AIza") || productionFirebaseApiKey.length < 20) {
                add("FIREBASE_API_KEY is missing or malformed")
            }
            if (!productionFirebaseProjectId.matches(Regex("^[a-z][a-z0-9-]{4,}$"))) {
                add("FIREBASE_PROJECT_ID is missing or malformed")
            }
            if (!productionFirebaseSenderId.matches(Regex("^[0-9]+$"))) {
                add("FIREBASE_SENDER_ID is missing or malformed")
            }
            val redirectUri = httpsUri(productionEmailRedirectUrl)
            if (redirectUri?.host != "www.okusuri-mimamori.com" || redirectUri.path != "/auth/confirmed") {
                add("EMAIL_CONFIRMATION_REDIRECT_URL must use the production confirmation route")
            }
            if (productionBillingEnabled != "false") {
                add("BILLING_ENABLED must remain false for the approved initial Android release")
            }
        }
        require(failures.isEmpty()) {
            "Production runtime configuration is not Play-ready:\n - ${failures.joinToString("\n - ")}"
        }
    }
}

val verifyReleaseApkCompatibility by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Builds and inspects the Release APK for SDK, permission and 16 KB page-size compatibility."
    dependsOn("assembleRelease")
    commandLine("bash", rootProject.file("scripts/verify-release-apk.sh").absolutePath)
}

val verifyPlayStoreAssets by tasks.registering {
    group = "verification"
    description = "Validates Play listing text limits, phone screenshots, store icon and cross-platform icon parity."

    val listingFile = rootProject.file("../docs/android/play-store-listing-ja.md")
    val assetRoot = rootProject.file("../docs/android/play-store-assets")
    val phoneDirectory = assetRoot.resolve("phone-ja-JP")
    val storeIconFile = assetRoot.resolve("icon-512.png")
    val featureGraphicFile = assetRoot.resolve("feature-graphic-1024x500.jpg")
    val iosIconFile = rootProject.file("../ios/MedicationApp/Assets.xcassets/AppIcon.appiconset/med_1024_transparent.png")
    val androidForegroundFile = project.file("src/main/res/drawable-nodpi/ic_launcher_foreground.png")
    inputs.files(listingFile, storeIconFile, featureGraphicFile, iosIconFile, androidForegroundFile)
    inputs.dir(phoneDirectory)

    doLast {
        val expectedScreenshots = listOf(
            "01-mode-select.jpg",
            "02-patient-today.jpg",
            "03-patient-history.jpg",
            "04-caregiver-today.jpg",
            "05-caregiver-medications.jpg",
            "06-caregiver-inventory.jpg",
            "07-caregiver-history.jpg",
            "08-caregiver-settings.jpg",
        )
        val screenshotFiles = phoneDirectory.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            .orEmpty()
        require(screenshotFiles.map { it.name } == expectedScreenshots) {
            "Expected exactly the ordered Play phone screenshot set: ${expectedScreenshots.joinToString()}"
        }
        screenshotFiles.forEach { file ->
            val image = requireNotNull(ImageIO.read(file)) { "Unreadable screenshot: $file" }
            require(image.width == 1350 && image.height == 2400) {
                "Play phone screenshot must be 1350 x 2400: $file is ${image.width} x ${image.height}"
            }
            require(!image.colorModel.hasAlpha()) { "Play JPEG must not contain alpha: $file" }
        }

        val storeIcon = requireNotNull(ImageIO.read(storeIconFile)) { "Unreadable Play store icon" }
        require(storeIcon.width == 512 && storeIcon.height == 512) { "Play store icon must be 512 x 512" }
        require(storeIcon.colorModel.hasAlpha()) { "Play store icon must be a 32-bit RGBA PNG" }
        require(storeIconFile.length() <= 1_024 * 1_024) { "Play store icon must not exceed 1,024 KB" }

        val featureGraphic = requireNotNull(ImageIO.read(featureGraphicFile)) { "Unreadable Play feature graphic" }
        require(featureGraphic.width == 1024 && featureGraphic.height == 500) {
            "Play feature graphic must be 1024 x 500"
        }
        require(!featureGraphic.colorModel.hasAlpha()) { "Play feature graphic must not contain alpha" }

        val iosIcon = requireNotNull(ImageIO.read(iosIconFile)) { "Unreadable iOS source icon" }
        val androidForeground = requireNotNull(ImageIO.read(androidForegroundFile)) { "Unreadable Android launcher foreground" }
        require(iosIcon.width == androidForeground.width && iosIcon.height == androidForeground.height) {
            "Android launcher foreground dimensions drifted from the shipping iOS icon"
        }
        val width = iosIcon.width
        val height = iosIcon.height
        require(
            iosIcon.getRGB(0, 0, width, height, null, 0, width)
                .contentEquals(androidForeground.getRGB(0, 0, width, height, null, 0, width)),
        ) { "Android launcher foreground pixels drifted from the shipping iOS icon" }

        val textBlocks = Regex("```text\\R(.*?)\\R```", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(listingFile.readText())
            .map { it.groupValues[1] }
            .toList()
        require(textBlocks.size == 4) { "Expected app name, short description, full description and release-note blocks" }
        val limits = listOf(30, 80, 4_000, 500)
        textBlocks.zip(limits).forEachIndexed { index, (text, limit) ->
            require(text.codePointCount(0, text.length) <= limit) {
                "Play text block ${index + 1} exceeds its $limit-character limit"
            }
        }
    }
}

tasks.register("bundleSignedRelease") {
    group = "build"
    description = "Builds the Play upload AAB only after production runtime and signing configuration are verified."
    dependsOn(
        verifyProductionRuntime,
        verifyProductionSigning,
        verifyReleaseApkCompatibility,
        verifyPlayStoreAssets,
        "bundleRelease",
    )
}
verifyReleaseApkCompatibility.configure {
    mustRunAfter(verifyProductionRuntime, verifyProductionSigning)
}
tasks.matching { it.name == "bundleRelease" }.configureEach {
    mustRunAfter(verifyProductionRuntime, verifyProductionSigning, verifyReleaseApkCompatibility)
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
