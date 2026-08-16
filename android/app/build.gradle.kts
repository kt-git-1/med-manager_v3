import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.tasks.Sync
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile
import javax.imageio.ImageIO

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun runtimeConfig(name: String, default: String = ""): String =
    providers.environmentVariable(name).orNull ?: localProperties.getProperty(name) ?: default

fun decodeBase64Url(value: String): String? {
    if (!value.matches(Regex("^[A-Za-z0-9_-]+$"))) return null
    val padded = value + "=".repeat((4 - value.length % 4) % 4)
    return runCatching { Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8) }.getOrNull()
}

fun legacySupabaseRole(value: String): String? {
    val segments = value.split('.')
    if (segments.size != 3 || segments.any(String::isBlank)) return null
    val header = decodeBase64Url(segments[0]) ?: return null
    val payload = decodeBase64Url(segments[1]) ?: return null
    if (!Regex("\"alg\"\\s*:").containsMatchIn(header)) return null
    val issuer = Regex("\"iss\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
    if (issuer != "supabase") return null
    return Regex("\"role\"\\s*:\\s*\"([^\"]+)\"")
        .findAll(payload)
        .map { it.groupValues[1] }
        .toList()
        .singleOrNull()
}

fun isClientSafeSupabaseKey(value: String): Boolean {
    if (value.matches(Regex("^sb_publishable_[A-Za-z0-9_-]{20,}$"))) return true
    return legacySupabaseRole(value) == "anon"
}

fun syntheticLegacySupabaseKey(role: String, issuer: String = "supabase"): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray())
    val payload = encoder.encodeToString("{\"iss\":\"$issuer\",\"role\":\"$role\"}".toByteArray())
    return "$header.$payload.synthetic-signature"
}

fun normalizedSha256Fingerprint(value: String): String? = value
    .replace(Regex("[:\\s]"), "")
    .lowercase()
    .takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }

fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun forbiddenReleaseSdkReason(coordinate: String): String? = when {
    coordinate.startsWith("com.android.billingclient:") ->
        "Google Play Billing is not approved while BILLING_ENABLED=false"
    coordinate.startsWith("com.android.installreferrer:") ->
        "Google Play Install Referrer is not approved"
    coordinate.startsWith("com.google.android.gms:play-services-ads:") ||
        coordinate.startsWith("com.google.android.gms:play-services-ads-") &&
        !coordinate.startsWith("com.google.android.gms:play-services-ads-identifier:") ->
        "Google Mobile Ads is not approved"
    coordinate.startsWith("com.google.android.gms:play-services-tagmanager:") ->
        "Google Tag Manager is not approved"
    coordinate.startsWith("com.google.firebase:firebase-crashlytics") ->
        "Firebase Crashlytics is not declared or approved"
    coordinate.startsWith("com.google.firebase:firebase-perf") ->
        "Firebase Performance Monitoring is not declared or approved"
    coordinate.startsWith("com.appsflyer:") -> "AppsFlyer attribution is not approved"
    coordinate.startsWith("com.adjust.sdk:") -> "Adjust attribution is not approved"
    coordinate.startsWith("com.facebook.android:") -> "Meta SDK is not approved"
    coordinate.startsWith("io.sentry:") -> "Sentry telemetry is not approved"
    coordinate.startsWith("com.mixpanel.android:") -> "Mixpanel analytics is not approved"
    coordinate.startsWith("com.amplitude:") -> "Amplitude analytics is not approved"
    coordinate.startsWith("com.segment.analytics:") || coordinate.startsWith("com.segment.analytics.android:") ->
        "Segment analytics is not approved"
    else -> null
}

fun releaseBundleStructureFailures(entries: Set<String>): List<String> = buildList {
    val requiredEntries = setOf(
        "BundleConfig.pb",
        "base/manifest/AndroidManifest.xml",
        "base/dex/classes.dex",
    )
    requiredEntries.minus(entries).sorted().forEach { add("Missing required AAB entry: $it") }

    val moduleManifests = entries
        .filter { it.matches(Regex("^[^/]+/manifest/AndroidManifest\\.xml$")) }
        .sorted()
    if (moduleManifests != listOf("base/manifest/AndroidManifest.xml")) {
        add("Expected exactly the reviewed base module manifest: ${moduleManifests.joinToString()}")
    }

    val forbiddenNames = setOf(
        ".env",
        "google-services.json",
        "local.properties",
        "secrets.properties",
        "service-account.json",
    )
    val forbiddenExtensions = setOf("jks", "keystore", "p12", "pem", "key")
    entries.sorted().forEach { entry ->
        val fileName = entry.substringAfterLast('/').lowercase()
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        if (fileName in forbiddenNames || extension in forbiddenExtensions) {
            add("Forbidden private configuration/key entry in AAB: $entry")
        }
    }
}

val generatedRoleAssets = layout.buildDirectory.dir("generated/role-assets/res")
val releaseApplicationId = "com.afterlifearchive.medmanager"
val releaseVersionCode = 1
val releaseVersionName = "1.0.6"
val releaseMinSdk = 26
val releaseTargetSdk = 35
val publishedIosApiBaselineSha = "432b34c064d70a59c20753116b39390bee2c1cd0"
val releaseStoreFilePath = runtimeConfig("RELEASE_STORE_FILE")
val releaseStorePassword = runtimeConfig("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = runtimeConfig("RELEASE_KEY_ALIAS")
val releaseKeyPassword = runtimeConfig("RELEASE_KEY_PASSWORD")
val playUploadCertSha256 = runtimeConfig("PLAY_UPLOAD_CERT_SHA256")
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

fun firebaseRuntimeFailures(): List<String> = buildList {
    val appIdMatch = Regex("^1:([0-9]+):android:[A-Za-z0-9]+$").matchEntire(productionFirebaseAppId)
    if (appIdMatch == null) add("FIREBASE_APP_ID is missing or malformed")
    if (!productionFirebaseApiKey.startsWith("AIza") || productionFirebaseApiKey.length < 20) {
        add("FIREBASE_API_KEY is missing or malformed")
    }
    if (!productionFirebaseProjectId.matches(Regex("^[a-z][a-z0-9-]{4,}$"))) {
        add("FIREBASE_PROJECT_ID is missing or malformed")
    }
    if (!productionFirebaseSenderId.matches(Regex("^[0-9]+$"))) {
        add("FIREBASE_SENDER_ID is missing or malformed")
    }
    if (appIdMatch != null && appIdMatch.groupValues[1] != productionFirebaseSenderId) {
        add("FIREBASE_APP_ID project number must match FIREBASE_SENDER_ID")
    }
}
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
    from(rootProject.file("../ios/MedicationApp/Assets.xcassets/AppImage.imageset/med_1024_transparent.png")) {
        into("drawable-nodpi")
        rename { "app_image.png" }
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
        applicationId = releaseApplicationId
        minSdk = releaseMinSdk
        targetSdk = releaseTargetSdk
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        buildConfigField("String", "API_BASE_URL", productionApiBaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_URL", productionSupabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", productionSupabaseAnonKey.asBuildConfigString())
        buildConfigField("boolean", "BILLING_ENABLED", productionBillingEnabled)
        buildConfigField("String", "FIREBASE_APP_ID", productionFirebaseAppId.asBuildConfigString())
        buildConfigField("String", "FIREBASE_API_KEY", productionFirebaseApiKey.asBuildConfigString())
        buildConfigField("String", "FIREBASE_PROJECT_ID", productionFirebaseProjectId.asBuildConfigString())
        buildConfigField("String", "FIREBASE_SENDER_ID", productionFirebaseSenderId.asBuildConfigString())
        // FirebaseApp can be initialized from FirebaseOptions, but Analytics still resolves its
        // Android app identity from the standard google-services resources. Generate the same
        // resource contract from Git-ignored runtime values without committing google-services.json.
        resValue("string", "google_app_id", productionFirebaseAppId)
        resValue("string", "google_api_key", productionFirebaseApiKey)
        resValue("string", "gcm_defaultSenderId", productionFirebaseSenderId)
        resValue("string", "project_id", productionFirebaseProjectId)
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

configurations.configureEach {
    if (name == "releaseRuntimeClasspath") {
        resolutionStrategy.activateDependencyLocking()
    }
}
dependencyLocking {
    lockMode.set(LockMode.STRICT)
}
val bundletoolCli = configurations.create("bundletoolCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
    resolutionStrategy.activateDependencyLocking()
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
        require(normalizedSha256Fingerprint(playUploadCertSha256) != null) {
            "Set PLAY_UPLOAD_CERT_SHA256 to the registered Play upload-certificate fingerprint."
        }
    }
}

val verifyUploadKeystore by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Proves the configured upload alias, private key and certificate before building the Play AAB."
    dependsOn(verifyProductionSigning)
    inputs.files(
        providers.provider {
            releaseStoreFilePath.takeIf(String::isNotBlank)
                ?.let { listOf(rootProject.file(it)) }
                ?: emptyList<File>()
        },
        rootProject.file("scripts/verify-upload-keystore.sh"),
    )
    inputs.property("releaseKeyAlias", releaseKeyAlias)
    inputs.property("playUploadCertSha256", playUploadCertSha256)
    doFirst {
        commandLine(
            "bash",
            rootProject.file("scripts/verify-upload-keystore.sh").absolutePath,
            rootProject.file(releaseStoreFilePath).absolutePath,
        )
        environment("RELEASE_STORE_PASSWORD", releaseStorePassword)
        environment("RELEASE_KEY_ALIAS", releaseKeyAlias)
        environment("RELEASE_KEY_PASSWORD", releaseKeyPassword)
        environment("EXPECTED_UPLOAD_CERT_SHA256", playUploadCertSha256)
    }
}

val verifyUploadKeystoreContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises accepted and rejected upload-keystore verifier fixtures."
    inputs.files(
        rootProject.file("scripts/verify-upload-keystore.sh"),
        rootProject.file("scripts/test-verify-upload-keystore.sh"),
    )
    commandLine("bash", rootProject.file("scripts/test-verify-upload-keystore.sh").absolutePath)
}

val verifyFirebaseRuntime by tasks.registering {
    group = "verification"
    description = "Fails unless the Android Firebase app identity is complete and internally consistent."
    doLast {
        val failures = firebaseRuntimeFailures()
        require(failures.isEmpty()) {
            "Firebase runtime configuration is incomplete:\n - ${failures.joinToString("\n - ")}"
        }
    }
}

val verifyRuntimeCredentialSafety by tasks.registering {
    group = "verification"
    description = "Proves that only client-safe Supabase publishable or legacy anon keys pass validation."
    doLast {
        require(isClientSafeSupabaseKey("sb_publishable_${"a".repeat(24)}"))
        require(isClientSafeSupabaseKey(syntheticLegacySupabaseKey("anon")))
        require(!isClientSafeSupabaseKey("sb_secret_${"a".repeat(24)}"))
        require(!isClientSafeSupabaseKey(syntheticLegacySupabaseKey("service_role")))
        require(!isClientSafeSupabaseKey(syntheticLegacySupabaseKey("anon", issuer = "other")))
        require(!isClientSafeSupabaseKey("x".repeat(40)))
        require(!isClientSafeSupabaseKey("malformed.jwt"))
    }
}

val verifyProductionRuntime by tasks.registering {
    group = "verification"
    description = "Fails unless the Play artifact has complete, structurally valid production runtime configuration."
    dependsOn(verifyRuntimeCredentialSafety)
    doLast {
        fun httpsUri(value: String): URI? = runCatching { URI(value) }.getOrNull()
            ?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }

        val failures = buildList {
            val apiUri = httpsUri(productionApiBaseUrl)
            if (apiUri?.host != "www.okusuri-mimamori.com") add("API_BASE_URL must use the production HTTPS host")
            if (httpsUri(productionSupabaseUrl) == null) add("SUPABASE_URL is missing or is not HTTPS")
            if (!isClientSafeSupabaseKey(productionSupabaseAnonKey)) {
                add("SUPABASE_ANON_KEY must be a client-safe publishable or legacy anon key")
            }
            addAll(firebaseRuntimeFailures())
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

val verifyReleaseSdkPolicyContract by tasks.registering {
    group = "verification"
    description = "Proves the Release SDK allow/deny policy, including known Firebase transitive support libraries."
    doLast {
        listOf(
            "com.google.firebase:firebase-analytics:23.0.0",
            "com.google.firebase:firebase-messaging:25.0.1",
            "com.google.firebase:firebase-installations:19.0.1",
            "com.google.android.gms:play-services-ads-identifier:18.0.0",
            "androidx.privacysandbox.ads:ads-adservices:1.1.0-beta11",
        ).forEach { coordinate ->
            require(forbiddenReleaseSdkReason(coordinate) == null) {
                "Approved or known Firebase transitive dependency was rejected: $coordinate"
            }
        }
        listOf(
            "com.android.billingclient:billing:7.1.1",
            "com.android.installreferrer:installreferrer:2.2",
            "com.google.android.gms:play-services-ads:24.0.0",
            "com.google.android.gms:play-services-ads-base:24.0.0",
            "com.google.firebase:firebase-crashlytics:19.4.1",
            "com.google.firebase:firebase-perf:21.0.5",
            "com.appsflyer:af-android-sdk:6.16.2",
            "com.adjust.sdk:adjust-android:5.0.2",
            "com.facebook.android:facebook-core:18.0.0",
            "io.sentry:sentry-android:8.0.0",
            "com.mixpanel.android:mixpanel-android:7.5.2",
            "com.amplitude:analytics-android:1.22.1",
            "com.segment.analytics.android:analytics:4.11.3",
        ).forEach { coordinate ->
            require(forbiddenReleaseSdkReason(coordinate) != null) {
                "Forbidden Release SDK unexpectedly passed policy: $coordinate"
            }
        }
    }
}

val releaseRuntimeClasspath = providers.provider { configurations.getByName("releaseRuntimeClasspath") }
val releaseSdkInventoryFile = layout.buildDirectory.file("reports/release-sdk-inventory.txt")
val releaseDependencyLockFile = project.file("gradle.lockfile")
val verifyReleaseSdkPolicy by tasks.registering {
    group = "verification"
    description = "Audits the exact resolved Release SDK inventory against the Play Data safety policy."
    dependsOn(verifyReleaseSdkPolicyContract)
    inputs.files(releaseRuntimeClasspath)
    inputs.file(releaseDependencyLockFile)
    outputs.file(releaseSdkInventoryFile)
    doLast {
        val coordinates = releaseRuntimeClasspath.get()
            .incoming.resolutionResult.allComponents
            .mapNotNull { component ->
                component.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" }
            }
            .distinct()
            .sorted()
        val violations = coordinates.mapNotNull { coordinate ->
            forbiddenReleaseSdkReason(coordinate)?.let { reason -> "$coordinate - $reason" }
        }
        require(violations.isEmpty()) {
            "Release contains SDKs outside the approved Data safety contract:\n - ${violations.joinToString("\n - ")}"
        }
        require(coordinates.any { it.startsWith("com.google.firebase:firebase-analytics:") }) {
            "Firebase Analytics is required by the approved Data safety contract"
        }
        require(coordinates.any { it.startsWith("com.google.firebase:firebase-messaging:") }) {
            "Firebase Cloud Messaging is required by the approved Data safety contract"
        }
        require(coordinates.any { it.startsWith("com.google.firebase:firebase-installations:") }) {
            "Firebase Installations is required by the approved Data safety contract"
        }

        val knownFirebaseTransitives = coordinates.filter { coordinate ->
            coordinate.startsWith("com.google.android.gms:play-services-ads-identifier:") ||
                coordinate.startsWith("androidx.privacysandbox.ads:ads-adservices")
        }
        releaseSdkInventoryFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("Release runtime SDK inventory")
                    appendLine("Resolved modules: ${coordinates.size}")
                    appendLine()
                    appendLine("Known Firebase Analytics transitive support libraries")
                    if (knownFirebaseTransitives.isEmpty()) appendLine("(none)")
                    knownFirebaseTransitives.forEach(::appendLine)
                    appendLine()
                    appendLine("All resolved modules")
                    coordinates.forEach(::appendLine)
                },
            )
        }
        println(
            "Release SDK policy passed for ${coordinates.size} resolved modules; " +
                "inventory: ${releaseSdkInventoryFile.get().asFile}",
        )
    }
}

val verifyReleaseApkCompatibility by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Inspects Release manifest security/privacy, SDK, permissions and 16 KB compatibility."
    dependsOn("assembleRelease", verifyReleaseSdkPolicy)
    val apkFileName = if (releaseSigningConfigured) "app-release.apk" else "app-release-unsigned.apk"
    val apkFile = layout.buildDirectory.file("outputs/apk/release/$apkFileName")
    inputs.files(
        apkFile,
        rootProject.file("scripts/verify-release-apk.sh"),
        rootProject.file("scripts/verify-release-manifest-policy.py"),
    )
    commandLine(
        "bash",
        rootProject.file("scripts/verify-release-apk.sh").absolutePath,
        apkFile.get().asFile.absolutePath,
    )
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

val releaseBundleFile = layout.buildDirectory.file("outputs/bundle/release/app-release.aab")
val releaseBundleManifestFile = layout.buildDirectory.file("reports/release-bundle-manifest.xml")

val verifyReleaseBundlePolicyContract by tasks.registering {
    group = "verification"
    description = "Proves the AAB base-module and private-file fail-closed structure policy."
    doLast {
        val validEntries = setOf(
            "BundleConfig.pb",
            "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex",
            "base/resources.pb",
            "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties",
        )
        require(releaseBundleStructureFailures(validEntries).isEmpty())
        listOf(
            validEntries - "BundleConfig.pb",
            validEntries + "feature/manifest/AndroidManifest.xml",
            validEntries + "base/assets/google-services.json",
            validEntries + "base/root/private-upload-key.jks",
            validEntries + "base/root/.env",
        ).forEach { entries ->
            require(releaseBundleStructureFailures(entries).isNotEmpty()) {
                "Unsafe synthetic AAB structure unexpectedly passed"
            }
        }
    }
}

val prepareReleaseBundleManifest by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Validates the Release AAB and atomically extracts its base manifest with pinned bundletool."
    dependsOn("bundleRelease", verifyReleaseBundlePolicyContract)
    inputs.files(
        releaseBundleFile,
        bundletoolCli,
        rootProject.file("scripts/prepare-release-bundle-manifest.sh"),
    )
    outputs.file(releaseBundleManifestFile)
    doFirst {
        commandLine(
            "bash",
            rootProject.file("scripts/prepare-release-bundle-manifest.sh").absolutePath,
            bundletoolCli.asPath,
            releaseBundleFile.get().asFile.absolutePath,
            releaseBundleManifestFile.get().asFile.absolutePath,
        )
    }
}

val verifyReleaseBundleContent by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Verifies validated AAB structure and its dumped merged manifest security/privacy policy."
    dependsOn(prepareReleaseBundleManifest)
    inputs.files(
        releaseBundleFile,
        releaseBundleManifestFile,
        rootProject.file("scripts/verify-release-manifest-policy.py"),
    )
    commandLine(
        "python3",
        rootProject.file("scripts/verify-release-manifest-policy.py").absolutePath,
        releaseBundleManifestFile.get().asFile.absolutePath,
    )
    doLast {
        val bundle = releaseBundleFile.get().asFile
        val entries = ZipFile(bundle).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
        val failures = releaseBundleStructureFailures(entries)
        require(failures.isEmpty()) {
            "Release AAB content policy failed:\n - ${failures.joinToString("\n - ")}"
        }
        val sha256 = bundle.sha256Hex()
        val dexCount = entries.count { it.matches(Regex("^base/dex/classes[0-9]*\\.dex$")) }
        val nativeLibraryCount = entries.count { it.matches(Regex("^base/lib/[^/]+/[^/]+\\.so$")) }
        println("Release AAB content verification passed.")
        println("modules=base dexFiles=$dexCount nativeLibraries=$nativeLibraryCount")
        println("AAB_SHA256=$sha256")
    }
}

val verifySignedReleaseBundle by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Verifies the generated AAB signature and registered Play upload certificate."
    dependsOn(
        verifyProductionRuntime,
        verifyUploadKeystore,
        verifyReleaseSdkPolicy,
        verifyReleaseApkCompatibility,
        verifyReleaseBundleContent,
        verifyPlayStoreAssets,
    )
    inputs.files(releaseBundleFile, rootProject.file("scripts/verify-signed-aab.sh"))
    inputs.property("playUploadCertSha256", playUploadCertSha256)
    commandLine(
        "bash",
        rootProject.file("scripts/verify-signed-aab.sh").absolutePath,
        releaseBundleFile.get().asFile.absolutePath,
    )
    environment("EXPECTED_UPLOAD_CERT_SHA256", playUploadCertSha256)
}

val verifyReleaseEvidencePolicyContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises exact-artifact release evidence acceptance and rejection boundaries."
    inputs.files(
        rootProject.file("scripts/generate-release-evidence.py"),
        rootProject.file("scripts/test-generate-release-evidence.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-generate-release-evidence.py").absolutePath)
}

val signedReleaseEvidenceFile = layout.buildDirectory.file("reports/play-release-evidence.json")
val generateSignedReleaseEvidence by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Writes the exact signed Play AAB source/version/hash/certificate/dependency evidence ledger."
    dependsOn(verifySignedReleaseBundle, verifyReleaseEvidencePolicyContract)
    inputs.files(
        releaseBundleFile,
        releaseBundleManifestFile,
        releaseDependencyLockFile,
        releaseSdkInventoryFile,
        rootProject.file("scripts/generate-release-evidence.py"),
    )
    inputs.properties(
        mapOf(
            "applicationId" to releaseApplicationId,
            "versionCode" to releaseVersionCode,
            "versionName" to releaseVersionName,
            "minSdk" to releaseMinSdk,
            "targetSdk" to releaseTargetSdk,
            "publishedIosApiBaselineSha" to publishedIosApiBaselineSha,
            "playUploadCertSha256" to playUploadCertSha256,
        ),
    )
    outputs.file(signedReleaseEvidenceFile)
    outputs.upToDateWhen { false }
    commandLine(
        "python3",
        rootProject.file("scripts/generate-release-evidence.py").absolutePath,
        "--repository-root",
        rootProject.projectDir.parentFile.absolutePath,
        "--aab",
        releaseBundleFile.get().asFile.absolutePath,
        "--manifest",
        releaseBundleManifestFile.get().asFile.absolutePath,
        "--dependency-lock",
        releaseDependencyLockFile.absolutePath,
        "--inventory",
        releaseSdkInventoryFile.get().asFile.absolutePath,
        "--output",
        signedReleaseEvidenceFile.get().asFile.absolutePath,
        "--application-id",
        releaseApplicationId,
        "--version-code",
        releaseVersionCode.toString(),
        "--version-name",
        releaseVersionName,
        "--min-sdk",
        releaseMinSdk.toString(),
        "--target-sdk",
        releaseTargetSdk.toString(),
        "--baseline-sha",
        publishedIosApiBaselineSha,
        "--expected-signer-sha256",
        playUploadCertSha256,
        "--bundletool-version",
        "1.18.0",
    )
}

tasks.register("bundleSignedRelease") {
    group = "build"
    description = "Builds, verifies and records exact evidence for the Play upload AAB."
    dependsOn(generateSignedReleaseEvidence)
}
verifyReleaseApkCompatibility.configure {
    mustRunAfter(verifyProductionRuntime, verifyProductionSigning, verifyUploadKeystore)
}
tasks.matching { it.name == "bundleRelease" }.configureEach {
    mustRunAfter(
        verifyProductionRuntime,
        verifyProductionSigning,
        verifyUploadKeystore,
        verifyReleaseApkCompatibility,
    )
}

tasks.named("preBuild").configure { dependsOn(syncRoleAssets) }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    add(bundletoolCli.name, "com.android.tools.build:bundletool:1.18.0")
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
