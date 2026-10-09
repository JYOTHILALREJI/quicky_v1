import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// ----------------------------------------------------------------------------
// AD UNIT CONFIGURATION (v3.2.1)
//
// AdMob ids resolve in this priority order:
//   1. A real key in the developer's local .env  (gitignored — never committed)
//   2. Google's OFFICIAL TEST unit ids           (safe default for dev/QA)
//
// To serve REAL ads: create the ad units in your AdMob console and add e.g.
//   QUICKY_NATIVE_AD_UNIT=ca-app-pub-XXXXXXXX/NNNNNNNNN
//   QUICKY_CHAT_BANNER_AD_UNIT=ca-app-pub-XXXXXXXX/NNNNNNNNN
//   QUICKY_MATCHES_BANNER_AD_UNIT=ca-app-pub-XXXXXXXX/NNNNNNNNN
//   QUICKY_CLUB_BANNER_AD_UNIT=ca-app-pub-XXXXXXXX/NNNNNNNNN
// to your local .env (see .env.example), then rebuild. While the TEST ids
// are in use, AdMob shows the static "Test Ad" creative and — on debug
// builds — the "native ad validator" overlay; both disappear once real
// unit ids are configured.
// ----------------------------------------------------------------------------
val quickyAdEnv: Map<String, String> by lazy {
  val map = mutableMapOf<String, String>()
  // Module .env first (the secrets plugin's convention), then the root one.
  listOf(file(".env"), rootProject.file(".env")).forEach { f ->
    if (f.exists()) {
      f.readLines().forEach { raw ->
        val line = raw.trim()
        if (line.isNotEmpty() && !line.startsWith("#")) {
          val idx = line.indexOf('=')
          if (idx > 0) {
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) map.putIfAbsent(k, v)
          }
        }
      }
    }
  }
  map
}

fun quickyAdUnit(key: String, testId: String): String =
  quickyAdEnv[key]?.takeIf { it.isNotBlank() } ?: testId

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.sparkdating.kqwzt"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // AdMob units — real ids from .env when present, Google test ids otherwise.
    buildConfigField(
      "String", "DISCOVERY_NATIVE_AD_UNIT",
      "\"${quickyAdUnit("QUICKY_NATIVE_AD_UNIT", "ca-app-pub-3940256099942544/2247696110")}\""
    )
    buildConfigField(
      "String", "CHAT_BANNER_AD_UNIT",
      "\"${quickyAdUnit("QUICKY_CHAT_BANNER_AD_UNIT", "ca-app-pub-3940256099942544/9214589741")}\""
    )
    buildConfigField(
      "String", "MATCHES_BANNER_AD_UNIT",
      "\"${quickyAdUnit("QUICKY_MATCHES_BANNER_AD_UNIT", "ca-app-pub-3940256099942544/9214589741")}\""
    )
    buildConfigField(
      "String", "CLUB_BANNER_AD_UNIT",
      "\"${quickyAdUnit("QUICKY_CLUB_BANNER_AD_UNIT", "ca-app-pub-3940256099942544/6300978111")}\""
    )
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      // Ludo stays premium-gated in release builds.
      buildConfigField("boolean", "LUDO_FREE", "false")
      // v2.1 §3.7 — premium gates stay REAL in production builds.
      buildConfigField("boolean", "FORCE_PREMIUM_UNLOCK", "false")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      // Ludo is free on debug builds so QA can test it without Premium.
      buildConfigField("boolean", "LUDO_FREE", "true")
      // v2.1 §3.7 — every premium gate is unlocked for QA on debug builds.
      buildConfigField("boolean", "FORCE_PREMIUM_UNLOCK", "true")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  // Cassy typography: Playfair Display + DM Sans via downloadable Google Fonts
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // ML Kit face detection: on-device validation of onboarding photos
  implementation(libs.mlkit.face.detection)
  // JPEG EXIF orientation handling — the live verification selfie and the
  // uploaded profile photos carry rotation in EXIF, which must be applied
  // before face detection/cropping (Get Verified, PRD §34–§39).
  implementation(libs.androidx.exifinterface)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  // FCM push notifications (match/message/club alerts) + Firebase
  // Analytics — both versioned by the BoM platform above.
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.analytics)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // FusedLocationProviderClient — one-shot GPS fix for the onboarding
  // location field (distance-based discovery filter)
  implementation(libs.play.services.location)
  // Google AdMob — native ad card in Discovery + banner ad in Club Chat
  // (v2.1 §3.6). Ships with Google's TEST ad unit ids; swap for real ids
  // via RemoteConfig before enabling production traffic.
  implementation(libs.play.services.ads)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
