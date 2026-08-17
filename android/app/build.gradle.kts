import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.tasks.Sync
import java.net.URI
import java.awt.Color
import java.awt.image.BufferedImage
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream

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

val expectedPlayStoreScreenshots = listOf(
    "01-mode-select.jpg",
    "02-patient-today.jpg",
    "03-patient-history.jpg",
    "04-caregiver-today.jpg",
    "05-caregiver-medications.jpg",
    "06-caregiver-inventory.jpg",
    "07-caregiver-history.jpg",
    "08-caregiver-settings.jpg",
)

fun playStoreScreenshotMappings(sourceMapFile: File): List<Pair<String, String>> =
    sourceMapFile.readLines()
        .filter(String::isNotBlank)
        .mapIndexed { index, line ->
            val fields = line.split('\t')
            require(fields.size == 2 && fields.all { it.isNotBlank() && it == it.trim() }) {
                "Invalid screenshot source mapping at line ${index + 1}"
            }
            fields[0] to fields[1]
        }
        .also { mappings ->
            require(mappings.map { it.first } == expectedPlayStoreScreenshots) {
                "Screenshot source map output order drifted"
            }
            require(mappings.map { it.second }.distinct().size == expectedPlayStoreScreenshots.size) {
                "Each Play screenshot must have one unique evidence source"
            }
        }

fun writePaddedPlayStoreJpeg(
    source: BufferedImage,
    destination: File,
    canvasWidth: Int = 1_350,
    canvasHeight: Int = 2_400,
    horizontalOffset: Int = 135,
    quality: Float = 0.9f,
) {
    require(source.width + horizontalOffset * 2 == canvasWidth) { "Source/canvas width contract drifted" }
    require(source.height == canvasHeight) { "Source/canvas height contract drifted" }
    require(quality in 0f..1f) { "JPEG quality must be between zero and one" }

    val canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = canvas.createGraphics()
    try {
        graphics.color = Color(0xF3, 0xFA, 0xFC)
        graphics.fillRect(0, 0, canvasWidth, canvasHeight)
        graphics.drawImage(source, horizontalOffset, 0, null)
    } finally {
        graphics.dispose()
    }
    destination.parentFile.mkdirs()
    val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
        ?: error("No JPEG writer is available")
    try {
        val parameters = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
            progressiveMode = ImageWriteParam.MODE_DISABLED
        }
        FileImageOutputStream(destination).use { output ->
            writer.output = output
            writer.write(null, IIOImage(canvas, null, null), parameters)
        }
    } finally {
        writer.dispose()
    }
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
val playAppSigningCertSha256Fingerprints = runtimeConfig(
    "PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS",
)
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

val verifyAndroidCiRuntimeContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Locks Android CI to reviewed Node 24 actions, least privilege and Java 17."
    inputs.files(
        rootProject.file("scripts/verify-android-ci-runtime.py"),
        rootProject.file("scripts/test-verify-android-ci-runtime.py"),
        rootProject.file("../.github/workflows/android-ci.yml"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", rootProject.file("scripts/test-verify-android-ci-runtime.py").absolutePath)
}

val verifyMainMergeSurfaceContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises accepted and rejected android-dev to main committed merge surfaces."
    inputs.files(
        rootProject.file("scripts/verify-main-merge-surface.py"),
        rootProject.file("scripts/test-verify-main-merge-surface.py"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", rootProject.file("scripts/test-verify-main-merge-surface.py").absolutePath)
}

val verifyMainMergeSurface by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Fails unless committed android-dev changes are rebased and confined to reviewed main scopes."
    dependsOn(verifyMainMergeSurfaceContract)
    inputs.files(
        rootProject.file("scripts/verify-main-merge-surface.py"),
        rootProject.file("scripts/test-verify-main-merge-surface.py"),
    )
    outputs.upToDateWhen { false }
    commandLine(
        "python3",
        rootProject.file("scripts/verify-main-merge-surface.py").absolutePath,
        "--repository-root",
        rootProject.projectDir.parentFile.absolutePath,
        "--base-ref",
        "origin/main",
        "--head-ref",
        "HEAD",
    )
}

val verifyReleaseGatesContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises accepted and rejected residual release-gate ledger fixtures."
    inputs.files(
        rootProject.file("scripts/verify-release-gates.py"),
        rootProject.file("scripts/test-verify-release-gates.py"),
        rootProject.file("../docs/android/release-gates.json"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", rootProject.file("scripts/test-verify-release-gates.py").absolutePath)
}

val verifyReleaseGates by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Fails unless residual Android release gates exactly match requirements and backlog."
    dependsOn(verifyReleaseGatesContract)
    inputs.files(
        rootProject.file("scripts/verify-release-gates.py"),
        rootProject.file("scripts/test-verify-release-gates.py"),
        rootProject.file("../docs/android/release-gates.json"),
        rootProject.file("../docs/android/parity-requirements.md"),
        rootProject.file("../docs/android/execution-backlog.md"),
        rootProject.file("../docs/android/README.md"),
        rootProject.file("../docs/android/android-port-master-plan.md"),
    )
    outputs.upToDateWhen { false }
    commandLine(
        "python3",
        rootProject.file("scripts/verify-release-gates.py").absolutePath,
        "--repository-root",
        rootProject.projectDir.parentFile.absolutePath,
        "--manifest",
        rootProject.file("../docs/android/release-gates.json").absolutePath,
        "--requirements",
        rootProject.file("../docs/android/parity-requirements.md").absolutePath,
        "--backlog",
        rootProject.file("../docs/android/execution-backlog.md").absolutePath,
        "--readme",
        rootProject.file("../docs/android/README.md").absolutePath,
        "--master-plan",
        rootProject.file("../docs/android/android-port-master-plan.md").absolutePath,
    )
}

val verifyProductionAppLinksContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises accepted and rejected production Digital Asset Links surfaces."
    dependsOn("verifyPlayInstalledAppLinksContract")
    inputs.files(
        rootProject.file("scripts/verify-production-app-links.py"),
        rootProject.file("scripts/test-verify-production-app-links.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-verify-production-app-links.py").absolutePath)
}

val verifyPlayInstalledAppLinksContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises accepted and rejected Play-installed App Links package-manager states."
    inputs.files(
        rootProject.file("scripts/verify-production-app-links.py"),
        rootProject.file("scripts/verify-play-installed-app-links.py"),
        rootProject.file("scripts/test-verify-play-installed-app-links.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-verify-play-installed-app-links.py").absolutePath)
}

val verifyProductionAppLinks by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Fetches and verifies production Digital Asset Links against Play app signing."
    dependsOn(verifyProductionAppLinksContract)
    inputs.file(rootProject.file("scripts/verify-production-app-links.py"))
    inputs.property(
        "playAppSigningCertificateCount",
        playAppSigningCertSha256Fingerprints.split(',').count(String::isNotBlank),
    )
    outputs.upToDateWhen { false }
    commandLine("python3", rootProject.file("scripts/verify-production-app-links.py").absolutePath)
    environment(
        "EXPECTED_APP_SIGNING_CERT_SHA256_FINGERPRINTS",
        playAppSigningCertSha256Fingerprints,
    )
}

val verifyPlayInstalledAppLinks by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Re-verifies the exact Play-installed package signer and production App Link domain."
    dependsOn(verifyProductionAppLinks, verifyPlayInstalledAppLinksContract)
    inputs.files(
        rootProject.file("scripts/verify-production-app-links.py"),
        rootProject.file("scripts/verify-play-installed-app-links.py"),
    )
    inputs.property(
        "playAppSigningCertificateCount",
        playAppSigningCertSha256Fingerprints.split(',').count(String::isNotBlank),
    )
    outputs.upToDateWhen { false }
    commandLine("python3", rootProject.file("scripts/verify-play-installed-app-links.py").absolutePath)
    environment(
        "EXPECTED_APP_SIGNING_CERT_SHA256_FINGERPRINTS",
        playAppSigningCertSha256Fingerprints,
    )
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

val playStorePhoneDirectory = rootProject.file("../docs/android/play-store-assets/phone-ja-JP")
val playStoreScreenshotSourceMapFile = playStorePhoneDirectory.resolve("sources.tsv")
val renderedPlayStoreScreenshotDirectory = layout.buildDirectory.dir("generated/play-store-phone-ja-JP")

val renderPlayStoreScreenshots by tasks.registering {
    group = "documentation"
    description = "Deterministically renders the ordered Play phone JPEGs from mapped Compose evidence."
    inputs.file(playStoreScreenshotSourceMapFile)
    inputs.files(
        providers.provider {
            playStoreScreenshotMappings(playStoreScreenshotSourceMapFile).map { mapping ->
                rootProject.projectDir.parentFile.resolve(mapping.second)
            }
        },
    )
    outputs.dir(renderedPlayStoreScreenshotDirectory)

    doLast {
        val repositoryRoot = rootProject.projectDir.parentFile.canonicalFile
        val outputDirectory = renderedPlayStoreScreenshotDirectory.get().asFile
        delete(outputDirectory)
        outputDirectory.mkdirs()
        playStoreScreenshotMappings(playStoreScreenshotSourceMapFile).forEach { (outputName, sourcePath) ->
            require(
                sourcePath.startsWith("docs/android/evidence/") &&
                    sourcePath.endsWith(".png") &&
                    sourcePath.split('/').none { it == ".." },
            ) { "Unsafe or non-evidence screenshot source: $sourcePath" }
            val sourceFile = repositoryRoot.resolve(sourcePath).canonicalFile
            require(sourceFile.toPath().startsWith(repositoryRoot.toPath()) && sourceFile.isFile) {
                "Screenshot evidence source does not exist: $sourcePath"
            }
            val source = requireNotNull(ImageIO.read(sourceFile)) { "Unreadable screenshot source: $sourcePath" }
            require(source.width == 1080 && source.height == 2400) {
                "Screenshot source must be 1080 x 2400: $sourcePath is ${source.width} x ${source.height}"
            }
            writePaddedPlayStoreJpeg(source, outputDirectory.resolve(outputName))
        }
    }
}

val updatePlayStoreScreenshots by tasks.registering {
    group = "documentation"
    description = "Copies deterministic Play phone renders into the committed Japanese handoff."
    dependsOn(renderPlayStoreScreenshots)
    inputs.dir(renderedPlayStoreScreenshotDirectory)
    outputs.files(expectedPlayStoreScreenshots.map(playStorePhoneDirectory::resolve))
    doLast {
        expectedPlayStoreScreenshots.forEach { filename ->
            renderedPlayStoreScreenshotDirectory.get().asFile.resolve(filename)
                .copyTo(playStorePhoneDirectory.resolve(filename), overwrite = true)
        }
    }
}

val verifyPlayStoreScreenshotRendererContract by tasks.registering {
    group = "verification"
    description = "Proves deterministic Play JPEG rendering and rejects invalid canvas/quality inputs."
    val contractDirectory = layout.buildDirectory.dir("tmp/play-store-renderer-contract")
    outputs.upToDateWhen { false }
    doLast {
        val directory = contractDirectory.get().asFile
        delete(directory)
        directory.mkdirs()
        val source = BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setRGB(x, y, Color(20 + x * 30, 40 + y * 35, 90 + x * 10 + y * 5).rgb)
                }
            }
        }
        val first = directory.resolve("first.jpg")
        val second = directory.resolve("second.jpg")
        writePaddedPlayStoreJpeg(source, first, canvasWidth = 8, canvasHeight = 3, horizontalOffset = 2)
        writePaddedPlayStoreJpeg(source, second, canvasWidth = 8, canvasHeight = 3, horizontalOffset = 2)
        require(first.readBytes().contentEquals(second.readBytes())) {
            "Identical source renders must be byte-for-byte deterministic"
        }
        val decoded = requireNotNull(ImageIO.read(first)) { "Synthetic renderer output is unreadable" }
        require(decoded.width == 8 && decoded.height == 3 && !decoded.colorModel.hasAlpha()) {
            "Synthetic renderer output format drifted"
        }
        require(
            runCatching {
                writePaddedPlayStoreJpeg(source, directory.resolve("bad-width.jpg"), 9, 3, 2)
            }.isFailure,
        ) { "Invalid canvas width unexpectedly rendered" }
        require(
            runCatching {
                writePaddedPlayStoreJpeg(source, directory.resolve("bad-height.jpg"), 8, 4, 2)
            }.isFailure,
        ) { "Invalid canvas height unexpectedly rendered" }
        require(
            runCatching {
                writePaddedPlayStoreJpeg(source, directory.resolve("bad-quality.jpg"), 8, 3, 2, 1.1f)
            }.isFailure,
        ) { "Invalid JPEG quality unexpectedly rendered" }
        delete(directory)
    }
}

val verifyPlayStoreListingContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises accepted and rejected Play store listing contract fixtures."
    inputs.files(
        rootProject.file("scripts/verify-play-store-listing.py"),
        rootProject.file("scripts/test-verify-play-store-listing.py"),
        rootProject.file("../docs/android/play-store-listing-ja.md"),
        rootProject.file("../docs/android/play-store-assets/phone-ja-JP/sources.tsv"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", rootProject.file("scripts/test-verify-play-store-listing.py").absolutePath)
}

val verifyPlayStoreListing by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Fails unless Play listing copy, URLs, declarations and screenshot source map match shipping Android surfaces."
    dependsOn(verifyPlayStoreListingContract)
    inputs.files(
        rootProject.file("scripts/verify-play-store-listing.py"),
        rootProject.file("scripts/test-verify-play-store-listing.py"),
        rootProject.file("../docs/android/play-store-listing-ja.md"),
        rootProject.file("../docs/android/play-store-assets/phone-ja-JP/sources.tsv"),
        project.file("src/main/AndroidManifest.xml"),
        project.file("src/main/res/values/strings.xml"),
        project.file("build.gradle.kts"),
    )
    outputs.upToDateWhen { false }
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        rootProject.file("scripts/verify-play-store-listing.py").absolutePath,
        "--repository-root",
        rootProject.projectDir.parentFile.absolutePath,
    )
}

val verifyPlayStoreAssets by tasks.registering {
    group = "verification"
    description = "Validates source-bound Play phone screenshots, store icon and cross-platform icon parity."
    dependsOn(verifyPlayStoreListing, renderPlayStoreScreenshots, verifyPlayStoreScreenshotRendererContract)
    mustRunAfter(updatePlayStoreScreenshots)

    val listingFile = rootProject.file("../docs/android/play-store-listing-ja.md")
    val assetRoot = rootProject.file("../docs/android/play-store-assets")
    val phoneDirectory = playStorePhoneDirectory
    val screenshotSourceMapFile = playStoreScreenshotSourceMapFile
    val storeIconFile = assetRoot.resolve("icon-512.png")
    val featureGraphicFile = assetRoot.resolve("feature-graphic-1024x500.jpg")
    val iosIconFile = rootProject.file("../ios/MedicationApp/Assets.xcassets/AppIcon.appiconset/med_1024_transparent.png")
    val androidForegroundFile = project.file("src/main/res/drawable-nodpi/ic_launcher_foreground.png")
    inputs.files(
        listingFile,
        screenshotSourceMapFile,
        storeIconFile,
        featureGraphicFile,
        iosIconFile,
        androidForegroundFile,
    )
    inputs.dir(phoneDirectory)
    inputs.dir(renderedPlayStoreScreenshotDirectory)

    doLast {
        val expectedScreenshots = expectedPlayStoreScreenshots
        val directoryFiles = phoneDirectory.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            .orEmpty()
        require(directoryFiles.map { it.name } == expectedScreenshots.sorted() + "sources.tsv") {
            "Play phone asset directory contains an unexpected or missing file"
        }
        val screenshotFiles = directoryFiles.filter { it.extension.lowercase() == "jpg" }
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

        val sourceMappings = playStoreScreenshotMappings(screenshotSourceMapFile)
        val repositoryRoot = rootProject.projectDir.parentFile.canonicalFile
        sourceMappings.forEach { (outputName, sourcePath) ->
            require(
                sourcePath.startsWith("docs/android/evidence/") &&
                    sourcePath.endsWith(".png") &&
                    sourcePath.split('/').none { it == ".." },
            ) { "Unsafe or non-evidence screenshot source: $sourcePath" }
            val sourceFile = repositoryRoot.resolve(sourcePath).canonicalFile
            require(sourceFile.toPath().startsWith(repositoryRoot.toPath()) && sourceFile.isFile) {
                "Screenshot evidence source does not exist: $sourcePath"
            }
            val source = requireNotNull(ImageIO.read(sourceFile)) { "Unreadable screenshot source: $sourcePath" }
            require(source.width == 1080 && source.height == 2400) {
                "Screenshot source must be 1080 x 2400: $sourcePath is ${source.width} x ${source.height}"
            }
            val output = requireNotNull(ImageIO.read(phoneDirectory.resolve(outputName))) {
                "Unreadable Play screenshot: $outputName"
            }
            var channelDifference = 0L
            var comparedChannels = 0L
            for (y in 0 until source.height step 4) {
                for (x in 0 until source.width step 4) {
                    val sourceRgb = source.getRGB(x, y)
                    val outputRgb = output.getRGB(x + 135, y)
                    channelDifference += Math.abs((sourceRgb shr 16 and 0xff) - (outputRgb shr 16 and 0xff))
                    channelDifference += Math.abs((sourceRgb shr 8 and 0xff) - (outputRgb shr 8 and 0xff))
                    channelDifference += Math.abs((sourceRgb and 0xff) - (outputRgb and 0xff))
                    comparedChannels += 3
                }
            }
            val sourceMeanDifference = channelDifference.toDouble() / comparedChannels
            require(sourceMeanDifference <= 1.5) {
                "$outputName no longer derives from $sourcePath (mean RGB difference $sourceMeanDifference)"
            }

            var paddingDifference = 0L
            var paddingChannels = 0L
            for (y in 0 until output.height step 4) {
                for (x in 0 until output.width step 4) {
                    if (x >= 128 && x < 1222) continue
                    val rgb = output.getRGB(x, y)
                    paddingDifference += Math.abs((rgb shr 16 and 0xff) - 0xf3)
                    paddingDifference += Math.abs((rgb shr 8 and 0xff) - 0xfa)
                    paddingDifference += Math.abs((rgb and 0xff) - 0xfc)
                    paddingChannels += 3
                }
            }
            val paddingMeanDifference = paddingDifference.toDouble() / paddingChannels
            require(paddingMeanDifference <= 1.5) {
                "$outputName horizontal padding drifted from #F3FAFC (mean RGB difference $paddingMeanDifference)"
            }
            val renderedFile = renderedPlayStoreScreenshotDirectory.get().asFile.resolve(outputName)
            require(renderedFile.readBytes().contentEquals(phoneDirectory.resolve(outputName).readBytes())) {
                "$outputName is not the exact deterministic renderer output; run updatePlayStoreScreenshots"
            }
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

val verifyUniversalApkSetContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises universal APK Set structure and atomic extraction acceptance/rejection."
    inputs.files(
        rootProject.file("scripts/extract-universal-apk.py"),
        rootProject.file("scripts/test-extract-universal-apk.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-extract-universal-apk.py").absolutePath)
}

val releaseBundleInstallSurfaceApk = layout.buildDirectory.file(
    "outputs/bundle-install-surface/universal-test-only.apk",
)
val verifyReleaseBundleInstallSurface by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Builds a universal APK Set from the exact AAB and reapplies Release APK policy."
    dependsOn(verifyReleaseBundleContent, verifyUniversalApkSetContract)
    inputs.files(
        releaseBundleFile,
        bundletoolCli,
        rootProject.file("scripts/extract-universal-apk.py"),
        rootProject.file("scripts/verify-release-bundle-install-surface.sh"),
        rootProject.file("scripts/verify-release-apk.sh"),
        rootProject.file("scripts/verify-release-manifest-policy.py"),
    )
    outputs.file(releaseBundleInstallSurfaceApk)
    commandLine(
        "bash",
        rootProject.file("scripts/verify-release-bundle-install-surface.sh").absolutePath,
        bundletoolCli.asPath,
        releaseBundleFile.get().asFile.absolutePath,
        releaseBundleInstallSurfaceApk.get().asFile.absolutePath,
    )
}

val verifyDeviceSplitSetContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises exact device split selection structure and atomic report acceptance/rejection."
    inputs.files(
        rootProject.file("scripts/verify-device-split-set.py"),
        rootProject.file("scripts/test-verify-device-split-set.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-verify-device-split-set.py").absolutePath)
}

val verifyDeviceSplitInstallContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises physical split installer pre-existing-install refusal and cleanup."
    inputs.files(
        rootProject.file("scripts/verify-device-split-install.sh"),
        rootProject.file("scripts/test-verify-device-split-install.sh"),
    )
    commandLine("bash", rootProject.file("scripts/test-verify-device-split-install.sh").absolutePath)
}

val releaseDeviceSplitSurfaceDir = layout.buildDirectory.dir("outputs/device-split-install-surface")
val verifyReleaseDeviceSplitSurface by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Selects and validates exact API 26/33/35 split APK surfaces from the generated AAB."
    dependsOn(
        verifyReleaseBundleContent,
        verifyDeviceSplitSetContract,
        verifyDeviceSplitInstallContract,
    )
    inputs.files(
        releaseBundleFile,
        bundletoolCli,
        rootProject.file("scripts/verify-device-split-set.py"),
        rootProject.file("scripts/verify-device-split-install.sh"),
        rootProject.file("scripts/test-verify-device-split-install.sh"),
        rootProject.file("scripts/verify-release-device-split-surface.sh"),
        rootProject.file("scripts/verify-release-apk.sh"),
        rootProject.file("scripts/verify-release-manifest-policy.py"),
    )
    outputs.dir(releaseDeviceSplitSurfaceDir)
    commandLine(
        "bash",
        rootProject.file("scripts/verify-release-device-split-surface.sh").absolutePath,
        bundletoolCli.asPath,
        releaseBundleFile.get().asFile.absolutePath,
        releaseDeviceSplitSurfaceDir.get().asFile.absolutePath,
    )
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
        verifyReleaseBundleInstallSurface,
        verifyReleaseDeviceSplitSurface,
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
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("scripts/test-generate-release-evidence.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-generate-release-evidence.py").absolutePath)
}

val signedReleaseEvidenceFile = layout.buildDirectory.file("reports/play-release-evidence.json")
val generateSignedReleaseEvidence by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Writes exact signed-AAB and source-bound Play listing hashes to the release evidence ledger."
    dependsOn(verifySignedReleaseBundle, verifyReleaseEvidencePolicyContract)
    inputs.files(
        releaseBundleFile,
        releaseBundleManifestFile,
        releaseDependencyLockFile,
        releaseSdkInventoryFile,
        rootProject.file("../docs/android/play-store-listing-ja.md"),
        rootProject.file("../docs/android/play-store-assets/phone-ja-JP/sources.tsv"),
        rootProject.file("../docs/android/play-store-assets/icon-512.png"),
        rootProject.file("../docs/android/play-store-assets/feature-graphic-1024x500.jpg"),
        rootProject.file("scripts/generate-release-evidence.py"),
    )
    inputs.dir(rootProject.file("../docs/android/play-store-assets/phone-ja-JP"))
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
        "--store-listing",
        rootProject.file("../docs/android/play-store-listing-ja.md").absolutePath,
        "--store-source-map",
        rootProject.file("../docs/android/play-store-assets/phone-ja-JP/sources.tsv").absolutePath,
        "--store-icon",
        rootProject.file("../docs/android/play-store-assets/icon-512.png").absolutePath,
        "--store-feature-graphic",
        rootProject.file("../docs/android/play-store-assets/feature-graphic-1024x500.jpg").absolutePath,
        "--store-phone-directory",
        rootProject.file("../docs/android/play-store-assets/phone-ja-JP").absolutePath,
    )
}

val verifyPlayReleaseHandoffContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises exact AAB/evidence/checksum handoff acceptance and tamper rejection."
    inputs.files(
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("scripts/test-prepare-play-release-handoff.py"),
        rootProject.file("scripts/verify-prepared-play-release-handoff.py"),
    )
    commandLine("python3", rootProject.file("scripts/test-prepare-play-release-handoff.py").absolutePath)
}

val verifyPlayUploadReceiptContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises Google Play Bundle response matching and secret-free receipt output."
    inputs.files(
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("scripts/test-prepare-play-release-handoff.py"),
        rootProject.file("scripts/verify-prepared-play-release-handoff.py"),
        rootProject.file("scripts/verify-play-upload-receipt.py"),
        rootProject.file("scripts/test-verify-play-upload-receipt.py"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", rootProject.file("scripts/test-verify-play-upload-receipt.py").absolutePath)
}

val verifyPlayInternalTrackReceiptContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises published Google Play Internal-track matching and chained receipt output."
    inputs.files(
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("scripts/test-prepare-play-release-handoff.py"),
        rootProject.file("scripts/verify-prepared-play-release-handoff.py"),
        rootProject.file("scripts/verify-play-upload-receipt.py"),
        rootProject.file("scripts/verify-play-internal-track-receipt.py"),
        rootProject.file("scripts/test-verify-play-internal-track-receipt.py"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        rootProject.file("scripts/test-verify-play-internal-track-receipt.py").absolutePath,
    )
}

val verifyPlayGeneratedApksReceiptContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises Play-generated APK signing metadata and chained receipt output."
    inputs.files(
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("scripts/test-prepare-play-release-handoff.py"),
        rootProject.file("scripts/verify-prepared-play-release-handoff.py"),
        rootProject.file("scripts/verify-play-upload-receipt.py"),
        rootProject.file("scripts/verify-play-internal-track-receipt.py"),
        rootProject.file("scripts/verify-play-generated-apks-receipt.py"),
        rootProject.file("scripts/test-verify-play-generated-apks-receipt.py"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        rootProject.file("scripts/test-verify-play-generated-apks-receipt.py").absolutePath,
    )
}

val verifyPlayDownloadedBaseApksReceiptContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises downloaded Play base-master APK byte/signing checks and chained receipt output."
    dependsOn("assembleDebug")
    val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    inputs.files(
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("scripts/test-prepare-play-release-handoff.py"),
        rootProject.file("scripts/verify-prepared-play-release-handoff.py"),
        rootProject.file("scripts/verify-play-upload-receipt.py"),
        rootProject.file("scripts/verify-play-internal-track-receipt.py"),
        rootProject.file("scripts/verify-play-generated-apks-receipt.py"),
        rootProject.file("scripts/test-verify-play-generated-apks-receipt.py"),
        rootProject.file("scripts/verify-play-downloaded-base-apks-receipt.py"),
        rootProject.file("scripts/test-verify-play-downloaded-base-apks-receipt.py"),
        debugApk,
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        rootProject.file("scripts/test-verify-play-downloaded-base-apks-receipt.py").absolutePath,
        "--real-apk",
        debugApk.get().asFile.absolutePath,
        "--repository-root",
        rootProject.projectDir.parentFile.absolutePath,
        "--expected-version-code",
        releaseVersionCode.toString(),
        "--expected-version-name",
        releaseVersionName,
    )
}

val verifyPlayInstalledPackageReceiptContract by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Exercises C118-to-physical-package byte, installer, split, signer and App Links binding."
    inputs.files(
        rootProject.file("scripts/verify-production-app-links.py"),
        rootProject.file("scripts/verify-play-installed-app-links.py"),
        rootProject.file("scripts/verify-play-installed-package-receipt.py"),
        rootProject.file("scripts/test-verify-play-installed-package-receipt.py"),
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        rootProject.file("scripts/test-verify-play-installed-package-receipt.py").absolutePath,
    )
}

val playDownloadedBaseApksReceipt = runtimeConfig("PLAY_DOWNLOADED_BASE_APKS_RECEIPT")
val playInstalledPackageReceiptOutput = runtimeConfig("PLAY_INSTALLED_PACKAGE_RECEIPT_OUTPUT")

val verifyPlayInstalledPackageReceipt by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Binds the exact C118 base APK bytes to the Play-installed physical package."
    dependsOn(verifyProductionAppLinks, verifyPlayInstalledPackageReceiptContract)
    inputs.files(
        rootProject.file("scripts/verify-production-app-links.py"),
        rootProject.file("scripts/verify-play-installed-app-links.py"),
        rootProject.file("scripts/verify-play-installed-package-receipt.py"),
    )
    inputs.property("c118ReceiptConfigured", playDownloadedBaseApksReceipt.isNotBlank())
    inputs.property("c119ReceiptOutputConfigured", playInstalledPackageReceiptOutput.isNotBlank())
    outputs.upToDateWhen { false }
    doFirst {
        require(playDownloadedBaseApksReceipt.isNotBlank()) {
            "Set PLAY_DOWNLOADED_BASE_APKS_RECEIPT to the exact retained C118 receipt."
        }
        require(playInstalledPackageReceiptOutput.isNotBlank()) {
            "Set PLAY_INSTALLED_PACKAGE_RECEIPT_OUTPUT to the canonical C119 receipt path."
        }
    }
    commandLine(
        "python3",
        rootProject.file("scripts/verify-play-installed-package-receipt.py").absolutePath,
        "--downloaded-base-apks-receipt",
        playDownloadedBaseApksReceipt,
        "--output",
        playInstalledPackageReceiptOutput,
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
}

val playReleaseHandoffRoot = layout.buildDirectory.dir("outputs/play-release")
val preparePlayReleaseHandoff by tasks.registering(org.gradle.api.tasks.Exec::class) {
    group = "verification"
    description = "Pairs the exact signed AAB, evidence JSON and checksums in an atomic Play handoff directory."
    dependsOn(generateSignedReleaseEvidence, verifyPlayReleaseHandoffContract)
    inputs.files(
        releaseBundleFile,
        signedReleaseEvidenceFile,
        rootProject.file("scripts/prepare-play-release-handoff.py"),
        rootProject.file("../docs/android/play-store-listing-ja.md"),
        rootProject.file("../docs/android/play-store-assets/phone-ja-JP/sources.tsv"),
        rootProject.file("../docs/android/play-store-assets/icon-512.png"),
        rootProject.file("../docs/android/play-store-assets/feature-graphic-1024x500.jpg"),
    )
    inputs.dir(rootProject.file("../docs/android/play-store-assets/phone-ja-JP"))
    outputs.dir(playReleaseHandoffRoot)
    outputs.upToDateWhen { false }
    commandLine(
        "python3",
        rootProject.file("scripts/prepare-play-release-handoff.py").absolutePath,
        "--aab",
        releaseBundleFile.get().asFile.absolutePath,
        "--evidence",
        signedReleaseEvidenceFile.get().asFile.absolutePath,
        "--output-root",
        playReleaseHandoffRoot.get().asFile.absolutePath,
        "--repository-root",
        rootProject.projectDir.parentFile.absolutePath,
    )
}

tasks.register("bundleSignedRelease") {
    group = "build"
    description = "Builds, verifies, records and packages the exact Play upload AAB handoff."
    dependsOn(preparePlayReleaseHandoff)
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
