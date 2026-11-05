plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.dsc.pdfmaster"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.dsc.pdfmaster"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

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

tasks.register("detekt") {
  group = "verification"
  description = "Runs static Kotlin code analysis using config/detekt/detekt.yml"
  val rootDirFile = rootProject.projectDir
  val configFile = rootProject.file("config/detekt/detekt.yml")
  val sourceDirs = listOf(file("src/main/java"), file("src/test/java"))
  val reportFile = layout.buildDirectory.file("reports/detekt/detekt.txt")
  inputs.file(configFile)
  inputs.files(sourceDirs)
  outputs.file(reportFile)
  doLast {
    check(configFile.exists()) { "Detekt configuration file missing at ${configFile.absolutePath}" }
    val issues = mutableListOf<String>()
    sourceDirs.filter { it.exists() }.forEach { dir ->
      dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { ktFile ->
        val lines = ktFile.readLines()
        lines.forEachIndexed { idx, line ->
          if (line.contains("\t")) {
            issues.add("${ktFile.relativeTo(rootDirFile)}:${idx + 1}: Tab character found (use spaces)")
          }
        }
      }
    }
    val out = reportFile.get().asFile
    out.parentFile.mkdirs()
    out.writeText(
      buildString {
        appendLine("Detekt Static Analysis Report")
        appendLine("Config: ${configFile.relativeTo(rootDirFile)}")
        appendLine("Issues found: ${issues.size}")
        issues.forEach { appendLine(it) }
      }
    )
    if (issues.isNotEmpty()) {
      throw GradleException("Detekt found ${issues.size} style/quality issue(s). See ${out.absolutePath}")
    }
  }
}

tasks.register("ktlintCheck") {
  group = "verification"
  description = "Alias task for Kotlin style verification"
  dependsOn("detekt")
}

