import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
}

@CacheableTask
abstract class PackageLegalAssets : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val documents: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun copyDocuments() {
    val destination = outputDirectory.get().asFile.apply { mkdirs() }
    documents.files.forEach { source -> source.copyTo(destination.resolve(source.name), overwrite = true) }
  }
}

val releaseStorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH")
  .orElse(providers.environmentVariable("KEYSTORE_PATH")).orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD")
  .orElse(providers.environmentVariable("STORE_PASSWORD")).orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD")
  .orElse(providers.environmentVariable("KEY_PASSWORD")).orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orElse("upload").get()
if (!releaseStorePath.isNullOrBlank()) {
  require(!releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
    "Release signing requires both store and key passwords in environment variables."
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.homeserver.thns"
    minSdk = 24
    targetSdk = 36
    versionCode = 5
    versionName = "1.3.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    getByName("debug") {
      val stableDebugKeystore = System.getenv("DEBUG_KEYSTORE_PATH")
      if (!stableDebugKeystore.isNullOrBlank()) {
        storeFile = file(stableDebugKeystore)
        storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
        keyAlias = "homeserver"
        keyPassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
      }
    }
    if (!releaseStorePath.isNullOrBlank()) {
      create("release") {
        storeFile = file(releaseStorePath)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isDebuggable = false
      // The first optimized beta crashed on a device, including after a clean install.
      // Keep optimization off until that regression is diagnosed and covered on Android.
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (!releaseStorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
    }
    debug { }
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

androidComponents {
  onVariants { variant ->
    val legalAssets = tasks.register<PackageLegalAssets>("package${variant.name.replaceFirstChar { it.uppercase() }}LegalAssets") {
      documents.from(rootProject.files("PRIVACY.md", "SECURITY.md", "THIRD_PARTY_NOTICES.md"))
      if (rootProject.file("LICENSE").exists()) documents.from(rootProject.file("LICENSE"))
      outputDirectory.set(layout.buildDirectory.dir("generated/legalAssets/${variant.name}"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(legalAssets, PackageLegalAssets::outputDirectory)
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.coil.compose)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation("com.github.mwiede:jsch:0.2.20")
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
  testImplementation("org.apache.sshd:sshd-core:2.14.0")
  testImplementation("org.apache.sshd:sshd-sftp:2.14.0")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
