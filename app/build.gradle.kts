plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.detekt)
}

android {
  namespace = "com.dsc.pdfmaster"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.dsc.pdfmaster"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    val releaseKeystore = file(releaseKeystorePath)
    if (releaseKeystore.exists()) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    val debugKeystore = file("${rootDir}/debug.keystore")
    if (debugKeystore.exists()) {
      create("debugConfig") {
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      
      val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val releaseKeystore = file(releaseKeystorePath)
      
      if(releaseKeystore.exists()){
         signingConfig = signingConfigs.getByName("release") 
      }
    }
    debug {
      signingConfig = signingConfigs.findByName("debugConfig") ?: signingConfigs.getByName("debug")
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
  sourceSets {
    getByName("androidTest").assets.directories.add("$projectDir/schemas")
    getByName("test").assets.directories.add("$projectDir/schemas")
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.material3Adaptive)
  implementation(libs.material3WindowSizeClass)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.dagger)
  implementation(libs.javax.inject)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.pdfbox.android)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
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

  ksp(libs.androidx.room.compiler)
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
  source.setFrom("src/main/java", "src/test/java")
}

tasks.register("ktlintCheck") {
  group = "verification"
  description = "Alias task for Kotlin style verification"
  dependsOn("detekt")
}

